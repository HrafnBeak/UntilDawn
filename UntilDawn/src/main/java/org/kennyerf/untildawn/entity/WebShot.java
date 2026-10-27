package org.kennyerf.untildawn.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.kennyerf.untildawn.block.DecayingCobwebBlock;
import org.kennyerf.untildawn.registry.ModEntities;

/**
 * A Webber's shot: an arrow (drawn with the cobweb texture) that does no damage. Wherever it lands, on a block or a
 * mob, it leaves one to three Decaying Cobwebs in the air around that spot; whoever it hits directly is slowed too.
 */
public class WebShot extends AbstractArrow {
    /** Slowness I for 3 seconds on a direct hit. */
    private static final int SLOW_TICKS = 60;

    public WebShot(EntityType<? extends WebShot> type, Level level) {
        super(type, level);
    }

    public WebShot(Level level, LivingEntity owner) {
        super(ModEntities.WEB_SHOT, owner, level, new ItemStack(Items.COBWEB), null);
        this.pickup = Pickup.DISALLOWED;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.COBWEB);
    }

    /** Other wave mobs are let through. */
    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof WaveMob);
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        if (level() instanceof ServerLevel level) {
            Entity hit = hitResult.getEntity();
            if (hit instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 0), getEffectSource());
            }
            DecayingCobwebBlock.spin(level, hit.blockPosition());
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        if (level() instanceof ServerLevel level) {
            DecayingCobwebBlock.spin(level, hitResult.getBlockPos().relative(hitResult.getDirection()));
        }
        discard();
    }
}
