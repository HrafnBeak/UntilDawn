package org.kennyerf.monster.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;
import org.kennyerf.monster.entity.WaveMob;
import org.kennyerf.monster.game.WaveManager;
import org.kennyerf.monster.pathing.PathGoal;

import java.util.EnumSet;

/**
 * Drives a wave mob along its planned path to its objective: the player it is hunting, otherwise the Core.
 * Once in attack range (with line of sight) it lets go so the vanilla melee/bow/swell goals do the fighting.
 */
public class PathToObjectiveGoal<T extends Mob & WaveMob> extends Goal {
    private final T mob;

    public PathToObjectiveGoal(T mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return objective() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return objective() != null;
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
        PathGoal goal = objective();
        if (goal != null) {
            mob.pathController().tick(goal);
        }
    }

    private @Nullable PathGoal objective() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        LivingEntity target = mob.getTarget();
        if (target != null && target.isAlive()) {
            return engaged(target) ? null : PathGoal.near(target.blockPosition(), 1, target.getName().getString());
        }
        BlockPos core = WaveManager.corePos(level);
        if (core == null || WaveManager.isInCoreArea(mob)) {
            return null;
        }
        return PathGoal.coreArea(core, WaveManager.AREA_RADIUS, WaveManager.AREA_HEIGHT);
    }

    private boolean engaged(LivingEntity target) {
        double range = mob.engageRange();
        if (mob.distanceToSqr(target) > range * range || !mob.getSensing().hasLineOfSight(target)) {
            return false;
        }
        return mob.isRanged() || Math.abs(target.getY() - mob.getY()) < 1.5;
    }
}
