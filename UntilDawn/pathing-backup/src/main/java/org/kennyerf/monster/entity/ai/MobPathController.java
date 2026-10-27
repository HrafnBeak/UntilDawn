package org.kennyerf.monster.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.kennyerf.monster.config.MonsterConfig;
import org.kennyerf.monster.pathing.MobPath;
import org.kennyerf.monster.pathing.MobPathfinder;
import org.kennyerf.monster.pathing.MoveType;
import org.kennyerf.monster.pathing.PathAbilities;
import org.kennyerf.monster.pathing.PathCosts;
import org.kennyerf.monster.pathing.PathGoal;
import org.kennyerf.monster.pathing.PathStep;
import org.kennyerf.monster.team.WaveTeam;

import java.util.List;

/**
 * Follows a {@link MobPath} step by step, doing the work each step needs first: mining (Miner), igniting
 * (Bomber) or placing blocks (Builder). Waits when a teammate has claimed the work, and re-plans when the
 * target moves, the mob gets knocked off course, or progress stalls.
 */
public class MobPathController {
    private static final int REPATH_COOLDOWN = 20;
    private static final int STEP_TIMEOUT = 80;
    private static final int MAX_WAIT_TICKS = 160;
    private static final int CLAIM_LOOKAHEAD = 8;
    private static final double SPEED = 1.0;

    private final Mob mob;
    private final PathAbilities abilities;

    private @Nullable MobPath path;
    private @Nullable BlockPos pathAnchor;
    private int index;
    private int stepTicks;
    private int waitTicks;
    private int repathCooldown;
    private int placeCooldown;
    private boolean pillarPlaced;
    private @Nullable BlockPos miningPos;
    private int mineProgress;
    private int mineNeeded;
    private int lastStage = -1;
    private String status = "idle";

    public MobPathController(Mob mob, PathAbilities abilities) {
        this.mob = mob;
        this.abilities = abilities;
    }

    // ---------------------------------------------------------------- debug accessors

    public @Nullable MobPath path() {
        return path;
    }

    public int index() {
        return index;
    }

    public String status() {
        return status;
    }

    public @Nullable BlockPos miningPos() {
        return miningPos;
    }

    public PathAbilities abilities() {
        return abilities;
    }

    /** Drop the current path so the next tick plans a fresh one. */
    public void forceRepath() {
        invalidate("forced re-plan");
        repathCooldown = 0;
    }

    // ---------------------------------------------------------------- ticking

