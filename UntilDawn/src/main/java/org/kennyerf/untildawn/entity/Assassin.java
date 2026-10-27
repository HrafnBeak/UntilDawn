package org.kennyerf.untildawn.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.entity.ai.AssassinGoal;
import org.kennyerf.untildawn.entity.ai.HuntPlayersGoal;
import org.kennyerf.untildawn.entity.ai.MobPathController;
import org.kennyerf.untildawn.pathing.PathAbilities;
import org.kennyerf.untildawn.registry.ModItems;

/**
 * A stray in a black leather chestplate that hunts alone (never in a squad) and sprint-jumps its way to players like a
 * Sprinter. It fights in a loop (see {@link AssassinGoal}): from 6 blocks it fires a crossbow bolt that slows (Slowness II,
 * 5 seconds); while the target is slowed it closes in with an Iron Dagger; once the slowness wears off it sprints back
 * out to 6 blocks while it reloads, then turns and fires again.
 */
public class Assassin extends Stray implements WaveMob {
    /** The crossbow range it keeps to, and fires from. */
    public static final double RANGE = 6.0;
    private static final int SLOW_TICKS = 100;
    private static final int SLOW_AMPLIFIER = 1;
    /** A pillager's bolt speed; and the Assassin is a good shot. */
    private static final float BOLT_SPEED = 1.6F;
    private static final float BOLT_SPREAD = 1.0F;
    private static final double BOLT_DAMAGE = 2.0;

    // Created lazily: Mob's constructor calls registerGoals() before field initializers run.
    private MobPathController pathController;
    /** The weapon just put away, on its way to the off hand, and the tick it was put away. */
    private @Nullable ItemStack holstered;
    private int holsteredAt;

    public Assassin(EntityType<? extends Assassin> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSkeleton.createAttributes().add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    public PathAbilities pathAbilities() {
        return PathAbilities.SPRINTER;
    }

    @Override
    public UntilDawnConfig.MobSettings settings() {
        return UntilDawnConfig.ASSASSIN;
    }

    @Override
    public boolean joinsSquads() {
        return false;
    }

    @Override
    public MobPathController pathController() {
        if (pathController == null) {
            pathController = new MobPathController(this, pathAbilities());
        }
        return pathController;
    }

    @Override
    public double engageRange() {
        return RANGE;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // AssassinGoal does all the moving, the vanilla sun-dodging and strolling included.
        this.goalSelector.removeAllGoals(goal -> goal.getFlags().contains(Goal.Flag.MOVE));
        this.goalSelector.addGoal(1, new AssassinGoal(this));
        this.targetSelector.addGoal(1, new HuntPlayersGoal<>(this));
    }

    /** The vanilla bow and melee goals are replaced by {@link AssassinGoal}. */
    @Override
    public void reassessWeaponGoal() {
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        groupData = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        ItemStack chestplate = new ItemStack(Items.LEATHER_CHESTPLATE);
        chestplate.set(DataComponents.DYED_COLOR, new DyedItemColor(DyeColor.BLACK.getTextureDiffuseColor()));
        equip(EquipmentSlot.CHEST, chestplate);
        equip(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        equip(EquipmentSlot.OFFHAND, new ItemStack(ModItems.IRON_DAGGER));
        this.setCanPickUpLoot(false);
        return groupData;
    }

    private void equip(EquipmentSlot slot, ItemStack stack) {
        this.setItemSlot(slot, stack);
        this.setDropChance(slot, 0.0F);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        // Equipment is fixed; see finalizeSpawn.
    }

    public boolean holdsDagger() {
        return this.getMainHandItem().is(ModItems.IRON_DAGGER);
    }

    /**
     * Swaps the crossbow and the dagger between its hands; the one in the main hand is the one it uses. The weapon put
     * away only reaches the off hand on the next tick (see {@link #customServerAiStep}): a swap in one go goes to
     * clients as "swap your hands" rather than what each hand holds, and a client that started watching the
     * Assassin in between swaps hands that were already swapped, then shows the crossbow in both once it is next wound.
     */
    public void swapWeapons() {
        this.stopUsingItem();
        ItemStack drawn = holstered != null ? holstered : this.getOffhandItem();
        holstered = this.getMainHandItem();
        holsteredAt = this.tickCount;
        this.setItemSlot(EquipmentSlot.MAINHAND, drawn);
        this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (holstered != null) {
            if (this.tickCount > holsteredAt) {
                this.setItemSlot(EquipmentSlot.OFFHAND, holstered);
                holstered = null;
            }
        } else if (this.getOffhandItem().isEmpty()) {
            // Saved halfway through a swap: the weapon being put away wasn't saved.
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(holdsDagger() ? Items.CROSSBOW : ModItems.IRON_DAGGER));
        }
    }

    public boolean isCrossbowCharged() {
        ItemStack crossbow = this.getMainHandItem();
        return crossbow.is(Items.CROSSBOW) && CrossbowItem.isCharged(crossbow);
    }

    /** Winds the crossbow in the main hand; the crossbow loads itself once wound for long enough. */
    public void chargeCrossbow() {
        if (isCrossbowCharged()) {
            if (this.isUsingItem()) {
                this.stopUsingItem();
            }
        } else if (!this.isUsingItem() && this.getMainHandItem().is(Items.CROSSBOW)) {
            this.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }

    /** Fires the loaded crossbow at the target: a bolt that slows whoever it hits. */
    public void fireCrossbow(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel level) || !isCrossbowCharged()) {
            return;
        }
        // Let go of the crossbow: still wound past its charge time, it would load again on the next tick and fire a
        // bolt every tick. Winding starts over from nothing.
        this.stopUsingItem();
        ItemStack crossbow = this.getMainHandItem();
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        Arrow bolt = new Arrow(level, this, new ItemStack(Items.ARROW), crossbow);
        bolt.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, SLOW_AMPLIFIER));
        bolt.setBaseDamage(BOLT_DAMAGE * settings().damage.get());
        bolt.setCritArrow(true);
        double xd = target.getX() - this.getX();
        double yd = target.getY(1.0 / 3.0) - bolt.getY();
        double zd = target.getZ() - this.getZ();
        double lift = Math.sqrt(xd * xd + zd * zd) * 0.05;
        Projectile.spawnProjectileUsingShoot(bolt, level, new ItemStack(Items.ARROW), xd, yd + lift, zd, BOLT_SPEED, BOLT_SPREAD);
        this.playSound(SoundEvents.CROSSBOW_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }
}
