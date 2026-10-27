package org.kennyerf.monster.pathing;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;
import org.kennyerf.monster.registry.ModBlocks;

/**
 * Cached, read-only block access for one search. Never loads chunks: unloaded terrain is treated as solid rock
 * that cannot be broken, so searches stay inside what the server already has in memory.
 */
final class BlockView {
    private final ServerLevel level;
    private final Long2ObjectOpenHashMap<BlockState> cache = new Long2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private @Nullable LevelChunk lastChunk;
    private int lastChunkX = Integer.MIN_VALUE;
    private int lastChunkZ = Integer.MIN_VALUE;

    BlockView(ServerLevel level) {
        this.level = level;
    }

    ServerLevel level() {
        return level;
    }

    /** Null when the position is outside the world or in an unloaded chunk. */
    @Nullable BlockState get(int x, int y, int z) {
        if (y < level.getMinY() || y > level.getMaxY()) {
            return null;
        }
        long key = BlockPos.asLong(x, y, z);
        BlockState cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        int cx = x >> 4;
        int cz = z >> 4;
        if (cx != lastChunkX || cz != lastChunkZ) {
            lastChunk = level.getChunkSource().getChunkNow(cx, cz);
            lastChunkX = cx;
            lastChunkZ = cz;
        }
        if (lastChunk == null) {
            return null;
        }
        BlockState state = lastChunk.getBlockState(cursor.set(x, y, z));
        cache.put(key, state);
        return state;
    }

    private VoxelShape shape(BlockState state, int x, int y, int z) {
        return state.getCollisionShape(level, cursor.set(x, y, z));
    }

    /** The mob's body can occupy this cell without breaking anything. */
    boolean isPassable(int x, int y, int z) {
        BlockState state = get(x, y, z);
        return state != null && shape(state, x, y, z).isEmpty() && !isHarmful(state);
    }

    boolean isWater(int x, int y, int z) {
        BlockState state = get(x, y, z);
        return state != null && state.getFluidState().is(FluidTags.WATER);
    }

    /** The mob can stand on top of this block. */
    boolean canStandOn(int x, int y, int z) {
        BlockState state = get(x, y, z);
        if (state == null || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) {
            return false;
        }
        VoxelShape shape = shape(state, x, y, z);
        // Fences and walls are too tall to step onto.
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) <= 1.0;
    }

    boolean isReplaceable(int x, int y, int z) {
        BlockState state = get(x, y, z);
        return state != null && state.canBeReplaced() && !state.getFluidState().is(FluidTags.LAVA);
    }

    /** Ticks to mine this block, or {@link PathCosts#INF} if it must not be broken. */
    double mineCost(int x, int y, int z) {
        BlockState state = get(x, y, z);
        if (state == null || state.isAir() || state.is(ModBlocks.CORE) || !state.getFluidState().isEmpty()) {
            return PathCosts.INF;
        }
        float hardness = state.getDestroySpeed(level, cursor.set(x, y, z));
        if (hardness < 0 || touchesLava(x, y, z)) {
            return PathCosts.INF;
        }
        double cost = PathCosts.mine(hardness);
        BlockState above = get(x, y + 1, z);
        if (above != null && above.getBlock() instanceof FallingBlock) {
            // Sand or gravel will fall into the gap and need digging again.
            cost *= 2.0;
        }
        return cost;
    }

    /** Lava, fire and other things a mob should never walk into. */
    static boolean isHarmful(BlockState state) {
        FluidState fluid = state.getFluidState();
        return fluid.is(FluidTags.LAVA)
                || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.COBWEB);
    }

    private boolean touchesLava(int x, int y, int z) {
        for (Direction direction : Direction.values()) {
            BlockState neighbour = get(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());
            if (neighbour != null && neighbour.getFluidState().is(FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }
}