    public void tick(PathGoal goal) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        if (repathCooldown > 0) {
            repathCooldown--;
        }
        if (placeCooldown > 0) {
            placeCooldown--;
        }
        if (path != null && index >= path.steps().size()) {
            // Reached the end. Drop the path so nothing indexes past it; a new one is planned once the
            // re-plan cooldown allows (or never, if the objective was reached and the goal stops).
            path = null;
            status = "reached end of path";
        }
        if (shouldRepath(goal)) {
            repath(level, goal);
        }
        if (path != null && index < path.steps().size()) {
            follow(level);
        }
    }

    public void stop() {
        clearCracks();
        miningPos = null;
        path = null;
        status = "idle";
        if (mob.level() instanceof ServerLevel level) {
            WaveTeam.releaseAll(level, mob);
        }
    }

    private boolean shouldRepath(PathGoal goal) {
        if (repathCooldown > 0) {
            return false;
        }
        if (path == null) {
            return true;
        }
        // The target has wandered away from where this path leads.
        return pathAnchor != null && pathAnchor.distSqr(goal.anchor()) > 9.0;
    }

    private void repath(ServerLevel level, PathGoal goal) {
        MobPath result = MobPathfinder.find(level, mob, abilities, feet(), goal);
        if (result == null) {
            if (path == null) {
                status = "no route (or path budget spent)";
            }
            repathCooldown = 10;
            return;
        }
        clearCracks();
        miningPos = null;
        WaveTeam.releaseAll(level, mob);
        path = result;
        pathAnchor = goal.anchor();
        index = 1;
        resetStep();
        repathCooldown = REPATH_COOLDOWN;
        claimUpcoming(level);
    }

    private void invalidate(String reason) {
        clearCracks();
        miningPos = null;
        path = null;
        status = reason;
        repathCooldown = Math.min(repathCooldown, 5);
    }

    private void resetStep() {
        stepTicks = 0;
        waitTicks = 0;
        pillarPlaced = false;
    }

    private void follow(ServerLevel level) {
        List<PathStep> steps = path.steps();
        BlockPos feet = feet();

        // Shortcuts: if the mob is already standing on a later node, jump ahead.
        for (int k = Math.min(index + 3, steps.size() - 1); k > index; k--) {
            if (feet.equals(steps.get(k).pos()) && mob.onGround()) {
                index = k + 1;
                resetStep();
                if (index >= steps.size()) {
                    return;
                }
                break;
            }
        }

        PathStep step = steps.get(index);
        BlockPos previous = steps.get(index - 1).pos();
        stepTicks++;
        if (stepTicks > STEP_TIMEOUT && miningPos == null && waitTicks == 0) {
            invalidate("stuck, re-planning");
            return;
        }
        if (feet.distManhattan(step.pos()) > 4) {
            invalidate("knocked off path, re-planning");
            return;
        }
        if (stepTicks % 10 == 1) {
            claimUpcoming(level);
        }

        // 1. Clear the way.
        for (BlockPos block : step.breaks()) {
            if (isOpen(level, block)) {
                continue;
            }
            if (WaveTeam.isBreakClaimedByOther(level, block, mob)) {
                waitAt(previous, "waiting for a teammate to dig through");
                return;
            }
            if (!abilities.canBreak()) {
                invalidate("blocked, re-planning");
                return;
            }
            if (abilities.explosive()) {
                breach(previous, block);
            } else {
                mine(level, previous, block);
            }
            return;
        }

        // 2. Build what the step stands on (pillars place mid-jump instead).
        if (step.type() != MoveType.PILLAR) {
            for (BlockPos spot : step.places()) {
                if (!level.getBlockState(spot).canBeReplaced()) {
                    continue;
                }
                if (WaveTeam.isPlaceClaimedByOther(level, spot, mob)) {
                    waitAt(previous, "waiting for a teammate to build");
                    return;
                }
                if (abilities.canPlace() && placeCooldown > 0) {
                    waitAt(previous, "building");
                    return;
                }
                if (!abilities.canPlace() || !place(level, spot)) {
                    invalidate("can't build there, re-planning");
                    return;
                }
                return;
            }
        }
        waitTicks = 0;

        // 3. Move.
        Vec3 target = Vec3.atBottomCenterOf(step.pos());
        if (step.type() == MoveType.PILLAR) {
            status = "pillaring up";
            BlockPos under = step.pos().below();
            if (!pillarPlaced) {
                if (placeCooldown <= 0 && mob.getBoundingBox().minY >= under.getY() + 1.0 && place(level, under)) {
                    pillarPlaced = true;
                } else if (mob.onGround()) {
                    mob.getJumpControl().jump();
                }
            }
        } else {
            status = "moving (" + step.type().name().toLowerCase() + ")";
            if (step.type() == MoveType.ASCEND && mob.onGround() && mob.horizontalCollision) {
                mob.getJumpControl().jump();
            }
        }
        mob.getMoveControl().setWantedPosition(target.x, target.y, target.z, SPEED);

        if (arrived(step, feet)) {
            index++;
            resetStep();
        }
    }

    private boolean arrived(PathStep step, BlockPos feet) {
        if (!feet.equals(step.pos())) {
            return false;
        }
        return switch (step.type()) {
            case PILLAR -> pillarPlaced && mob.onGround();
            case ASCEND, DESCEND, FALL, DIG_DOWN -> mob.onGround() || mob.isInWater();
            default -> {
                double dx = mob.getX() - (step.pos().getX() + 0.5);
                double dz = mob.getZ() - (step.pos().getZ() + 0.5);
                yield dx * dx + dz * dz < 0.45 * 0.45;
            }
        };
    }

    private void waitAt(BlockPos spot, String reason) {
        status = reason;
        if (++waitTicks > MAX_WAIT_TICKS) {
            invalidate("waited too long, re-planning");
            return;
        }
        Vec3 hold = Vec3.atBottomCenterOf(spot);
        mob.getMoveControl().setWantedPosition(hold.x, hold.y, hold.z, SPEED);
    }

    // ---------------------------------------------------------------- work

    private void mine(ServerLevel level, BlockPos standAt, BlockPos block) {
        if (!block.equals(miningPos)) {
            clearCracks();
            miningPos = block;
            mineProgress = 0;
            float hardness = level.getBlockState(block).getDestroySpeed(level, block);
            mineNeeded = Math.max(1, (int) (PathCosts.mineTicks(hardness) / MonsterConfig.MINER_DIG_SPEED.get()));
            WaveTeam.claimBreak(level, block, mob);
        }
        status = "digging";
        Vec3 hold = Vec3.atBottomCenterOf(standAt);
        mob.getMoveControl().setWantedPosition(hold.x, hold.y, hold.z, SPEED);
        mob.getLookControl().setLookAt(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);

        BlockState state = level.getBlockState(block);
        if (mineProgress % 6 == 0) {
            mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            level.playSound(null, block, state.getSoundType().getHitSound(), mob.getSoundSource(), 0.5F, 0.8F);
        }
        mineProgress++;
        int stage = Math.min(9, mineProgress * 10 / mineNeeded);
        if (stage != lastStage) {
            level.destroyBlockProgress(mob.getId(), block, stage);
            lastStage = stage;
        }
        if (mineProgress >= mineNeeded) {
            level.destroyBlock(block, true, mob);
            WaveTeam.release(level, block);
            clearCracks();
            miningPos = null;
            stepTicks = 0;
        }
    }

    private void breach(BlockPos standAt, BlockPos block) {
        Vec3 hold = Vec3.atBottomCenterOf(standAt);
        mob.getMoveControl().setWantedPosition(hold.x, hold.y, hold.z, SPEED);
        if (mob.distanceToSqr(Vec3.atCenterOf(block)) < 2.5 * 2.5 && mob instanceof Creeper creeper && !creeper.isIgnited()) {
            creeper.ignite();
        }
        status = "breaching a wall";
    }

    private boolean place(ServerLevel level, BlockPos spot) {
        if (!level.getBlockState(spot).canBeReplaced()
                || !level.getEntitiesOfClass(LivingEntity.class, new AABB(spot)).isEmpty()
                || mob.distanceToSqr(Vec3.atCenterOf(spot)) > 4.5 * 4.5) {
            return false;
        }
        BlockState block = buildingBlock();
        level.setBlockAndUpdate(spot, block);
        level.playSound(null, spot, block.getSoundType().getPlaceSound(), mob.getSoundSource(), 1.0F, 0.8F);
        mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        WaveTeam.release(level, spot);
        placeCooldown = MonsterConfig.BUILDER_PLACE_DELAY.get();
        status = "building";
        return true;
    }

    private static BlockState buildingBlock() {
        return BuiltInRegistries.BLOCK.getOptional(MonsterConfig.BUILDER_BLOCK.get())
                .orElse(Blocks.COBBLESTONE)
                .defaultBlockState();
    }

    /** Tell teammates about the next few blocks this mob will dig or build, so they wait and follow. */
    private void claimUpcoming(ServerLevel level) {
        if (path == null) {
            return;
        }
        List<PathStep> steps = path.steps();
        for (int k = index; k < Math.min(index + CLAIM_LOOKAHEAD, steps.size()); k++) {
            PathStep step = steps.get(k);
            if (abilities.canBreak()) {
                for (BlockPos block : step.breaks()) {
                    if (!isOpen(level, block)) {
                        WaveTeam.claimBreak(level, block, mob);
                    }
                }
            }
            if (abilities.canPlace()) {
                for (BlockPos spot : step.places()) {
                    if (level.getBlockState(spot).canBeReplaced()) {
                        WaveTeam.claimPlace(level, spot, mob);
                    }
                }
            }
        }
    }

    private void clearCracks() {
        if (miningPos != null) {
            mob.level().destroyBlockProgress(mob.getId(), miningPos, -1);
        }
        lastStage = -1;
    }

    private static boolean isOpen(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** The block the mob's feet are in, stepping up out of partial blocks like farmland or slabs. */
    private BlockPos feet() {
        BlockPos pos = mob.blockPosition();
        return isOpen((ServerLevel) mob.level(), pos) ? pos : pos.above();
    }
}
