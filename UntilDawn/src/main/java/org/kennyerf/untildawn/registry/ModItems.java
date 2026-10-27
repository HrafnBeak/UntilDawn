package org.kennyerf.untildawn.registry;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ToolMaterial;
import org.kennyerf.untildawn.UntilDawn;

public final class ModItems {
    public static final Item CORE = register("core",
            new BlockItem(ModBlocks.CORE, properties("core").useBlockDescriptionPrefix().rarity(Rarity.EPIC).stacksTo(1)));

    public static final Item UNSTABLE_PLANKS = register("unstable_planks",
            new BlockItem(ModBlocks.UNSTABLE_PLANKS, properties("unstable_planks").useBlockDescriptionPrefix()));

    public static final Item UNSTABLE_LADDER = register("unstable_ladder",
            new BlockItem(ModBlocks.UNSTABLE_LADDER, properties("unstable_ladder").useBlockDescriptionPrefix()));

    public static final Item WORKBENCH = register("workbench",
            new BlockItem(ModBlocks.WORKBENCH, properties("workbench").useBlockDescriptionPrefix().stacksTo(1)));

    /** Dropped only by wave mobs; pays for research at a Workbench. */
    public static final Item SCRAP = register("scrap", new Item(properties("scrap")));

    /** The Assassin's knife: an iron sword with less bite (4 damage, against 6) but quicker (2.6 attacks a second, against 1.6). */
    public static final Item IRON_DAGGER = register("iron_dagger",
            new Item(properties("iron_dagger").sword(ToolMaterial.IRON, 1.0F, -1.4F)));

    public static final Item MINER_SPAWN_EGG = spawnEgg("miner_spawn_egg", ModEntities.MINER);
    public static final Item BUILDER_SPAWN_EGG = spawnEgg("builder_spawn_egg", ModEntities.BUILDER);
    public static final Item BOMBER_SPAWN_EGG = spawnEgg("bomber_spawn_egg", ModEntities.BOMBER);
    public static final Item ARCHER_SPAWN_EGG = spawnEgg("archer_spawn_egg", ModEntities.ARCHER);
    public static final Item LONGBOWMAN_SPAWN_EGG = spawnEgg("longbowman_spawn_egg", ModEntities.LONGBOWMAN);
    public static final Item SPRINTER_SPAWN_EGG = spawnEgg("sprinter_spawn_egg", ModEntities.SPRINTER);
    public static final Item SWARMER_SPAWN_EGG = spawnEgg("swarmer_spawn_egg", ModEntities.SWARMER);
    public static final Item WARRIOR_SPAWN_EGG = spawnEgg("warrior_spawn_egg", ModEntities.WARRIOR);
    public static final Item ASSASSIN_SPAWN_EGG = spawnEgg("assassin_spawn_egg", ModEntities.ASSASSIN);
    public static final Item WEBBER_SPAWN_EGG = spawnEgg("webber_spawn_egg", ModEntities.WEBBER);
    public static final Item SNEAKING_SPIDER_SPAWN_EGG = spawnEgg("sneaking_spider_spawn_egg", ModEntities.SNEAKING_SPIDER);

    private ModItems() {
    }

    private static ResourceKey<Item> key(String name) {
        return ResourceKey.create(Registries.ITEM, UntilDawn.id(name));
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

    /** Everything the mod adds, in one creative tab. */
    public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, UntilDawn.id("untildawn"),
            FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.untildawn"))
                    .icon(() -> new ItemStack(CORE))
                    .displayItems((parameters, output) -> {
                        output.accept(CORE);
                        output.accept(UNSTABLE_PLANKS);
                        output.accept(UNSTABLE_LADDER);
                        output.accept(WORKBENCH);
                        output.accept(SCRAP);
                        output.accept(IRON_DAGGER);
                        output.accept(MINER_SPAWN_EGG);
                        output.accept(BUILDER_SPAWN_EGG);
                        output.accept(BOMBER_SPAWN_EGG);
                        output.accept(ARCHER_SPAWN_EGG);
                        output.accept(LONGBOWMAN_SPAWN_EGG);
                        output.accept(SPRINTER_SPAWN_EGG);
                        output.accept(SWARMER_SPAWN_EGG);
                        output.accept(WARRIOR_SPAWN_EGG);
                        output.accept(ASSASSIN_SPAWN_EGG);
                        output.accept(WEBBER_SPAWN_EGG);
                        output.accept(SNEAKING_SPIDER_SPAWN_EGG);
                    })
                    .build());

    public static void init() {
        // Registered by loading the class.
    }
}
