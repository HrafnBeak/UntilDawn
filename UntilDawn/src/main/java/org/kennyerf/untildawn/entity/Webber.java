package org.kennyerf.untildawn.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.entity.ai.MobPathController;
import org.kennyerf.untildawn.entity.ai.WebShotGoal;
import org.kennyerf.untildawn.pathing.PathAbilities;

/**
 * A spider that bites like any other (in daylight too), and every 10 seconds shoots a {@link WebShot} at its target,
 * leaving Decaying Cobwebs where it lands.
 */
public class Webber extends Spider implements WaveMob {
    private static final int WEB_COOLDOWN = 200;
    private static final float WEB_SPEED = 1.6F;
    private static final float WEB_SPREAD = 2.0F;

    // Created lazily: Mob's constructor calls registerGoals() before field initializers run.
    private MobPathController pathController;
    private int webCooldown = WEB_COOLDOWN / 2;

    public Webber(EntityType<? extends Webber> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return WaveSpiders.createAttributes();
    }

    @Override
    public PathAbilities pathAbilities() {
        return PathAbilities.SPIDER;
    }

    @Override
    public UntilDawnConfig.MobSettings settings() {
        return UntilDawnConfig.WEBBER;
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
        this.goalSelector.addGoal(3, new WebShotGoal(this));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        // Empty spider data: none of the random potion effects a spider can spawn with on Hard.
        groupData = super.finalizeSpawn(level, difficulty, spawnReason, new Spider.SpiderEffectsGroupData());
        WaveSpiders.dropRiders(this);
        return groupData;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (webCooldown > 0) {
            webCooldown--;
        }
    }

    public boolean isWebReady() {
        return webCooldown <= 0;
    }

    /** Shoots a web at the target, aimed a little high to make up for it dropping. */
    public void shootWeb(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        webCooldown = WEB_COOLDOWN;
        WebShot web = new WebShot(level, this);
        double xd = target.getX() - this.getX();
        double yd = target.getY(1.0 / 3.0) - web.getY();
        double zd = target.getZ() - this.getZ();
        double lift = Math.sqrt(xd * xd + zd * zd) * 0.2;
        Projectile.spawnProjectileUsingShoot(web, level, ItemStack.EMPTY, xd, yd + lift, zd, WEB_SPEED, WEB_SPREAD);
        this.playSound(SoundEvents.LLAMA_SPIT, 1.0F, 0.6F + this.getRandom().nextFloat() * 0.2F);
    }
}
