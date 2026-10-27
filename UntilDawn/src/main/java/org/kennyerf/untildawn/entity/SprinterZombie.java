package org.kennyerf.untildawn.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.pathing.PathAbilities;

/**
 * Fast, empty-handed zombie that plans its routes with Baritone and sprint-jumps gaps the others have to go around
 * or bridge. It can't dig or build. Baritone only plans inside the client; on a dedicated server (or without
 * Baritone installed) the mod's own planner plans its gap jumps, but not Baritone's jumps around walls.
 */
public class SprinterZombie extends WaveZombie {
    /** A vanilla zombie's is 0.23. */
    private static final double SPEED = 0.3;

    public SprinterZombie(EntityType<? extends SprinterZombie> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return WaveZombie.createAttributes().add(Attributes.MOVEMENT_SPEED, SPEED);
    }

    @Override
    protected Item tool() {
        return Items.AIR;
    }

    @Override
    public PathAbilities pathAbilities() {
        return PathAbilities.SPRINTER;
    }

    @Override
    public UntilDawnConfig.MobSettings settings() {
        return UntilDawnConfig.SPRINTER;
    }
}
