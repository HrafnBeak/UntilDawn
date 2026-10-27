package org.kennyerf.untildawn.item;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A crossbow that loads and fires by itself for as long as right click is held, one arrow from the inventory a shot,
 * faster the longer it is held: from one shot a second to {@link #MAX_SHOTS_PER_SECOND} over {@link #SPOOL_TICKS}.
 * It never holds a loaded arrow, so Quick Charge, Piercing and Multishot have nothing to do with it (it is left out
 * of the crossbow enchantment tag).
 */
public class ChainCrossbowItem extends CrossbowItem {
    public static final int MAX_SHOTS_PER_SECOND = 4;
    /** Time held to reach the top rate. */
    public static final int SPOOL_TICKS = 40;
    private static final double START_RATE = 1.0 / 20.0;
    private static final double MAX_RATE = MAX_SHOTS_PER_SECOND / 20.0;
    private static final float ARROW_POWER = 3.15F;

    public ChainCrossbowItem(Properties properties) {
        super(properties);
    }

    @Override
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return NetShotItem.ARROWS_WITHOUT_NETS;
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return NetShotItem.ARROWS_WITHOUT_NETS;
    }

    /** How many shots a hold has made after {@code ticksHeld}: the rate rises evenly, and the first shot comes when half of it is due. */
    public static int shotsBy(int ticksHeld) {
        double t = Math.max(0, ticksHeld);
        double spooling = Math.min(t, SPOOL_TICKS);
        double due = START_RATE * spooling + (MAX_RATE - START_RATE) * spooling * spooling / (2.0 * SPOOL_TICKS) + MAX_RATE * (t - spooling);
        return (int) Math.floor(due + 0.5);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.getProjectile(player.getItemInHand(hand)).isEmpty()) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity shooter, ItemStack weapon, int ticksRemaining) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int held = weapon.getUseDuration(shooter) - ticksRemaining;
        if (shotsBy(held) <= shotsBy(held - 1)) {
            return;
        }
        List<ItemStack> arrows = draw(weapon, shooter.getProjectile(weapon), shooter);
        if (arrows.isEmpty()) {
            shooter.stopUsingItem();
            return;
        }
        shoot(serverLevel, shooter, shooter.getUsedItemHand(), weapon, arrows, ARROW_POWER, 1.0F, shooter instanceof Player, null);
        if (shooter instanceof ServerPlayer player) {
            player.awardStat(Stats.ITEM_USED.get(this));
        }
    }
}
