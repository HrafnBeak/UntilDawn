package org.kennyerf.untildawn.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.kennyerf.untildawn.UntilDawn;
import net.minecraft.world.level.block.Blocks;
import org.kennyerf.untildawn.block.BarricadeBlock;
import org.kennyerf.untildawn.block.CoreBlock;
import org.kennyerf.untildawn.block.CrossbowSentryBlock;
import org.kennyerf.untildawn.block.DecayingCobwebBlock;
import org.kennyerf.untildawn.block.LowFireBlock;
import org.kennyerf.untildawn.block.SpikeTrapBlock;
import org.kennyerf.untildawn.block.SpikyBarricadeBlock;
import org.kennyerf.untildawn.block.UnstableLadderBlock;
import org.kennyerf.untildawn.block.UnstablePlanksBlock;
import org.kennyerf.untildawn.workbench.WorkbenchBlock;

import java.util.function.BiFunction;

public final class ModBlocks {
    public static final ResourceKey<Block> CORE_KEY = ResourceKey.create(Registries.BLOCK, UntilDawn.id("core"));
    public static final ResourceKey<Block> UNSTABLE_PLANKS_KEY = ResourceKey.create(Registries.BLOCK, UntilDawn.id("unstable_planks"));

    // Breakable by players (ending the game), but immune to explosions and never mined by Miners.
    public static final Block CORE = Registry.register(BuiltInRegistries.BLOCK, CORE_KEY, new CoreBlock(
            BlockBehaviour.Properties.of()
                    .setId(CORE_KEY)
                    .mapColor(MapColor.COLOR_PURPLE)
                    .requiresCorrectToolForDrops()
                    .strength(25.0F, 3_600_000.0F)
                    .lightLevel(state -> 15)
                    // The model is an open frame, so neighbouring faces must still be drawn.
                    .noOcclusion()
                    .sound(SoundType.LODESTONE)
                    .pushReaction(PushReaction.IMMOVEABLE)
    ));

    // What Builders build with: looks like oak planks, breaks four times as fast, drops nothing, and crumbles away after the night.
    public static final Block UNSTABLE_PLANKS = Registry.register(BuiltInRegistries.BLOCK, UNSTABLE_PLANKS_KEY, new UnstablePlanksBlock(
            BlockBehaviour.Properties.of()
                    .randomTicks()
                    .setId(UNSTABLE_PLANKS_KEY)
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(0.5F, 0.5F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava()
                    .noLootTable()
    ));

    public static final ResourceKey<Block> UNSTABLE_LADDER_KEY = ResourceKey.create(Registries.BLOCK, UntilDawn.id("unstable_ladder"));

    // The ladders Builders hang: a ladder in every way, except that it breaks four times as fast, drops nothing, and crumbles away after the night.
    public static final Block UNSTABLE_LADDER = Registry.register(BuiltInRegistries.BLOCK, UNSTABLE_LADDER_KEY, new UnstableLadderBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.LADDER)
                    .randomTicks()
                    .setId(UNSTABLE_LADDER_KEY)
                    .strength(0.1F)
                    .noLootTable()
    ));

    public static final ResourceKey<Block> DECAYING_COBWEB_KEY = ResourceKey.create(Registries.BLOCK, UntilDawn.id("decaying_cobweb"));

    // Left by a Webber's shot: a cobweb in every way, except that it vanishes after a few seconds and drops nothing.
    public static final Block DECAYING_COBWEB = Registry.register(BuiltInRegistries.BLOCK, DECAYING_COBWEB_KEY, new DecayingCobwebBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.COBWEB)
                    .setId(DECAYING_COBWEB_KEY)
                    .noLootTable()
    ));

    public static final ResourceKey<Block> LOW_FIRE_KEY = ResourceKey.create(Registries.BLOCK, UntilDawn.id("low_fire"));

    // Left by a Fire Flask: fire in every way, except that it never spreads and goes out after a minute or three.
    public static final Block LOW_FIRE = Registry.register(BuiltInRegistries.BLOCK, LOW_FIRE_KEY, new LowFireBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.FIRE)
                    .setId(LOW_FIRE_KEY)
                    .noLootTable()
    ));

    public static final ResourceKey<Block> WORKBENCH_KEY = ResourceKey.create(Registries.BLOCK, UntilDawn.id("workbench"));

    // Researches and crafts the tech tree with Scrap; see WorkbenchBlock.
    public static final Block WORKBENCH = Registry.register(BuiltInRegistries.BLOCK, WORKBENCH_KEY, new WorkbenchBlock(
            BlockBehaviour.Properties.of()
                    .setId(WORKBENCH_KEY)
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava()
    ));

    // The Fortification tree. Walls are three blocks tall; the block's strength is the hardness from the front, and the
    // second number is the hardness from behind. Spiky Barricades are a weaker wall with a trick.
    public static final Block PRIMITIVE_BARRICADE = wall("primitive_barricade", 15.0F, 5.0F, SoundType.WOOD, MapColor.WOOD, BarricadeBlock::new);
    public static final Block REINFORCED_BARRICADE = wall("reinforced_barricade", 25.0F, 5.0F, SoundType.METAL, MapColor.METAL, BarricadeBlock::new);
    public static final Block SPIKY_BARRICADE = wall("spiky_barricade", 20.0F, 5.0F, SoundType.WOOD, MapColor.WOOD, SpikyBarricadeBlock::new);

    public static final ResourceKey<Block> CROSSBOW_SENTRY_KEY = ResourceKey.create(Registries.BLOCK, UntilDawn.id("crossbow_sentry"));

    // A solid turret block rather than a wall; it goes on any surface.
    public static final Block CROSSBOW_SENTRY = Registry.register(BuiltInRegistries.BLOCK, CROSSBOW_SENTRY_KEY, new CrossbowSentryBlock(
            BlockBehaviour.Properties.of()
                    .setId(CROSSBOW_SENTRY_KEY)
                    .mapColor(MapColor.STONE)
                    .strength(5.0F)
                    .sound(SoundType.STONE)
    ));

    public static final ResourceKey<Block> SPIKE_TRAP_KEY = ResourceKey.create(Registries.BLOCK, UntilDawn.id("spike_trap"));

    public static final Block SPIKE_TRAP = Registry.register(BuiltInRegistries.BLOCK, SPIKE_TRAP_KEY, new SpikeTrapBlock(
            BlockBehaviour.Properties.of()
                    .setId(SPIKE_TRAP_KEY)
                    .mapColor(MapColor.METAL)
                    .strength(5.0F)
                    .sound(SoundType.METAL)
                    .noCollision()
                    .noOcclusion()
                    .pushReaction(PushReaction.POPPED)
    ));

    private ModBlocks() {
    }

    private static Block wall(String name, float frontHardness, float backHardness, SoundType sound, MapColor color,
            BiFunction<BlockBehaviour.Properties, Float, Block> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, UntilDawn.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(
                BlockBehaviour.Properties.of().setId(key).mapColor(color).strength(frontHardness).sound(sound).noOcclusion(), backHardness));
    }

    public static void init() {
    }
}
