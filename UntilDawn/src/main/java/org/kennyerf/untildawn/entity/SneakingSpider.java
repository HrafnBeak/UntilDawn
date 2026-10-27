package org.kennyerf.untildawn.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.entity.ai.MobPathController;
import org.kennyerf.untildawn.entity.ai.SneakGoal;
import org.kennyerf.untildawn.pathing.PathAbilities;

/**
 * A cave spider that creeps up on its target invisible (no particles) and silent. Within 5 blocks it shows itself for
 * good, with a gust of cloud and a whoosh, and lunges; from then on it fights like any cave spider.
 */
public class SneakingSpider extends CaveSpider implements WaveMob {
    public static final double REVEAL_RANGE = 5.0;
    /** The lunge: upward speed, and how much of the distance to the target is turned into speed towards it (capped). */
    private static final double LUNGE_UP = 0.45;
    private static final double LUNGE_PER_BLOCK = 0.14;
    private static final double MAX_LUNGE = 0.9;

    // Created lazily: Mob's constructor calls registerGoals() before field initializers run.
    private MobPathController pathController;

    public SneakingSpider(EntityType<? extends SneakingSpider> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CaveSpider.createCaveSpider().add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    public PathAbilities pathAbilities() {
        return PathAbilities.SPIDER;
    }

    @Override
    public UntilDawnConfig.MobSettings settings() {
        return UntilDawnConfig.SNEAKING_SPIDER;
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
        WaveSpiders.replaceVanillaGoals(this, this.goalSelector, this.targetSelector);
        // Ahead of the leap and the bite, which only get a go once it has shown itself.
        this.goalSelector.addGoal(2, new SneakGoal(this));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        // A cave spider never spawns with a rider or random effects.
        groupData = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        this.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        this.setSilent(true);
        return groupData;
    }

    /** Still creeping up, invisible. */
    public boolean isSneaking() {
        return this.hasEffect(MobEffects.INVISIBILITY);
    }

    /** Shows itself for good and lunges at the target. */
    public void reveal(LivingEntity target) {
        this.removeEffect(MobEffects.INVISIBILITY);
        this.setSilent(false);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY(), this.getZ(), 20, 0.5, 0.5, 0.5, 0.02);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BREEZE_JUMP, this.getSoundSource(), 1.0F, 1.0F);
        }
        Vec3 toTarget = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
        if (toTarget.lengthSqr() > 1.0E-4) {
            Vec3 lunge = toTarget.normalize().scale(Math.min(MAX_LUNGE, toTarget.length() * LUNGE_PER_BLOCK));
            this.setDeltaMovement(lunge.x, LUNGE_UP, lunge.z);
        }
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }
}
