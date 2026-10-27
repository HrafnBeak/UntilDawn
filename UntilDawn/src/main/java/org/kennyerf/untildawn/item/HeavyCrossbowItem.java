package org.kennyerf.untildawn.item;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A crossbow that takes half as long again to wind and shoots twice as fast. It has the normal crossbow's enchantments
 * except Multishot, and Power and Punch as well (see the enchantment overrides in the data folder).
 */
public class HeavyCrossbowItem extends WindingCrossbowItem {
    /** How much longer than the normal crossbow it takes to wind. */
    public static final float DRAW_TIME = 1.5F;
    /** How many times the normal crossbow's arrow speed. */
    public static final float VELOCITY = 2.0F;
    /** The share of a normal crossbow's spread: it flies almost straight. */
    public static final float SPREAD = 0.1F;

    public HeavyCrossbowItem(Properties properties) {
        super(properties);
    }

    @Override
    public int windTicks(ItemStack weapon, LivingEntity user) {
        return Mth.floor(EnchantmentHelper.modifyCrossbowChargingTime(weapon, user, 1.25F) * DRAW_TIME * 20.0F);
    }

    @Override
    protected boolean load(LivingEntity shooter, ItemStack weapon) {
        List<ItemStack> drawn = draw(weapon, shooter.getProjectile(weapon), shooter);
        if (drawn.isEmpty()) {
            return false;
        }
        weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(drawn));
        return true;
    }

    @Override
    public void performShooting(Level level, LivingEntity shooter, InteractionHand hand, ItemStack weapon, float power,
                                float uncertainty, @Nullable LivingEntity targetOverride) {
        super.performShooting(level, shooter, hand, weapon, power * VELOCITY, uncertainty * SPREAD, targetOverride);
    }
}
