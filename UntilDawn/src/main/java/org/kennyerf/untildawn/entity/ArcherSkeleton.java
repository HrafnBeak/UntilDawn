package org.kennyerf.untildawn.entity;

import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.entity.ai.Ballistics;
import org.kennyerf.untildawn.entity.ai.BowAttackGoal;
import org.kennyerf.untildawn.entity.ai.HuntPlayersGoal;
import org.kennyerf.untildawn.entity.ai.MobPathController;
import org.kennyerf.untildawn.entity.ai.SquadObjectiveGoal;
import org.kennyerf.untildawn.pathing.PathAbilities;

/**
 * Skeleton that fires straight at targets it can see. Bow range, arrow speed and damage come from the config,
 * and its arrows chip away at the blocks they hit ({@code archer.blockDamage}) without it ever aiming at blocks.
 */
public class ArcherSkeleton extends Skeleton implements WaveMob {
    /** On an arrow that will damage the first block it hits; removed when it hits one. */
    public static final String BLOCK_DAMAGE_TAG = "untildawn.block_damage";
    /** On an arrow that leaves a trail of {@link #TRAIL_PARTICLE} until it hits a block. */
    public static final String TRAIL_TAG = "untildawn.trail";
    public static final DustParticleOptions TRAIL_PARTICLE = new DustParticleOptions(0xFFFFFF, 1.0F);
    private static final int PLUNGE_RECHECK_TICKS = 10;

    // Created lazily: Mob's constructor calls registerGoals() before field initializers run.
    private MobPathController pathController;
    private @Nullable LivingEntity plungeTarget;
    private Ballistics.@Nullable Shot plungeShot;
    private int plungeCheckedAt = Integer.MIN_VALUE;

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
    public UntilDawnConfig.MobSettings settings() {
        return UntilDawnConfig.ARCHER;
    }

    @Override
    public MobPathController pathController() {
        if (pathController == null) {
            pathController = new MobPathController(this, pathAbilities());
        }
        return pathController;
    }

    public double bowRange() {
        return settings().range.get();
    }

    /** Stop a little inside bow range so small target movements don't make it walk again. */
    @Override
    public double engageRange() {
        return bowRange() * 0.85;
    }

    @Override
    public boolean isRanged() {
        return true;
    }

    /** Whether it can arc arrows over cover onto targets it can't see. */
    protected boolean canPlunge() {
        return false;
    }

    @Override
    public boolean canFightUnseen(LivingEntity target) {
        return plungingShot(target) != null;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new SquadObjectiveGoal<>(this));
        this.goalSelector.addGoal(4, new BowAttackGoal(this));
        this.targetSelector.addGoal(1, new HuntPlayersGoal<>(this));
    }

    /** The vanilla bow and melee goals are replaced by {@link BowAttackGoal}. */
    @Override
    public void reassessWeaponGoal() {
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        groupData = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        this.setCanPickUpLoot(false);
        return groupData;
    }

    public boolean isHoldingBow() {
        return this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW)).is(Items.BOW);
    }

    /** Ticks between shots, as for a vanilla skeleton, divided by {@link #fireRateFactor()}. */
    public int shotInterval() {
        int interval = this.level().getDifficulty() == Difficulty.HARD ? this.getHardAttackInterval()
                : this.getAttackInterval();
        return Math.max(1, (int) (interval / fireRateFactor()));
    }

    /** Multiplier on vanilla fire rate. Archers fire twice as fast. */
    protected double fireRateFactor() {
        return 2.0;
    }

    /** Multiplier on arrow damage, on top of {@code <mob>.damage}. Archers deal half damage. */
    protected double damageFactor() {
        return 0.5;
    }

    /** Direct fire, aimed a little high to make up for the arrow dropping. */
    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        double xd = target.getX() - this.getX();
        double yd = target.getY(1.0 / 3.0) - this.getEyeY();
        double zd = target.getZ() - this.getZ();
        double distance = Math.sqrt(xd * xd + zd * zd);
        double speed = settings().arrowSpeed.get();
        // Faster arrows drop less.
        double lift = 0.035 * distance * distance / (speed * speed);
        shoot(power, new Vec3(xd, yd + lift, zd));
    }

    /** Plunging fire: a high arc, drawn as hard as the distance needs, that comes down on the target from above. */
    public void performPlungingAttack(float power, Ballistics.Shot shot) {
        shoot(power, shot.direction(), shot.speed());
    }

    private void shoot(float power, Vec3 direction) {
        shoot(power, direction, settings().arrowSpeed.get());
    }

    private void shoot(float power, Vec3 direction, double speed) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemStack bowItem = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        ItemStack projectile = this.getProjectile(bowItem);
        AbstractArrow arrow = this.getArrow(projectile, power, bowItem);
        double damage = settings().damage.get() * damageFactor();
        if (damage != 1.0) {
            // Same roll as AbstractArrow#setBaseDamageFromMob, scaled.
            int difficulty = this.level().getDifficulty().getId();
            arrow.setBaseDamage((power * 2.0 + this.getRandom().triangle(difficulty * 0.11, 0.57425)) * damage);
        }
        if (settings().blockDamage != null && settings().blockDamage.get() > 0) {
            arrow.addTag(BLOCK_DAMAGE_TAG);
        }
        if (hasTrail()) {
            arrow.addTag(TRAIL_TAG);
        }
        Projectile.spawnProjectileUsingShoot(arrow, serverLevel, projectile, direction.x, direction.y, direction.z,
                (float) speed, this.rangedAttackUncertainty(serverLevel) * accuracyFactor());
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    /**
     * A plunging shot at the target, or null if this mob can't plunge, the target is out of range, or no arc clears
     * the terrain. Re-worked out every half second.
     */
    public Ballistics.@Nullable Shot plungingShot(LivingEntity target) {
        if (!canPlunge()) {
            return null;
        }
        if (target != plungeTarget || this.tickCount - plungeCheckedAt >= PLUNGE_RECHECK_TICKS) {
            plungeTarget = target;
            plungeCheckedAt = this.tickCount;
            plungeShot = this.distanceTo(target) > bowRange() ? null : Ballistics.plungingShot(this.level(), this,
                    new Vec3(this.getX(), this.getEyeY() - 0.1, this.getZ()), target.getBoundingBox().getCenter(), settings().arrowSpeed.get());
        }
        return plungeShot;
    }

    /** Whether its arrows leave a trail. */
    protected boolean hasTrail() {
        return false;
    }

    /** Multiplier on vanilla arrow spread. */
    protected float accuracyFactor() {
        return 1.0F;
    }
}
