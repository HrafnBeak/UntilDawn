package org.kennyerf.untildawn.registry;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ChargedProjectiles;
import org.kennyerf.untildawn.UntilDawn;
import org.kennyerf.untildawn.item.ChainCrossbowItem;
import org.kennyerf.untildawn.entity.ThrownFireFlask;
import org.kennyerf.untildawn.entity.ThrownSapperFlask;
import org.kennyerf.untildawn.item.FlaskItem;
import org.kennyerf.untildawn.item.GrapplingGunItem;
import org.kennyerf.untildawn.item.HeavyCrossbowItem;
import org.kennyerf.untildawn.item.NetShotItem;
import org.kennyerf.untildawn.item.RepeaterCrossbowItem;
import org.kennyerf.untildawn.item.ScopedHeavyCrossbowItem;

public final class ModItems {
    public static final Item CORE = register("core",
            new BlockItem(ModBlocks.CORE, properties("core").useBlockDescriptionPrefix().rarity(Rarity.EPIC).stacksTo(1)));

    public static final Item UNSTABLE_PLANKS = register("unstable_planks",
            new BlockItem(ModBlocks.UNSTABLE_PLANKS, properties("unstable_planks").useBlockDescriptionPrefix()));

    public static final Item UNSTABLE_LADDER = register("unstable_ladder",
            new BlockItem(ModBlocks.UNSTABLE_LADDER, properties("unstable_ladder").useBlockDescriptionPrefix()));

    public static final Item WORKBENCH = register("workbench",
            new BlockItem(ModBlocks.WORKBENCH, properties("workbench").useBlockDescriptionPrefix().stacksTo(1)));

    public static final Item PRIMITIVE_BARRICADE = block("primitive_barricade", ModBlocks.PRIMITIVE_BARRICADE);
    public static final Item REINFORCED_BARRICADE = block("reinforced_barricade", ModBlocks.REINFORCED_BARRICADE);
    public static final Item SPIKY_BARRICADE = block("spiky_barricade", ModBlocks.SPIKY_BARRICADE);
    public static final Item CROSSBOW_SENTRY = block("crossbow_sentry", ModBlocks.CROSSBOW_SENTRY);
    public static final Item SPIKE_TRAP = block("spike_trap", ModBlocks.SPIKE_TRAP);

    /** Dropped only by wave mobs; pays for research at a Workbench. */
    public static final Item SCRAP = register("scrap", new Item(properties("scrap")));

    /** The Assassin's knife: an iron sword with less bite (4 damage, against 6) but quicker (2.6 attacks a second, against 1.6). */
    public static final Item IRON_DAGGER = register("iron_dagger",
            new Item(properties("iron_dagger").sword(ToolMaterial.IRON, 1.0F, -1.4F)));

    /** Pikes hit 0.5 blocks further than a sword, and swing 10% slower so they deal 10% less damage a second. */
    private static final double PIKE_REACH = 0.5;
    private static final float PIKE_ATTACK_SPEED = -2.56F;

    public static final Item WOODEN_PIKE = pike("wooden_pike", ToolMaterial.WOOD, false);
    public static final Item STONE_PIKE = pike("stone_pike", ToolMaterial.STONE, false);
    public static final Item COPPER_PIKE = pike("copper_pike", ToolMaterial.COPPER, false);
    public static final Item IRON_PIKE = pike("iron_pike", ToolMaterial.IRON, false);
    public static final Item DIAMOND_PIKE = pike("diamond_pike", ToolMaterial.DIAMOND, false);
    /** Not in the tech tree: a Diamond Pike upgraded at a smithing table, like any other netherite tool. */
    public static final Item NETHERITE_PIKE = pike("netherite_pike", ToolMaterial.NETHERITE, true);

    /** Pulls the user to the block or mob it hooks (20 blocks) for as long as use is held; crouching pulls the mob to the user instead. */
    public static final Item GRAPPLING_GUN = register("grappling_gun",
            new GrapplingGunItem(properties("grappling_gun").stacksTo(1)));

