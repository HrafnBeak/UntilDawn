package org.kennyerf.untildawn.pathing;

import org.kennyerf.untildawn.config.UntilDawnConfig;

/**
 * Searches run on the server thread, so the number of nodes all mobs may explore per tick is capped
 * ({@code pathing.nodesPerTick}). A mob that finds the budget spent keeps its old path and retries next tick.
 */
public final class PathBudget {
    private static int used;

    private PathBudget() {
    }

    public static void resetForTick() {
        used = 0;
    }

    /** Nodes the next search may use, or 0 if this tick's budget is spent. */
    static int available() {
        int perTick = UntilDawnConfig.NODES_PER_TICK.get();
        return Math.max(0, Math.min(UntilDawnConfig.NODES_PER_SEARCH.get(), perTick - used));
    }

    static void spend(int nodes) {
        used += nodes;
    }
}
