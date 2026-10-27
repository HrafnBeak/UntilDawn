package org.kennyerf.untildawn.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.entity.ArcherSkeleton;
import org.kennyerf.untildawn.pathing.PathCosts;
import org.kennyerf.untildawn.registry.ModBlocks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Damage building up on blocks, from Miners digging and from arrows. Measured in ticks of Miner digging
 * ({@link PathCosts#mineTicks}); a block breaks once it has taken that much, and shows cracks on the way.
 * Damage that isn't added to for a while heals.
 */
public final class BlockDamage {
    private static final int HEAL_TICKS = 600;

    private static final class Damage {
        final int crackId;
        BlockState state;
        double amount;
        long lastHit;
        int stage = -1;

        Damage(int crackId, BlockState state) {
            this.crackId = crackId;
            this.state = state;
        }
    }

    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Damage>> DAMAGE = new HashMap<>();
    /** Crack overlays are keyed by "breaker" id; negative ids never clash with entities. */
    private static int nextCrackId = -1;

    private BlockDamage() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BlockDamage::heal);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> DAMAGE.clear());
    }

    /** Whether this block can take damage at all: not air, fluid, unbreakable or the Core. */
    public static boolean canDamage(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.is(ModBlocks.CORE) && state.getDestroySpeed(level, pos) >= 0;
    }

    /** Adds damage to a block. Returns true if that broke it. */
    public static boolean damage(ServerLevel level, BlockPos pos, double amount, @Nullable Entity source) {
        BlockState state = level.getBlockState(pos);
        if (amount <= 0 || !canDamage(level, pos, state)) {
            return false;
        }
        Long2ObjectOpenHashMap<Damage> damage = DAMAGE.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>());
        long key = pos.asLong();
        Damage entry = damage.get(key);
        if (entry == null) {
            entry = new Damage(nextCrackId(), state);
            damage.put(key, entry);
        } else if (entry.state != state) {
            // A different block now; old damage doesn't carry over.
            entry.state = state;
            entry.amount = 0;
        }
        entry.amount += amount;
        entry.lastHit = level.getGameTime();

        int needed = PathCosts.mineTicks(state.getDestroySpeed(level, pos));
        if (entry.amount >= needed) {
            damage.remove(key);
            level.destroyBlockProgress(entry.crackId, pos, -1);
            level.destroyBlock(pos, true, source);
            return true;
        }
        int stage = (int) Math.min(9, entry.amount * 10 / needed);
        if (stage != entry.stage) {
            entry.stage = stage;
            level.destroyBlockProgress(entry.crackId, pos, stage);
        }
        return false;
    }

    /**
     * A Bomber's blast: each block within {@code radius} of the centre takes up to {@code power} ticks of digging damage,
     * most at the centre and none just past the radius, so weak walls fall and strong ones are only cracked.
     */
    public static void blast(ServerLevel level, Vec3 centre, double radius, double power, @Nullable Entity source) {
        if (power <= 0 || radius <= 0) {
            return;
        }
        int reach = (int) Math.ceil(radius);
        BlockPos origin = BlockPos.containing(centre);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    double distance = centre.distanceTo(Vec3.atCenterOf(pos));
                    if (distance <= radius && !level.getBlockState(pos).isAir()) {
                        damage(level, pos.immutable(), power * (1.0 - distance / (radius + 1.0)), source);
                    }
                }
            }
        }
    }

    /**
     * Collateral damage: an Archer's or Longbow Man's arrow chips the block it hits ({@code <mob>.blockDamage}). The
     * arrow is a plain arrow from then on, so if the block breaks and it falls onto another, that one is left alone.
     */
    public static void onArrowHit(AbstractArrow arrow, BlockPos pos) {
        if (!(arrow.level() instanceof ServerLevel level)) {
            return;
        }
        arrow.removeTag(ArcherSkeleton.TRAIL_TAG);
        if (arrow.removeTag(ArcherSkeleton.BLOCK_DAMAGE_TAG) && arrow.getOwner() instanceof ArcherSkeleton archer
                && archer.settings().blockDamage != null) {
            damage(level, pos, archer.settings().blockDamage.get(), archer);
        }
    }

    /** Damage taken so far, in ticks of digging; 0 if none. */
    public static double damageAt(ServerLevel level, BlockPos pos) {
        Long2ObjectOpenHashMap<Damage> damage = DAMAGE.get(level.dimension());
        Damage entry = damage != null ? damage.get(pos.asLong()) : null;
        return entry != null && entry.state == level.getBlockState(pos) ? entry.amount : 0;
    }

    private static int nextCrackId() {
        int id = nextCrackId;
        nextCrackId = nextCrackId == Integer.MIN_VALUE ? -1 : nextCrackId - 1;
        return id;
    }

    private static void heal(MinecraftServer server) {
        if (server.getTickCount() % 100 != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Long2ObjectOpenHashMap<Damage> damage = DAMAGE.get(level.dimension());
            if (damage == null) {
                continue;
            }
            long now = level.getGameTime();
            for (Iterator<Long2ObjectMap.Entry<Damage>> it = damage.long2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
                Long2ObjectMap.Entry<Damage> entry = it.next();
                if (now - entry.getValue().lastHit > HEAL_TICKS) {
                    level.destroyBlockProgress(entry.getValue().crackId, BlockPos.of(entry.getLongKey()), -1);
                    it.remove();
                }
            }
        }
    }
}
