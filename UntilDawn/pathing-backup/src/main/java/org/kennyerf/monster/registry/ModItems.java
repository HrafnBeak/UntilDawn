package org.kennyerf.monster.registry;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import org.kennyerf.monster.Monster;

public final class ModItems {
    public static final Item CORE = register("core",
            new BlockItem(ModBlocks.CORE, properties("core").useBlockDescriptionPrefix().rarity(Rarity.EPIC).stacksTo(1)));

    public static final Item MINER_SPAWN_EGG = spawnEgg("miner_spawn_egg", ModEntities.MINER);
    public static final Item BUILDER_SPAWN_EGG = spawnEgg("builder_spawn_egg", ModEntities.BUILDER);
    public static final Item BOMBER_SPAWN_EGG = spawnEgg("bomber_spawn_egg", ModEntities.BOMBER);
    public static final Item ARCHER_SPAWN_EGG = spawnEgg("archer_spawn_egg", ModEntities.ARCHER);
    public static final Item LONGBOWMAN_SPAWN_EGG = spawnEgg("longbowman_spawn_egg", ModEntities.LONGBOWMAN);

    private ModItems() {
    }

    private static ResourceKey<Item> key(String name) {
        return ResourceKey.create(Registries.ITEM, Monster.id(name));
    }

    private static Item.Properties properties(String name) {
        return new Item.Properties().setId(key(name));
    }

    private static Item spawnEgg(String name, EntityType<?> type) {
        return register(name, new SpawnEggItem(properties(name).spawnEgg(type)));
    }

    private static Item register(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, key(name), item);
    }

    public static void init() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(CORE));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
            output.accept(MINER_SPAWN_EGG);
            output.accept(BUILDER_SPAWN_EGG);
            output.accept(BOMBER_SPAWN_EGG);
            output.accept(ARCHER_SPAWN_EGG);
            output.accept(LONGBOWMAN_SPAWN_EGG);
        });
    }
}
