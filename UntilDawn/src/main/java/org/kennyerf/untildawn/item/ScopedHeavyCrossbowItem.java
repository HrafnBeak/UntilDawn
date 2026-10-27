package org.kennyerf.untildawn.item;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.kennyerf.untildawn.UntilDawn;

/**
 * A Heavy Crossbow with a Scope. Once it is loaded, holding right click looks through the scope (zoomed like a
 * spyglass, see ScopedCrossbowMixin) instead of shooting, and left click while looking fires. The client only says
 * that the player clicked ({@link Shoot}); the server checks they really are aiming a loaded one.
 */
public class ScopedHeavyCrossbowItem extends HeavyCrossbowItem {
    /** What a normal crossbow's arrows are shot with, before the Heavy Crossbow doubles it. */
    private static final float ARROW_POWER = 3.15F;

    public ScopedHeavyCrossbowItem(Properties properties) {
        super(properties);
    }

    /** The left click of a player looking through a scope. */
    public record Shoot() implements CustomPacketPayload {
        public static final Type<Shoot> TYPE = new Type<>(UntilDawn.id("scoped_shot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Shoot> CODEC = StreamCodec.unit(new Shoot());

        @Override
        public Type<Shoot> type() {
            return TYPE;
        }
    }

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(Shoot.TYPE, Shoot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Shoot.TYPE, (payload, context) -> fire(context.player()));
    }

    /** Whether this is a scoped crossbow with an arrow in it, which is what looking through the scope takes. */
    public static boolean isAiming(ItemStack stack) {
        return stack.getItem() instanceof ScopedHeavyCrossbowItem && isCharged(stack);
    }

    /** Fires what the player is aiming, if they are. */
    public static void fire(Player player) {
        ItemStack weapon = player.getUseItem();
        if (!player.isUsingItem() || !isAiming(weapon)) {
            return;
        }
        ((ScopedHeavyCrossbowItem) weapon.getItem()).performShooting(player.level(), player, player.getUsedItemHand(), weapon, ARROW_POWER, 1.0F, null);
        player.stopUsingItem();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPYGLASS_STOP_USING, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (isAiming(player.getItemInHand(hand))) {
            player.playSound(SoundEvents.SPYGLASS_USE, 1.0F, 1.0F);
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        return super.use(level, player, hand);
    }

    /** Letting go of a loaded one lowers the scope. */
    @Override
    public boolean releaseUsing(ItemStack weapon, Level level, LivingEntity user, int remainingTime) {
        if (isAiming(weapon)) {
            user.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
        }
        return super.releaseUsing(weapon, level, user, remainingTime);
    }
}
