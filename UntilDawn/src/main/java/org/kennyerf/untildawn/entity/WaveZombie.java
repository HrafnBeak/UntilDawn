package org.kennyerf.untildawn.entity;

import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
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
import org.kennyerf.untildawn.entity.ai.HuntPlayersGoal;
import org.kennyerf.untildawn.entity.ai.MobPathController;
import org.kennyerf.untildawn.entity.ai.SquadObjectiveGoal;

/**
 * An adult zombie with a fixed tool and an iron helmet.
 */
public abstract class WaveZombie extends Zombie implements WaveMob {
    // Created lazily: Mob's constructor calls registerGoals() before field initializers run.
    private MobPathController pathController;

    protected WaveZombie(EntityType<? extends WaveZombie> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes().add(Attributes.FOLLOW_RANGE, 64.0);
    }

    protected abstract Item tool();

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
        WaveMobs.removeVanillaMovement(this.goalSelector, this);
        this.goalSelector.addGoal(1, new SquadObjectiveGoal<>(this));
        this.targetSelector.addGoal(1, new HuntPlayersGoal<>(this));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        // Always an adult, never a chicken jockey.
        groupData = super.finalizeSpawn(level, difficulty, spawnReason, new Zombie.ZombieGroupData(false, false));
        WaveMobs.equip(this, new ItemStack(tool()), new ItemStack(Items.IRON_HELMET));
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
