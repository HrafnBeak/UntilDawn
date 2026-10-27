package org.kennyerf.untildawn.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A flask in the air: it breaks on the first thing it meets, and the kind of flask decides what that does. */
public abstract class ThrownFlask extends ThrowableItemProjectile {
    protected ThrownFlask(EntityType<? extends ThrownFlask> type, Level level) {
        super(type, level);
    }

    protected ThrownFlask(EntityType<? extends ThrownFlask> type, Level level, LivingEntity owner, ItemStack stack) {
        super(type, owner, level, stack);
    }

    /** Called on the server where the flask broke: {@code at} is the cell it broke in, {@code where} the exact spot. */
    protected abstract void shatter(ServerLevel level, BlockPos at, Vec3 where);

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (level() instanceof ServerLevel level) {
            Vec3 where = hitResult.getLocation();
            BlockPos at = hitResult instanceof BlockHitResult block ? block.getBlockPos().relative(block.getDirection()) : BlockPos.containing(where);
            level.playSound(null, where.x, where.y, where.z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.NEUTRAL, 1.0F, 1.0F);
            shatter(level, at, where);
            discard();
        }
    }
}
