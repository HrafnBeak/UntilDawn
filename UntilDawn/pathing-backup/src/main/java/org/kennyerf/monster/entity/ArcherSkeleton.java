package org.kennyerf.monster.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
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
 * Basic skeleton. Paths until it is in bow range with a clear shot, then shoots. Bow range, arrow speed and
 * damage come from the config (the vanilla bow goal has a fixed 15-block range, so it is swapped out).
 */
public class ArcherSkeleton extends Skeleton implements WaveMob {
    // Created lazily: Mob's constructor calls registerGoals() and reassessWeaponGoal() before field initializers run.
    private MobPathController pathController;
    private RangedBowAttackGoal<ArcherSkeleton> bowGoal;
    private float bowGoalRange;

    public ArcherSkeleton(EntityType<? extends ArcherSkeleton> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSkeleton.createAttributes().add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    public PathAbilities pathAbilities() {
        return PathAbilities.WALKER;
    }

    @Override
    public MonsterConfig.MobSettings settings() {
        return MonsterConfig.ARCHER;
    }

    @Override
    public MobPathController pathController() {
        if (pathController == null) {
            pathController = new MobPathController(this, pathAbilities());
        }
        return pathController;
    }

    private double bowRange() {
        return settings().range.get();
    }

    /** Stop a little inside bow range so the bow goal doesn't immediately step back out. */
    @Override
    public double engageRange() {
        return bowRange() * 0.85;
    }

    @Override
    public boolean isRanged() {
        return true;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(2, new PathToObjectiveGoal<>(this));
        this.targetSelector.addGoal(1, new HuntPlayersGoal<>(this));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        groupData = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        this.setCanPickUpLoot(false);
        return groupData;
    }

    @Override
    public void reassessWeaponGoal() {
        super.reassessWeaponGoal();
        if (this.level() == null || this.level().isClientSide()) {
            return;
        }
        float range = (float) bowRange();
        if (bowGoal == null || bowGoalRange != range) {
            bowGoal = new RangedBowAttackGoal<>(this, 1.0, 20, range);
            bowGoalRange = range;
        }
        // Swap the vanilla 15-block bow goal for one with the configured range.
        this.goalSelector.removeAllGoals(goal -> goal instanceof RangedBowAttackGoal);
        if (this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW)).is(Items.BOW)) {
            bowGoal.setMinAttackInterval(this.level().getDifficulty() == Difficulty.HARD ? this.getHardAttackInterval() : this.getAttackInterval());
            this.goalSelector.addGoal(4, bowGoal);
        }
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        ItemStack bowItem = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        ItemStack projectile = this.getProjectile(bowItem);
        AbstractArrow arrow = this.getArrow(projectile, power, bowItem);
        double damage = settings().damage.get();
        if (damage != 1.0) {
            // Same roll as AbstractArrow#setBaseDamageFromMob, scaled.
            int difficulty = this.level().getDifficulty().getId();
            arrow.setBaseDamage((power * 2.0 + this.getRandom().triangle(difficulty * 0.11, 0.57425)) * damage);
        }
        double xd = target.getX() - this.getX();
        double yd = target.getY(1.0 / 3.0) - arrow.getY();
        double zd = target.getZ() - this.getZ();
        double distance = Math.sqrt(xd * xd + zd * zd);
        if (this.level() instanceof ServerLevel serverLevel) {
            double speed = settings().arrowSpeed.get();
            // Aim above the target to make up for the arrow dropping; faster arrows drop less.
            double lift = 0.035 * distance * distance / (speed * speed);
            Projectile.spawnProjectileUsingShoot(arrow, serverLevel, projectile, xd, yd + lift, zd,
                    (float) speed, this.rangedAttackUncertainty(serverLevel) * accuracyFactor());
        }
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    /** Multiplier on vanilla arrow spread. */
    protected float accuracyFactor() {
        return 1.0F;
    }
}
