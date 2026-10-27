package org.kennyerf.untildawn.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.entity.ai.HuntPlayersGoal;
import org.kennyerf.untildawn.entity.ai.MobPathController;
import org.kennyerf.untildawn.entity.ai.SquadObjectiveGoal;
import org.kennyerf.untildawn.game.WaveManager;
import org.kennyerf.untildawn.pathing.PathAbilities;

/**
 * Creeper that supports its squad first: it blasts walls squadmates are stuck behind (warning them so they
 * hold back, then rush through), lends itself to nearby squads stuck at a wall, and plans its own routes
 * through walls when that beats walking around. Detonates on the Core.
 */
public class BomberCreeper extends Creeper implements WaveMob {
    // Created lazily: Mob's constructor calls registerGoals() before field initializers run.
    private MobPathController pathController;

    public BomberCreeper(EntityType<? extends BomberCreeper> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Creeper.createAttributes().add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    public PathAbilities pathAbilities() {
        return PathAbilities.BOMBER;
    }

    @Override
    public UntilDawnConfig.MobSettings settings() {
        return UntilDawnConfig.BOMBER;
    }

    @Override
    public double engageRange() {
        return 2.5;
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
        this.goalSelector.addGoal(1, new SquadObjectiveGoal<>(this));
        this.targetSelector.addGoal(1, new HuntPlayersGoal<>(this));
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide() && this.isAlive() && !this.isIgnited() && WaveManager.isInCoreArea(this)) {
            this.ignite();
        }
        super.tick();
    }

    @Override
    public void remove(RemovalReason reason) {
        // The vanilla explosion discards the creeper; if that happened on the Core, hit it hard.
        if (reason == RemovalReason.DISCARDED && this.isIgnited() && this.level() instanceof ServerLevel level
                && WaveManager.isInCoreArea(this)) {
            WaveManager.onBomberDetonatedAtCore(level);
        }
        super.remove(reason);
    }
}
