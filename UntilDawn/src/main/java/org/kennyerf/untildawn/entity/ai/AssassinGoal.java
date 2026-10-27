package org.kennyerf.untildawn.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.entity.Assassin;
import org.kennyerf.untildawn.game.WaveManager;
import org.kennyerf.untildawn.pathing.PathGoal;

import java.util.EnumSet;

/**
 * An Assassin's fighting loop, and all its moving (on its own planner, sprint-jumping like a Sprinter):
 * <ol>
 *     <li>crossbow loaded: close to {@link Assassin#RANGE} with the target in sight, and fire a slowing bolt</li>
 *     <li>target slowed: dagger out, close in and stab, faster than a sword swings</li>
 *     <li>slowness worn off: crossbow back out, sprint away to {@link Assassin#RANGE} while winding it, then turn and
 *     go back to 1</li>
 * </ol>
 * With nobody to hunt it heads for the Core.
 */
public class AssassinGoal extends Goal {
    /** An Iron Dagger's attack speed is 2.6 a second, against an iron sword's 1.6. */
    private static final int STAB_INTERVAL = 8;
    /** How much further than the range it backs off, so a step forward from the target doesn't start it running again. */
    private static final double RETREAT_MARGIN = 1.0;

    private enum Move { CORE, CHASE, APPROACH, RETREAT, HOLD }

    private final Assassin mob;
    private @Nullable Move move;
    private int stabCooldown;

    public AssassinGoal(Assassin mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() || core() != null && !WaveManager.isInCoreArea(mob);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        move = null;
        mob.pathController().stop();
        mob.stopUsingItem();
        mob.setSprinting(false);
        mob.setAggressive(false);
    }

    private @Nullable BlockPos core() {
        return mob.level() instanceof ServerLevel level ? WaveManager.corePos(level) : null;
    }

    @Override
    public void tick() {
        if (stabCooldown > 0) {
            stabCooldown--;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            mob.setAggressive(false);
            BlockPos core = core();
            if (core != null) {
                moveTowards(Move.CORE, PathGoal.coreArea(core, WaveManager.AREA_RADIUS, WaveManager.AREA_HEIGHT));
            }
            return;
        }
        mob.setAggressive(true);
        if (target.hasEffect(MobEffects.SLOWNESS)) {
            stab(target);
        } else {
            shoot(target);
        }
    }

    /** The target is slowed: dagger out, close in and stab. */
    private void stab(LivingEntity target) {
        if (!mob.holdsDagger()) {
            mob.swapWeapons();
        }
        if (mob.isWithinMeleeAttackRange(target) && mob.getSensing().hasLineOfSight(target)) {
            hold(target);
            if (stabCooldown <= 0) {
                stabCooldown = STAB_INTERVAL;
                mob.swingForAttack(InteractionHand.MAIN_HAND);
                mob.doHurtTarget(getServerLevel(mob), target);
            }
        } else {
            moveTowards(Move.CHASE, PathGoal.near(target.blockPosition(), 1, target.getName().getString()));
        }
    }

    /** The target isn't slowed: crossbow out; wind it keeping out of reach, then fire from range. */
    private void shoot(LivingEntity target) {
        if (mob.holdsDagger()) {
            mob.swapWeapons();
        }
        double distance = mob.distanceTo(target);
        // Winds it while empty, and lets go once it is loaded.
        mob.chargeCrossbow();
        if (!mob.isCrossbowCharged()) {
            if (distance < Assassin.RANGE) {
                moveTowards(Move.RETREAT, PathGoal.awayFrom(target.blockPosition(), Assassin.RANGE + RETREAT_MARGIN,
                        "away from " + target.getName().getString()));
                // Running, not walking: it has to get back out of reach.
                mob.setSprinting(true);
            } else {
                hold(target);
            }
            return;
        }
        if (distance <= Assassin.RANGE && mob.getSensing().hasLineOfSight(target)) {
            hold(target);
            mob.fireCrossbow(target);
        } else {
            moveTowards(Move.APPROACH, PathGoal.near(target.blockPosition(), (int) Assassin.RANGE - 1, target.getName().getString()));
        }
    }

    private void moveTowards(Move kind, PathGoal goal) {
        MobPathController controller = mob.pathController();
        if (move != kind) {
            // A new kind of move needs a new route even if the target stayed put.
            move = kind;
            controller.forceRepath();
            mob.setSprinting(false);
        }
        controller.tick(goal);
    }

    /** Stands still, facing the target. */
    private void hold(LivingEntity target) {
        if (move != Move.HOLD) {
            move = Move.HOLD;
            mob.pathController().stop();
            mob.getNavigation().stop();
            mob.setSprinting(false);
        }
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 1.0);
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }
}
