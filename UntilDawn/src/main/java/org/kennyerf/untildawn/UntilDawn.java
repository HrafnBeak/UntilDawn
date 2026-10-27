package org.kennyerf.untildawn;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.kennyerf.untildawn.command.UntilDawnCommand;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.debug.PathDebug;
import org.kennyerf.untildawn.game.WaveManager;
import org.kennyerf.untildawn.pathing.AccessCorridors;
import org.kennyerf.untildawn.registry.ModBlockEntities;
import org.kennyerf.untildawn.registry.ModBlocks;
import org.kennyerf.untildawn.registry.ModComponents;
import org.kennyerf.untildawn.workbench.Workbench;
import org.kennyerf.untildawn.registry.ModEntities;
import org.kennyerf.untildawn.registry.ModItems;
import org.kennyerf.untildawn.squad.Squads;
import org.kennyerf.untildawn.world.BlockDamage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UntilDawn implements ModInitializer {
    public static final String MOD_ID = "untildawn";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        UntilDawnConfig.load();
        ModBlocks.init();
        ModBlockEntities.init();
        ModComponents.init();
        ModEntities.init();
        ModItems.init();
        Workbench.init();
        WaveManager.init();
        Squads.init();
        AccessCorridors.init();
        BlockDamage.init();
        PathDebug.init();
        UntilDawnCommand.init();
    }
}
