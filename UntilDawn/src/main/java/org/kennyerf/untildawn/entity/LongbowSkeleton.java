package org.kennyerf.untildawn.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.config.UntilDawnConfig;

/**
 * Skeleton with much more range: by default starts shooting at 48 blocks with faster, flatter, more accurate
 * arrows (see {@code longbowman.*} in the config). It fires straight at targets it can see, and arcs arrows
 * high over cover onto targets it can't (plunging fire). Wears leather armour, shoots a Punch I bow, and its arrows leave
 * a white trail until they hit a block.
 */
public class LongbowSkeleton extends ArcherSkeleton {
    public LongbowSkeleton(EntityType<? extends LongbowSkeleton> type, Level level) {
        super(type, level);
    }

    @Override
    public UntilDawnConfig.MobSettings settings() {
        return UntilDawnConfig.LONGBOWMAN;
    }

    /** A full suit of leather armour, and a bow with Punch I. */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        groupData = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        setArmour(EquipmentSlot.HEAD, Items.LEATHER_HELMET);
        setArmour(EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE);
        setArmour(EquipmentSlot.LEGS, Items.LEATHER_LEGGINGS);
        setArmour(EquipmentSlot.FEET, Items.LEATHER_BOOTS);
        ItemStack bow = new ItemStack(Items.BOW);
        bow.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PUNCH), 1);
        this.setItemSlot(EquipmentSlot.MAINHAND, bow);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        return groupData;
    }

    private void setArmour(EquipmentSlot slot, Item item) {
        this.setItemSlot(slot, new ItemStack(item));
        this.setDropChance(slot, 0.0F);
    }

    @Override
    protected boolean canPlunge() {
        return true;
    }

    /** Its arrows leave a white trail. */
    @Override
    protected boolean hasTrail() {
        return true;
    }

    @Override
    protected float accuracyFactor() {
        return 0.5F;
    }

    /** Keeps the vanilla fire rate; only Archers fire faster. */
    @Override
    protected double fireRateFactor() {
        return 1.0;
    }

    /** Keeps full damage; only Archers deal half. */
    @Override
    protected double damageFactor() {
        return 1.0;
    }
}
