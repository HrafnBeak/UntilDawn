package org.kennyerf.untildawn.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.kennyerf.untildawn.block.DecayingCobwebBlock;
import org.kennyerf.untildawn.registry.ModEntities;
import org.kennyerf.untildawn.registry.ModItems;

/**
 * What a Net Shot fires: a slow, weak arrow that bursts into Decaying Cobwebs wherever it lands, on a block or a mob.
 * Whoever it hits directly is slowed for ten seconds as well.
 */
public class NetShot extends AbstractArrow {
    /** How fast it flies, against a normal arrow. */
    public static final float VELOCITY = 0.6F;
    /** A normal arrow from a crossbow does 7 (2 base damage at 3.15 speed, rounded up); this does a fifth of that. */
    private static final float DAMAGE = 7.0F * 0.2F;
    /** Slowness I for 10 seconds on a direct hit. */
    private static final int SLOW_TICKS = 200;

    public NetShot(EntityType<? extends NetShot> type, Level level) {
        super(type, level);
    }

    public NetShot(Level level, LivingEntity owner, ItemStack ammo, ItemStack weapon) {
        super(ModEntities.NET_SHOT, owner, level, ammo, weapon);
    }

    public NetShot(Level level, double x, double y, double z, ItemStack ammo, ItemStack weapon) {
        super(ModEntities.NET_SHOT, x, y, z, level, ammo, weapon);
    }

    @Override
    public void shoot(double x, double y, double z, float power, float uncertainty) {
        super.shoot(x, y, z, power * VELOCITY, uncertainty);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.NET_SHOT);
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        if (level() instanceof ServerLevel level) {
            Entity hit = hitResult.getEntity();
            Entity source = getOwner() == null ? this : getOwner();
            hit.hurtServer(level, damageSources().arrow(this, source), DAMAGE);
            if (hit instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 0), source);
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
