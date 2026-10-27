package org.kennyerf.untildawn.entity;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.UntilDawn;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.entity.ai.HuntPlayersGoal;
import org.kennyerf.untildawn.entity.ai.MobPathController;
import org.kennyerf.untildawn.entity.ai.SquadObjectiveGoal;
import org.kennyerf.untildawn.pathing.PathAbilities;

/**
 * Armored husk with a mix of iron and chainmail, a copper-iron sword, and a shield. After taking damage it
 * raises its shield for a random period before resuming the attack. It handles the shield like a player does: it
 * can't attack while blocking, is slowed (to 0.7 of its speed, so it keeps advancing behind the shield), and an axe hit
 * disables the shield for a while.
 */
public class Warrior extends Zombie implements WaveMob {
    // Created lazily: Mob's constructor calls registerGoals() before field initializers run.
    private MobPathController pathController;
    /** Blocking slows it to 0.7 of its speed. */
    private static final AttributeModifier BLOCKING_SLOWDOWN = new AttributeModifier(UntilDawn.id("shield_slowdown"), -0.3,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    /** Ticks remaining before the shield is lowered. */
    private int shieldTicks;
    /** Ticks the shield stays unusable after an axe hit. */
    private int shieldDisabledTicks;
    /** Previous hurtTime value, used to detect newly taken damage. */
    private int lastHurtTime;

    public Warrior(EntityType<? extends Warrior> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes().add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    public PathAbilities pathAbilities() {
        return PathAbilities.WALKER;
    }

    @Override
    public UntilDawnConfig.MobSettings settings() {
        return UntilDawnConfig.WARRIOR;
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
        WaveMobs.removeVanillaMovement(this.goalSelector, this);
        this.goalSelector.addGoal(1, new SquadObjectiveGoal<>(this));
        this.targetSelector.addGoal(1, new HuntPlayersGoal<>(this));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        // Always an adult, never a chicken jockey.
        groupData = super.finalizeSpawn(level, difficulty, spawnReason, new Zombie.ZombieGroupData(false, false));

        RandomSource random = level.getRandom();

        // Mix of iron and chainmail armor.
        if (random.nextBoolean()) {
            setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        } else {
            setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
        }
        setDropChance(EquipmentSlot.HEAD, 0.0F);

        if (random.nextBoolean()) {
            setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        } else {
            setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
        }
        setDropChance(EquipmentSlot.CHEST, 0.0F);

        if (random.nextBoolean()) {
            setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        } else {
            setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
        }
        setDropChance(EquipmentSlot.LEGS, 0.0F);

        if (random.nextBoolean()) {
            setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        } else {
            setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.CHAINMAIL_BOOTS));
        }
        setDropChance(EquipmentSlot.FEET, 0.0F);

        // Copper-iron sword.
        ItemStack sword = new ItemStack(random.nextBoolean() ? Items.IRON_SWORD : Items.COPPER_SWORD);
        setItemSlot(EquipmentSlot.MAINHAND, sword);
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);

        // Shield in the off hand.
        setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        setDropChance(EquipmentSlot.OFFHAND, 0.0F);

        setCanPickUpLoot(false);
        return groupData;
    }

    @Override
    public void tick() {
        super.tick();
        // Detect newly taken damage: hurtTime jumps up when hit.
        if (!level().isClientSide() && hurtTime > 0 && hurtTime > lastHurtTime && isAlive() && shieldDisabledTicks == 0) {
            shieldTicks = 20 + getRandom().nextInt(40); // 1 to 3 seconds
            startUsingItem(InteractionHand.OFF_HAND);
        }
        lastHurtTime = hurtTime;

        if (shieldDisabledTicks > 0) {
            shieldDisabledTicks--;
        }
        if (!level().isClientSide()) {
            updateBlockingSlowdown();
        }

        if (shieldTicks > 0) {
            shieldTicks--;
            if (shieldTicks == 0) {
                stopUsingItem();
            }
        }
    }

    private void updateBlockingSlowdown() {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean blocking = isUsingItem();
        if (blocking && !speed.hasModifier(BLOCKING_SLOWDOWN.id())) {
            speed.addTransientModifier(BLOCKING_SLOWDOWN);
        } else if (!blocking && speed.hasModifier(BLOCKING_SLOWDOWN.id())) {
            speed.removeModifier(BLOCKING_SLOWDOWN.id());
        }
    }

    /** Like a player, it can't attack while its shield is up. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        return !isUsingItem() && super.doHurtTarget(level, target);
    }

    /** Whoever the blocked hit came from may disable the shield with an axe, as against a player. */
    @Override
    protected void blockUsingItem(ServerLevel level, LivingEntity attacker, DamageSource source, float damage, boolean fullyBlocked) {
        super.blockUsingItem(level, attacker, source, damage, fullyBlocked);
        float seconds = attacker.getSecondsToDisableBlocking();
        ItemStack shield = getItemBlockingWith();
        BlocksAttacks blocksAttacks = shield != null ? shield.get(DataComponents.BLOCKS_ATTACKS) : null;
        if (seconds > 0.0F && blocksAttacks != null) {
            // Stops the blocking and plays the sound; unlike a player a mob has no item cooldown, so keep time here.
            blocksAttacks.disable(level, this, seconds, shield);
            shieldDisabledTicks = Math.round(seconds * 20.0F * blocksAttacks.disableCooldownScale());
            shieldTicks = 0;
        }
    }

    @Override
    public double engageRange() {
        return 2.0;
    }

    @Override
    public boolean isRanged() {
        return false;
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