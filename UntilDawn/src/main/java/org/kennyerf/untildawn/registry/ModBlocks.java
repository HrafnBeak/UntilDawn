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
import org.kennyerf.untildawn.block.CoreBlock;
import org.kennyerf.untildawn.block.DecayingCobwebBlock;
import org.kennyerf.untildawn.workbench.WorkbenchBlock;

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
                    .sound(SoundType.LODESTONE)
                    .pushReaction(PushReaction.IMMOVEABLE)
    ));

    // What Builders build with: looks like oak planks, breaks four times as fast, and drops nothing.
    public static final Block UNSTABLE_PLANKS = Registry.register(BuiltInRegistries.BLOCK, UNSTABLE_PLANKS_KEY, new Block(
            BlockBehaviour.Properties.of()
                    .setId(UNSTABLE_PLANKS_KEY)
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(0.5F, 0.5F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava()
                    .noLootTable()
    ));

    public static final ResourceKey<Block> UNSTABLE_LADDER_KEY = ResourceKey.create(Registries.BLOCK, UntilDawn.id("unstable_ladder"));

    // The ladders Builders hang: a ladder in every way, except that it breaks four times as fast and drops nothing.
    public static final Block UNSTABLE_LADDER = Registry.register(BuiltInRegistries.BLOCK, UNSTABLE_LADDER_KEY, new LadderBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.LADDER)
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

    private ModBlocks() {
    }

    public static void init() {
    }
}
