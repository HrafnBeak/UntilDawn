package org.kennyerf.untildawn.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.kennyerf.untildawn.entity.Webber;

import java.util.EnumSet;

/**
 * A Webber's other attack: when it is ready (every 10 seconds) and the target is in sight, a little way off, it shoots a
 * web at it. Takes no flags, so the spider keeps chasing and biting meanwhile.
 */
public class WebShotGoal extends Goal {
    /** Closer than this it just bites; further than this the shot is too wild. */
    private static final double MIN_RANGE = 3.0;
    private static final double MAX_RANGE = 16.0;

    private final Webber mob;

    public WebShotGoal(Webber mob) {
        this.mob = mob;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || !mob.isWebReady()) {
            return false;
        }
        double distance = mob.distanceTo(target);
        return distance >= MIN_RANGE && distance <= MAX_RANGE && mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        LivingEntity target = mob.getTarget();
        if (target != null) {
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            mob.shootWeb(target);
        }
    }
}
