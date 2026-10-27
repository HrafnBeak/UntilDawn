package org.kennyerf.untildawn.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.kennyerf.untildawn.entity.WaveMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wave mobs don't shove each other. Squadmates following each other up a ladder or a one-block stair high up would push
 * each other off it; out of each other's way they keep by themselves (see MobPathController's making way).
 */
@Mixin(LivingEntity.class)
public abstract class WaveMobPushMixin {
    @Inject(method = "doPush", at = @At("HEAD"), cancellable = true)
    private void untildawn$keepFromShovingSquadmates(Entity entity, CallbackInfo ci) {
        if ((Object) this instanceof WaveMob && entity instanceof WaveMob) {
            ci.cancel();
        }
    }
}
