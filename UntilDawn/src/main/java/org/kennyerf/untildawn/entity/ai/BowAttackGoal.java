package org.kennyerf.untildawn.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.entity.ArcherSkeleton;

import java.util.EnumSet;

/**
 * Draws and looses the bow. Fires straight at a target it can see; a mob that can plunge (Longbow Men) arcs
 * arrows over cover onto a target it can't. Direct fire always wins when there is a clear line of sight.
 * Moving into range is left to {@link SquadObjectiveGoal}, which stops once this goal can shoot.
 */
public class BowAttackGoal extends Goal {
    private static final int DRAW_TICKS = 20;

    private final ArcherSkeleton mob;
    private int cooldown;

    public BowAttackGoal(ArcherSkeleton mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && mob.isHoldingBow()
                && mob.distanceTo(target) <= mob.bowRange()
                && (mob.getSensing().hasLineOfSight(target) || mob.plungingShot(target) != null);
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
        mob.setAggressive(true);
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
        mob.stopUsingItem();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        boolean sees = mob.getSensing().hasLineOfSight(target);
        Ballistics.@Nullable Shot plunge = sees ? null : mob.plungingShot(target);
        if (plunge != null) {
            Vec3 aim = mob.getEyePosition().add(plunge.direction().scale(10.0));
            mob.getLookControl().setLookAt(aim.x, aim.y, aim.z, 30.0F, 90.0F);
        } else {
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }

        if (cooldown > 0) {
            cooldown--;
        }
        if (mob.isUsingItem()) {
            int drawn = mob.getTicksUsingItem();
            if (drawn >= DRAW_TICKS) {
                float power = BowItem.getPowerForTime(drawn);
                if (sees) {
                    mob.performRangedAttack(target, power);
                } else if (plunge != null) {
                    mob.performPlungingAttack(power, plunge);
                }
                mob.stopUsingItem();
                cooldown = mob.shotInterval();
            }
        } else if (cooldown <= 0 && (sees || plunge != null)) {
            mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(mob, Items.BOW));
        }
    }
}
