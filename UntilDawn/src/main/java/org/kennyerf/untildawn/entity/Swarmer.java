package org.kennyerf.untildawn.entity;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.pathing.PathAbilities;

/**
 * Simple melee-only zombie: it fights players and walks straight for the Core. It cannot dig, build, or
 * do anything besides combat. Spawns with random leather armor pieces and a wood-to-copper sword.
 */
public class Swarmer extends WaveZombie {
    public Swarmer(EntityType<? extends Swarmer> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes().add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected Item tool() {
        // Random wood-to-copper sword, picked in finalizeSpawn.
        return Items.WOODEN_SWORD;
    }

    @Override
    public PathAbilities pathAbilities() {
        return PathAbilities.WALKER;
    }

    @Override
    public UntilDawnConfig.MobSettings settings() {
        return UntilDawnConfig.SWARMER;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        groupData = super.finalizeSpawn(level, difficulty, spawnReason, groupData);

        // Random leather armor pieces (each slot independently).
        if (level.getRandom().nextFloat() < 0.5F) {
            setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            setDropChance(EquipmentSlot.HEAD, 0.0F);
        }
        if (level.getRandom().nextFloat() < 0.5F) {
            setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            setDropChance(EquipmentSlot.CHEST, 0.0F);
        }
        if (level.getRandom().nextFloat() < 0.5F) {
            setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
            setDropChance(EquipmentSlot.LEGS, 0.0F);
        }
        if (level.getRandom().nextFloat() < 0.5F) {
            setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            setDropChance(EquipmentSlot.FEET, 0.0F);
        }

        // Random wood-to-copper sword.
        Item sword = switch (level.getRandom().nextInt(4)) {
            case 1 -> Items.STONE_SWORD;
            case 2 -> Items.IRON_SWORD;
            case 3 -> Items.COPPER_SWORD;
            default -> Items.WOODEN_SWORD;
        };
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(sword));
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);

        setCanPickUpLoot(false);
        return groupData;
    }
}