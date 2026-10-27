package org.kennyerf.untildawn.item;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/**
 * A crossbow of the mod. The normal crossbow's winding is private to it, so the ones that change how long it takes or
 * what it loads redo it here: {@link #windTicks} is how long a load takes and {@link #load} what it takes from the
 * shooter. They take arrows only, no fireworks.
 */
public abstract class WindingCrossbowItem extends CrossbowItem {
    private static final ChargingSounds DEFAULT_SOUNDS = new ChargingSounds(
            Optional.of(SoundEvents.CROSSBOW_LOADING_START), Optional.of(SoundEvents.CROSSBOW_LOADING_MIDDLE), Optional.of(SoundEvents.CROSSBOW_LOADING_END));

    private boolean startSoundPlayed = false;
    private boolean midLoadSoundPlayed = false;

    protected WindingCrossbowItem(Properties properties) {
        super(properties);
    }

    @Override
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return takesNetShots() ? ARROW_ONLY : NetShotItem.ARROWS_WITHOUT_NETS;
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return getSupportedHeldProjectiles();
    }

    /** Whether Net Shots fit; the Repeater says no. */
    protected boolean takesNetShots() {
        return true;
    }

    /** Ticks to wind a load; Quick Charge shortens it. */
    public int windTicks(ItemStack weapon, LivingEntity user) {
        return getChargeDuration(weapon, user);
    }

    /** Takes ammo from the shooter into the weapon; false if it had none. */
    protected abstract boolean load(LivingEntity shooter, ItemStack weapon);

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack itemStack, int ticksRemaining) {
        if (level.isClientSide() || isCharged(itemStack)) {
            return;
        }
        ChargingSounds sounds = EnchantmentHelper.pickHighestLevel(itemStack, EnchantmentEffectComponents.CROSSBOW_CHARGING_SOUNDS).orElse(DEFAULT_SOUNDS);
        float progress = (float) (itemStack.getUseDuration(entity) - ticksRemaining) / windTicks(itemStack, entity);
        if (progress < 0.2F) {
            startSoundPlayed = false;
            midLoadSoundPlayed = false;
        }
        if (progress >= 0.2F && !startSoundPlayed) {
            startSoundPlayed = true;
            sounds.start().ifPresent(sound -> level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound.value(), SoundSource.PLAYERS, 0.5F, 1.0F));
        }
        if (progress >= 0.5F && !midLoadSoundPlayed) {
            midLoadSoundPlayed = true;
            sounds.mid().ifPresent(sound -> level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound.value(), SoundSource.PLAYERS, 0.5F, 1.0F));
        }
        if (progress >= 1.0F && load(entity, itemStack)) {
            sounds.end().ifPresent(sound -> level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound.value(), entity.getSoundSource(),
                    1.0F, 1.0F / (level.getRandom().nextFloat() * 0.5F + 1.0F) + 0.2F));
        }
    }
}
