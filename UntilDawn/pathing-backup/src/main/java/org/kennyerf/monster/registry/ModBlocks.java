package org.kennyerf.monster.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.kennyerf.monster.Monster;
import org.kennyerf.monster.block.CoreBlock;

public final class ModBlocks {
    public static final ResourceKey<Block> CORE_KEY = ResourceKey.create(Registries.BLOCK, Monster.id("core"));

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

    private ModBlocks() {
    }

    public static void init() {
    }
}
