package org.kennyerf.untildawn.pathing;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * The air that built routes need: the cells a mob occupies walking a stair, bridge, ladder or tower that has been built.
 * Nobody may plan to build in them, so a later stair (another Builder's, or the same Builder's after the target moved)
 * can cross the way it is walked over, but never wall it in, and mobs that follow keep a way up. The planks themselves
 * are not reserved: later routes may walk on them, and put more next to them. Only built steps are reserved: routes that
 * were only planned (and maybe never built, or planned by mobs waiting for a Builder) would soon leave a Builder nowhere
 * to build.
 */
public final class AccessCorridors {
    /** How long a step stays reserved after a mob last walked onto it. */
    private static final long HOLD_TICKS = 6000;
    /** A mob is two blocks tall, and a jump up needs a third of headroom. */
    private static final int HEIGHT = 3;

    private static final Map<ResourceKey<Level>, Long2LongOpenHashMap> RESERVED = new HashMap<>();

    private AccessCorridors() {
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RESERVED.clear());
    }

    /** A mob has arrived on a step that was built: the air its body passed through is kept free. Other steps reserve nothing. */
    public static void reserve(ServerLevel level, PathStep step) {
        if (step.places().isEmpty()) {
            return;
        }
        Long2LongOpenHashMap reserved = RESERVED.computeIfAbsent(level.dimension(), key -> new Long2LongOpenHashMap());
        long until = level.getGameTime() + HOLD_TICKS;
        for (int h = 0; h < HEIGHT; h++) {
            BlockPos cell = step.pos().above(h);
            if (!step.places().contains(cell)) {
                reserved.put(cell.asLong(), until);
            }
        }
    }

    public static boolean isReserved(ServerLevel level, int x, int y, int z) {
        Long2LongOpenHashMap reserved = RESERVED.get(level.dimension());
        if (reserved == null || reserved.isEmpty()) {
            return false;
        }
        long key = BlockPos.asLong(x, y, z);
        long until = reserved.getOrDefault(key, 0L);
        if (until == 0L) {
            return false;
        }
        if (until < level.getGameTime()) {
            reserved.remove(key);
            return false;
        }
        return true;
    }
}
