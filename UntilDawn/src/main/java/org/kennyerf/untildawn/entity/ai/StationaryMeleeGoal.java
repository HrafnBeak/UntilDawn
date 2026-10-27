package org.kennyerf.untildawn.entity.ai;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Hits the target when it is in reach, and never moves: walking to the target is {@link SquadObjectiveGoal}'s job.
 * This replaces the vanilla melee goal, which also paths towards the target on its own.
 */
public class StationaryMeleeGoal extends Goal {
    private static final int ATTACK_INTERVAL = 20;

    private final PathfinderMob mob;
    private int ticksUntilNextAttack;

    public StationaryMeleeGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        mob.setAggressive(true);
        ticksUntilNextAttack = 0;
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);
        // A mob with its shield up can't attack, as with a player.
        if (ticksUntilNextAttack <= 0 && !mob.isUsingItem() && mob.isWithinMeleeAttackRange(target)
                && mob.getSensing().hasLineOfSight(target)) {
            ticksUntilNextAttack = adjustedTickDelay(ATTACK_INTERVAL);
            mob.swingForAttack(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(getServerLevel(mob), target);
        }
    }
}
