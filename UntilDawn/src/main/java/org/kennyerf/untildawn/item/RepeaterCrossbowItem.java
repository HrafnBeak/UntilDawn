package org.kennyerf.untildawn.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A crossbow that winds {@link #MAGAZINE} arrows in one load and fires them one pull at a time, at a fifth less
 * speed than the normal crossbow. Everything else is the normal crossbow's, so its enchantments work (Multishot
 * turns each pull into a volley and each loaded arrow into three).
 */
public class RepeaterCrossbowItem extends WindingCrossbowItem {
    @Override
    protected boolean takesNetShots() {
        return false;
    }

    /** Arrows wound in one load. */
    public static final int MAGAZINE = 3;
    /** The share of a normal crossbow's arrow speed. */
    public static final float VELOCITY = 0.8F;

    public RepeaterCrossbowItem(Properties properties) {
        super(properties);
    }

    /** Fires the next volley and leaves the rest in the magazine. {@code power} is the normal crossbow's. */
    @Override
    public void performShooting(Level level, LivingEntity shooter, InteractionHand hand, ItemStack weapon, float power,
                                float uncertainty, @Nullable LivingEntity targetOverride) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ChargedProjectiles charged = weapon.get(DataComponents.CHARGED_PROJECTILES);
        if (charged == null || charged.isEmpty()) {
            return;
        }
        List<ItemStack> loaded = charged.itemCopies().toList();
        int volley = Math.min(loaded.size(), EnchantmentHelper.processProjectileCount(serverLevel, weapon, shooter, 1));
        List<ItemStack> left = loaded.subList(volley, loaded.size());
        // Updated first: shooting wears the weapon, and it may break.
        weapon.set(DataComponents.CHARGED_PROJECTILES, left.isEmpty() ? ChargedProjectiles.EMPTY : ChargedProjectiles.ofNonEmpty(left));
        shoot(serverLevel, shooter, hand, weapon, loaded.subList(0, volley), power * VELOCITY, uncertainty, shooter instanceof Player, targetOverride);
        if (shooter instanceof ServerPlayer player) {
            CriteriaTriggers.SHOT_CROSSBOW.trigger(player, weapon);
            player.awardStat(Stats.ITEM_USED.get(weapon.getItem()));
        }
    }

    @Override
    protected boolean load(LivingEntity shooter, ItemStack weapon) {
        List<ItemStack> loaded = new ArrayList<>();
        for (int i = 0; i < MAGAZINE; i++) {
            loaded.addAll(draw(weapon, shooter.getProjectile(weapon), shooter));
        }
        if (loaded.isEmpty()) {
            return false;
        }
        weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(loaded));
        return true;
    }
}
