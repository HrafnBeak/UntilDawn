package org.kennyerf.untildawn.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.entity.WaveMob;
import org.kennyerf.untildawn.game.WaveManager;
import org.kennyerf.untildawn.pathing.PathAbilities;
import org.kennyerf.untildawn.pathing.PathGoal;
import org.kennyerf.untildawn.squad.Squad;
import org.kennyerf.untildawn.squad.Squads;
import org.kennyerf.untildawn.squad.WorkRequest;

import java.util.EnumSet;

/**
 * Drives a wave mob along its own planned path towards its squad's shared goal. In order of priority:
 * <ol>
 *     <li>a specialist answers a squadmate's call for help (dig, blast or build); Builders and Bombers even in the
 *     middle of a fight, but none while hanging on a ladder or in mid-air</li>
 *     <li>the player this mob is hunting</li>
 *     <li>the player most of its squad is hunting</li>
 *     <li>the Core, from the side the squad was given</li>
 * </ol>
 * Once in attack range (and able to hit) it lets go so the vanilla melee/swell goals or {@link BowAttackGoal} fight.
 */
public class SquadObjectiveGoal<T extends Mob & WaveMob> extends Goal {
    private static final int JOB_SCAN_TICKS = 10;

    private final T mob;
    private int jobScanIn;

    public SquadObjectiveGoal(T mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (mob instanceof Creeper creeper && creeper.getSwellDir() > 0 && !mob.pathController().hasJob()) {
            // About to explode at a player: let the vanilla swell goal hold it still.
            return false;
        }
        return mob.pathController().hasJob() || mob.pathController().isYielding() || lookForJob() || objective() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        mob.pathController().stop();
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        MobPathController controller = mob.pathController();
        if (!controller.hasJob()) {
            lookForJob();
        }
        controller.tick(controller.hasJob() ? null : objective());
    }

    /** Specialists listen for squadmates asking for help; answering comes before fighting further away. */
    private boolean lookForJob() {
        if (--jobScanIn > 0) {
            return false;
        }
        jobScanIn = JOB_SCAN_TICKS;
        PathAbilities abilities = mob.pathAbilities();
        Squad squad = Squads.of(mob);
        if (squad == null || !(abilities.canBreak() || abilities.canPlace()) || !mob.pathController().isFree()) {
            // Hanging on a ladder or in mid-air, it gets itself somewhere safe before going off to help.
            return false;
        }
        LivingEntity target = mob.getTarget();
        if (target != null && target.isAlive() && engaged(target) && !abilities.explosive() && !abilities.canPlace()) {
            // Busy fighting right here. Bombers put support first regardless, and Builders, whose job is making the
            // squad's way, leave a fight to build for a squadmate.
            return false;
        }
        WorkRequest job = squad.offerJob(mob);
        if (job == null) {
            return false;
        }
        mob.pathController().takeJob(job);
        return true;
    }

    private @Nullable PathGoal objective() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        LivingEntity target = mob.getTarget();
        if (target != null && target.isAlive()) {
            return engaged(target) ? null : PathGoal.near(target.blockPosition(), 1, target.getName().getString());
        }
        Squad squad = Squads.of(mob);
        Player squadTarget = squad != null ? squad.goalPlayer() : null;
        if (squadTarget != null && squadTarget.level() == level) {
            return PathGoal.near(squadTarget.blockPosition(), 2, "the squad's target " + squadTarget.getName().getString());
        }
        BlockPos core = WaveManager.corePos(level);
        // Standing there: a mob climbing up a ladder (or jumping) at the edge of the area touches it before it gets in, and
        // stopping there it would slide back down.
        if (core == null || WaveManager.isInCoreArea(mob) && (mob.onGround() || mob.isInWater())) {
            return null;
        }
        if (squad != null && squad.attackSide() >= 0) {
            // Each squad has its own side to come at the Core from (see Squads), so they don't all bunch up on one.
            int[] side = Squads.SIDES[squad.attackSide()];
            return PathGoal.coreSide(core, WaveManager.AREA_RADIUS, WaveManager.AREA_HEIGHT, side[0], side[1]);
        }
        return PathGoal.coreArea(core, WaveManager.AREA_RADIUS, WaveManager.AREA_HEIGHT);
    }

    private boolean engaged(LivingEntity target) {
        if (!mob.isRanged() && !mob.onGround()) {
            // Mid-jump (towering, climbing): finish the move first, or it would stop before placing its block.
            return false;
        }
        double range = mob.engageRange();
        // A zombie's or spider's own AI no longer walks it the last bit, so it keeps coming until it can really hit.
        boolean inRange = (mob instanceof Zombie || mob instanceof Spider) && !mob.isRanged() ? mob.isWithinMeleeAttackRange(target)
                : mob.distanceToSqr(target) <= range * range;
        if (!inRange) {
            return false;
        }
        if (!mob.getSensing().hasLineOfSight(target) && !mob.canFightUnseen(target)) {
            return false;
        }
        return mob.isRanged() || Math.abs(target.getY() - mob.getY()) < 1.5;
    }
}