    /** A crossbow that winds three arrows in one load and fires them one at a time, 20% slower than the normal crossbow. */
    public static final Item REPEATER_CROSSBOW = register("repeater_crossbow",
            new RepeaterCrossbowItem(properties("repeater_crossbow").stacksTo(1).durability(465)
                    .component(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).enchantable(1)));

    /** Winds 1.5 times as slowly as the normal crossbow and shoots twice as fast. Takes Power, not Multishot. */
    public static final Item HEAVY_CROSSBOW = register("heavy_crossbow",
            new HeavyCrossbowItem(properties("heavy_crossbow").stacksTo(1).durability(465)
                    .component(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).enchantable(1)));

    /** A Heavy Crossbow with a Scope on it; made and unmade by shapeless crafting (see ScopeRecipes). */
    public static final Item SCOPED_HEAVY_CROSSBOW = register("scoped_heavy_crossbow",
            new ScopedHeavyCrossbowItem(properties("scoped_heavy_crossbow").stacksTo(1).durability(465)
                    .component(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).enchantable(1)));

    public static final Item SCOPE = register("scope", new Item(properties("scope").stacksTo(1)));

    /** Loads and fires by itself while right click is held, faster the longer it is held, up to 4 shots a second. */
    public static final Item CHAIN_CROSSBOW = register("chain_crossbow",
            new ChainCrossbowItem(properties("chain_crossbow").stacksTo(1).durability(465)
                    .component(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).enchantable(1)));

    /** Ammo for crossbows: slow and weak, and bursts into Decaying Cobwebs where it lands. */
    public static final Item NET_SHOT = register("net_shot", new NetShotItem(properties("net_shot")));

    /** Thrown; leaves a Low Fire where it breaks. */
    public static final Item FIRE_FLASK = register("fire_flask",
            new FlaskItem(properties("fire_flask").stacksTo(16), ThrownFireFlask::new));

    /** Thrown; blows up where it breaks, taking only Unstable Planks and Unstable Ladders with it. */
    public static final Item SAPPER_FLASK = register("sapper_flask",
            new FlaskItem(properties("sapper_flask").stacksTo(16), ThrownSapperFlask::new));

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

    private static Item block(String name, Block block) {
        return register(name, new BlockItem(block, properties(name).useBlockDescriptionPrefix()));
    }

    private static Item pike(String name, ToolMaterial material, boolean fireproof) {
        Item.Properties properties = properties(name).sword(material, 3.0F, PIKE_ATTACK_SPEED)
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                                3.0F + material.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                                PIKE_ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(UntilDawn.id("pike_reach"),
                                PIKE_REACH, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                        .build());
        return register(name, new Item(fireproof ? properties.fireResistant() : properties));
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
                        output.accept(PRIMITIVE_BARRICADE);
                        output.accept(REINFORCED_BARRICADE);
                        output.accept(SPIKY_BARRICADE);
                        output.accept(CROSSBOW_SENTRY);
                        output.accept(SPIKE_TRAP);
                        output.accept(SCRAP);
                        output.accept(IRON_DAGGER);
                        output.accept(WOODEN_PIKE);
                        output.accept(STONE_PIKE);
                        output.accept(COPPER_PIKE);
                        output.accept(IRON_PIKE);
                        output.accept(DIAMOND_PIKE);
                        output.accept(NETHERITE_PIKE);
                        output.accept(GRAPPLING_GUN);
                        output.accept(REPEATER_CROSSBOW);
                        output.accept(HEAVY_CROSSBOW);
                        output.accept(SCOPED_HEAVY_CROSSBOW);
                        output.accept(SCOPE);
                        output.accept(CHAIN_CROSSBOW);
                        output.accept(NET_SHOT);
                        output.accept(FIRE_FLASK);
                        output.accept(SAPPER_FLASK);
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
