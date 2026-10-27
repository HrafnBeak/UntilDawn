package org.kennyerf.untildawn.mixin;

import net.minecraft.world.entity.player.Player;
import org.kennyerf.untildawn.item.ScopedHeavyCrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A loaded Scoped Heavy Crossbow is looked through like a spyglass: the zoom and the scope overlay follow from this. */
@Mixin(Player.class)
public abstract class ScopedCrossbowMixin {
    @Inject(method = "isScoping", at = @At("RETURN"), cancellable = true)
    private void untildawn$lookThroughTheScope(CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        if (!cir.getReturnValueZ() && player.isUsingItem() && ScopedHeavyCrossbowItem.isAiming(player.getUseItem())) {
            cir.setReturnValue(true);
        }
    }
}
