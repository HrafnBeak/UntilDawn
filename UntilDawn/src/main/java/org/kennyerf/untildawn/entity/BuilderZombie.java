package org.kennyerf.untildawn.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.pathing.PathAbilities;

/**
 * Zombie with a wooden shovel, whose job is making a way the whole squad can use. Out of Unstable Planks and ladders it
 * builds bridges, straight stairs, and ladders up walls or up pillars of planks it puts up to hang them on; and to get
 * down a drop too far to take, a ladder over the edge or a stair down. It gets along the ground first and climbs at the
 * end. It answers squadmates' calls for building before its own fight, unless it is hanging on a ladder or in mid-air.
 */
public class BuilderZombie extends WaveZombie {
    public BuilderZombie(EntityType<? extends BuilderZombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item tool() {
        return Items.WOODEN_SHOVEL;
    }

    @Override
    public PathAbilities pathAbilities() {
        return PathAbilities.BUILDER;
    }

    @Override
    public UntilDawnConfig.MobSettings settings() {
        return UntilDawnConfig.BUILDER;
    }
}
