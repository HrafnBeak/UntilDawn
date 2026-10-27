package org.kennyerf.monster;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.kennyerf.monster.config.MonsterConfig;
import org.kennyerf.monster.debug.MonsterCommand;
import org.kennyerf.monster.debug.PathDebug;
import org.kennyerf.monster.game.WaveManager;
import org.kennyerf.monster.registry.ModBlocks;
import org.kennyerf.monster.registry.ModEntities;
import org.kennyerf.monster.registry.ModItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Monster implements ModInitializer {
    public static final String MOD_ID = "monster";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        MonsterConfig.load();
        ModBlocks.init();
        ModEntities.init();
        ModItems.init();
        WaveManager.init();
        PathDebug.init();
        MonsterCommand.init();
    }
}
