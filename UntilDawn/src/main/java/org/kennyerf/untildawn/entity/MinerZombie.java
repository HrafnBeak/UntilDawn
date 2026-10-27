package org.kennyerf.untildawn.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.pathing.PathAbilities;

/**
 * Zombie with a wooden pickaxe. Plans routes straight through blocks when digging is quicker than walking
 * around, digs tunnels squadmates ask for, and asks Builders for bridges and stairs where it can't go on.
 */
public class MinerZombie extends WaveZombie {
    public MinerZombie(EntityType<? extends MinerZombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item tool() {
        return Items.WOODEN_PICKAXE;
    }

    @Override
    public PathAbilities pathAbilities() {
        return PathAbilities.MINER;
    }

    @Override
    public UntilDawnConfig.MobSettings settings() {
        return UntilDawnConfig.MINER;
    }
}
