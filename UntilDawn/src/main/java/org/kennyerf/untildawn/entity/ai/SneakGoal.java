package org.kennyerf.untildawn.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.kennyerf.untildawn.entity.SneakingSpider;
import org.kennyerf.untildawn.pathing.PathGoal;

import java.util.EnumSet;

/**
 * A Sneaking Spider, still invisible, creeps up on its target; within {@link SneakingSpider#REVEAL_RANGE} it shows
 * itself and lunges. Until then it holds the move, look and jump controls, so the spider neither bites nor leaps early.
 * It creeps along its own planned path, climbing walls on the way like any wave spider.
 */
public class SneakGoal extends Goal {
    private final SneakingSpider mob;

    public SneakGoal(SneakingSpider mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return mob.isSneaking() && target != null && target.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        mob.pathController().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        if (mob.distanceTo(target) <= SneakingSpider.REVEAL_RANGE) {
            mob.pathController().stop();
            mob.reveal(target);
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        mob.pathController().tick(PathGoal.near(target.blockPosition(), 1, target.getName().getString()));
    }
}
