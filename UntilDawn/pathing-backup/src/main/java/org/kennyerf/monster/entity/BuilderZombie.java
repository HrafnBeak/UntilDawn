package org.kennyerf.monster.entity;

import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;
import org.kennyerf.monster.config.MonsterConfig;
import org.kennyerf.monster.entity.ai.HuntPlayersGoal;
import org.kennyerf.monster.entity.ai.MobPathController;
import org.kennyerf.monster.entity.ai.PathToObjectiveGoal;
import org.kennyerf.monster.pathing.PathAbilities;

/**
 * Zombie with a wooden shovel and an iron helmet. Plans bridges, staircases and pillars into its routes
 * and places the blocks; teammates walk up the stairs and across the bridges behind it.
 */
public class BuilderZombie extends Zombie implements WaveMob {
    // Created lazily: Mob's constructor calls registerGoals() before field initializers run.
    private MobPathController pathController;

    public BuilderZombie(EntityType<? extends BuilderZombie> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes().add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    public PathAbilities pathAbilities() {
        return PathAbilities.BUILDER;
    }

    @Override
    public MonsterConfig.MobSettings settings() {
        return MonsterConfig.BUILDER;
    }

    @Override
    public MobPathController pathController() {
        if (pathController == null) {
            pathController = new MobPathController(this, pathAbilities());
        }
        return pathController;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(2, new PathToObjectiveGoal<>(this));
        this.targetSelector.addGoal(1, new HuntPlayersGoal<>(this));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        // Always an adult, never a chicken jockey.
        groupData = super.finalizeSpawn(level, difficulty, spawnReason, new Zombie.ZombieGroupData(false, false));
        WaveMobs.equip(this, new ItemStack(Items.WOODEN_SHOVEL), new ItemStack(Items.IRON_HELMET));
        return groupData;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        // Equipment is fixed; see finalizeSpawn.
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }
}
