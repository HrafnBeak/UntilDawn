package org.kennyerf.monster.pathing;

/**
 * Action costs in ticks, in the spirit of Baritone's ActionCosts.
 */
public final class PathCosts {
    public static final double INF = 1_000_000.0;
    public static final double WALK = 4.633;
    public static final double DIAGONAL = WALK * Math.sqrt(2);
    public static final double JUMP = 2.0;
    public static final double FALL_PER_BLOCK = 1.2;
    public static final double WATER_EXTRA = WALK * 1.5;
    /*
     * Work is costed below the real time it takes. Specialists *want* to dig and build: with real mining
     * times a 40-block detour would beat digging through one wall, and the search would flood the whole
     * area before considering it.
     */
    public static final double PLACE = 8.0;
    public static final double PILLAR = PLACE + 4.0;
    /** A Bomber can only blast once, so breaching is worth it only for real walls. */
    public static final double BLAST = 20.0;
    /** Moving through work a teammate has claimed: wait for them rather than duplicating it. */
    public static final double TEAM_WAIT = 6.0;

    private PathCosts() {
    }

    /** Ticks a mob needs to dig a block; also used by the executor so plan and reality agree. */
    public static int mineTicks(float hardness) {
        // Deliberately slow: a wooden pickaxe on stone takes ~4 seconds, obsidian ~100.
        return Math.max(15, (int) (20 + hardness * 40));
    }

    public static double fall(int blocks) {
        return 2.0 + blocks * FALL_PER_BLOCK;
    }

    /** Planning cost of digging: stone ~ 5 blocks of walking, obsidian ~ 110. */
    static double mine(float hardness) {
        return 8.0 + hardness * 10.0;
    }
}
