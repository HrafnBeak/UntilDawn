/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils;

import baritone.Baritone;
import baritone.api.utils.IPlayerContext;
import baritone.cache.CachedRegion;
import baritone.cache.WorldData;
import baritone.utils.accessor.IClientChunkProvider;
import baritone.utils.pathing.BetterWorldBorder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Wraps get for chuck caching capability
 *
 * @author leijurv
 */
public class BlockStateInterface {

    /**
     * Where loaded chunks come from: the client's chunk cache, or a snapshot of a server level taken for an entity's
     * search (see {@link #BlockStateInterface(Level, Long2ObjectMap)}).
     */
    @FunctionalInterface
    private interface ChunkLookup {
        LevelChunk get(int chunkX, int chunkZ);
    }

    private final ChunkLookup chunks;
    private final WorldData worldData;
    protected final Level world;
    public final BlockPos.MutableBlockPos isPassableBlockPos;
    public final BlockGetter access;
    public final BetterWorldBorder worldBorder;

    private LevelChunk prev = null;
    private CachedRegion prevCached = null;

    private final boolean useTheRealWorld;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public BlockStateInterface(IPlayerContext ctx) {
        this(ctx, false);
    }

    public BlockStateInterface(IPlayerContext ctx, boolean copyLoadedChunks) {
        this.world = ctx.world();
        this.worldBorder = new BetterWorldBorder(world.getWorldBorder());
        this.worldData = (WorldData) ctx.worldData();
        ClientChunkCache provider;
        if (copyLoadedChunks) {
            provider = ((IClientChunkProvider) world.getChunkSource()).createThreadSafeCopy();
        } else {
            provider = (ClientChunkCache) world.getChunkSource();
        }
        this.chunks = (chunkX, chunkZ) -> provider.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        this.useTheRealWorld = !Baritone.settings().pathThroughCachedOnly.value;
        if (!ctx.minecraft().isSameThread()) {
            throw new IllegalStateException("BlockStateInterface must be constructed on the main thread");
        }
        this.isPassableBlockPos = new BlockPos.MutableBlockPos();
        this.access = new BlockStateInterfaceAccessWrapper(this);
    }

    /**
     * Reads a fixed set of chunks, for searches that aren't for the local player. Everything outside them counts as
     * unloaded, and there is no Baritone chunk cache to fall back on.
     *
     * @param loaded the level's loaded chunks the search may use, keyed by {@link ChunkPos#pack(int, int)}
     */
    public BlockStateInterface(Level world, Long2ObjectMap<LevelChunk> loaded) {
        this.world = world;
        this.worldBorder = new BetterWorldBorder(world.getWorldBorder());
        this.worldData = null;
        this.chunks = (chunkX, chunkZ) -> loaded.get(ChunkPos.pack(chunkX, chunkZ));
        this.useTheRealWorld = true;
        this.isPassableBlockPos = new BlockPos.MutableBlockPos();
        this.access = new BlockStateInterfaceAccessWrapper(this);
    }

    public boolean worldContainsLoadedChunk(int blockX, int blockZ) {
        return chunks.get(blockX >> 4, blockZ >> 4) != null;
    }

    public static Block getBlock(IPlayerContext ctx, BlockPos pos) { // won't be called from the pathing thread because the pathing thread doesn't make a single blockpos pog
        return get(ctx, pos).getBlock();
    }

    public static BlockState get(IPlayerContext ctx, BlockPos pos) {
        return new BlockStateInterface(ctx).get0(pos.getX(), pos.getY(), pos.getZ()); // immense iq
        // can't just do world().get because that doesn't work for out of bounds
        // and toBreak and stuff fails when the movement is instantiated out of load range but it's not able to BlockStateInterface.get what it's going to walk on
    }

    public BlockState get0(BlockPos pos) {
        return get0(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockState get0(int x, int y, int z) { // Mickey resigned
        y -= world.dimensionType().minY();
        // Invalid vertical position
        if (y < 0 || y >= world.dimensionType().height()) {
            return AIR;
        }

        if (useTheRealWorld) {
            LevelChunk cached = prev;
            // there's great cache locality in block state lookups
            // generally it's within each movement
            // if it's the same chunk as last time
            // we can just skip the mc.world.getChunk lookup
            // which is a Long2ObjectOpenHashMap.get
            // see issue #113
            if (cached != null && cached.getPos().x() == x >> 4 && cached.getPos().z() == z >> 4) {
                return getFromChunk(cached, x, y, z);
            }
            LevelChunk chunk = chunks.get(x >> 4, z >> 4);
            if (chunk != null && !chunk.isEmpty()) {
                prev = chunk;
                return getFromChunk(chunk, x, y, z);
            }
        }
        // same idea here, skip the Long2ObjectOpenHashMap.get if at all possible
        // except here, it's 512x512 tiles instead of 16x16, so even better repetition
        CachedRegion cached = prevCached;
        if (cached == null || cached.getX() != x >> 9 || cached.getZ() != z >> 9) {
            if (worldData == null) {
                return AIR;
            }
            CachedRegion region = worldData.cache.getRegion(x >> 9, z >> 9);
            if (region == null) {
                return AIR;
            }
            prevCached = region;
            cached = region;
        }
        BlockState type = cached.getBlock(x & 511, y + world.dimensionType().minY(), z & 511);
        if (type == null) {
            return AIR;
        }
        return type;
    }

    public boolean isLoaded(int x, int z) {
        LevelChunk prevChunk = prev;
        if (prevChunk != null && prevChunk.getPos().x() == x >> 4 && prevChunk.getPos().z() == z >> 4) {
            return true;
        }
        prevChunk = chunks.get(x >> 4, z >> 4);
        if (prevChunk != null && !prevChunk.isEmpty()) {
            prev = prevChunk;
            return true;
        }
        CachedRegion prevRegion = prevCached;
        if (prevRegion != null && prevRegion.getX() == x >> 9 && prevRegion.getZ() == z >> 9) {
            return prevRegion.isCached(x & 511, z & 511);
        }
        if (worldData == null) {
            return false;
        }
        prevRegion = worldData.cache.getRegion(x >> 9, z >> 9);
        if (prevRegion == null) {
            return false;
        }
        prevCached = prevRegion;
        return prevRegion.isCached(x & 511, z & 511);
    }

    // get the block at x,y,z from this chunk WITHOUT creating a single blockpos object
    public static BlockState getFromChunk(LevelChunk chunk, int x, int y, int z) {
        LevelChunkSection section = chunk.getSections()[y >> 4];
        if (section.hasOnlyAir()) {
            return AIR;
        }
        return section.getBlockState(x & 15, y & 15, z & 15);
    }
}
