package org.kennyerf.untildawn.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import org.kennyerf.untildawn.entity.ArcherSkeleton;
import org.kennyerf.untildawn.world.BlockDamage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Archers' and Longbow Men's arrows: they damage the first block they hit, and Longbow Men's leave a trail until then
 * (see {@link ArcherSkeleton#BLOCK_DAMAGE_TAG} and {@link ArcherSkeleton#TRAIL_TAG}).
 */
@Mixin(AbstractArrow.class)
public abstract class MobArrowMixin {
    @Shadow
    protected abstract boolean isInGround();

    @Inject(method = "onHitBlock", at = @At("TAIL"))
    private void untildawn$damageHitBlock(BlockHitResult hitResult, CallbackInfo ci) {
        BlockDamage.onArrowHit((AbstractArrow) (Object) this, hitResult.getBlockPos());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void untildawn$drawTrail(CallbackInfo ci) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (arrow.level() instanceof ServerLevel level && !arrow.isRemoved() && !isInGround()
                && arrow.entityTags().contains(ArcherSkeleton.TRAIL_TAG)) {
            level.sendParticles(ArcherSkeleton.TRAIL_PARTICLE, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
