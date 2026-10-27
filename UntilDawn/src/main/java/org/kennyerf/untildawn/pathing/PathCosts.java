package org.kennyerf.untildawn.pathing;

/**
 * Action costs in ticks, in the spirit of Baritone's ActionCosts.
 */
public final class PathCosts {
    public static final double INF = 1_000_000.0;
    public static final double WALK = 4.633;
    public static final double DIAGONAL = WALK * Math.sqrt(2);
    /** One block at a sprint, as in a parkour jump. */
    public static final double SPRINT = 3.564;
    public static final double JUMP = 2.0;
    public static final double FALL_PER_BLOCK = 1.2;
    public static final double WATER_EXTRA = WALK * 1.5;
    /** One block up or down a ladder. */
    public static final double CLIMB = WALK * 1.3;
    /** A spider climbs 0.2 blocks a tick up a wall, and slides down at 0.15. */
    public static final double WALL_CLIMB = 1 / 0.2;
    public static final double WALL_DOWN = 1 / 0.15;
    /*
     * Work is costed below the real time it takes. Specialists *want* to dig and build: with real mining
     * times a 40-block detour would beat digging through one wall, and the search would flood the whole
     * area before considering it.
     */
    /** A plank to walk on: a bridge over a gap. */
    public static final double PLACE = 8.0;
    /**
     * Extra for a bridge plank with nothing under it either, up in the air. Builders get into place along the ground and
     * then build up, rather than climbing first and bridging across.
     */
    public static final double AIR_BRIDGE = 4.0;
    /**
     * A stair step, up or down. A level of stair costs more than a level of ladder even counting the block it advances, so
     * Builders take stairs only for a level or two where the route goes that way anyway, and climb anything higher on a
     * ladder. Stairs never turn (see MobPathfinder), so there are no zig-zags.
     */
    public static final double STAIR = 14.0;
    /**
     * A level of tower: a plank and the jump to put it under itself. Nobody can follow a tower up and the Builder can't
     * come back down it, so it is a last resort, dearer than a ladder up a pillar of planks, which does the same job.
     */
    public static final double PILLAR = 16.0;
    /**
     * Extra for a level of tower when the Builder has squadmates who can't build. A tower is one block wide: they can't
     * follow it up, and it can't come back down to help them. Stairs and ladders they can climb win instead.
     */
    public static final double PILLAR_ALONE = 12.0;
    /**
     * A ladder hung on a wall to climb. With the climb, a level of ladder costs well under a level of tower or stair, so
     * Builders at a wall climb it on ladders, leaving a way up their squadmates can follow.
     */
    public static final double LADDER = 2.5;
    /**
     * A plank beside a ladder for it to hang on, where there is no wall. Out in the open a Builder climbs a pillar of these
     * with a ladder up its side: still cheaper than a tower, and a way up (and down) anyone can use.
     */
    public static final double LADDER_SUPPORT = 3.0;
    /**
     * The least a level of climbing costs where it has to be built: a ladder and the climb up it. Stairs and towers cost
     * more. The search's estimate counts this for every level the goal is above a mob that builds (or has a Builder to ask),
     * so it heads up instead of searching the whole ground under a high goal first.
     */
    public static final double BUILT_CLIMB = CLIMB + LADDER;
    /** A Bomber can only blast once, so breaching is worth it only for real walls. */
    public static final double BLAST = 20.0;
    /** Moving through work a squadmate is already doing or has asked for: wait for it rather than duplicate it. */
    public static final double TEAM_WAIT = 6.0;
    /** Extra for work this mob can't do itself and must ask a squadmate for, per move. */
    public static final double ASSIST = 12.0;
    /** Extra for crossing the Core area away from the side the squad attacks from, so it goes round instead. */
    public static final double OFF_SIDE = 40.0;
    /** Extra for a cell another mob stands on or is about to walk through, so mobs spread out. */
    public static final double CROWD = 3.0;

    private PathCosts() {
    }

    /** Ticks of digging a block needs; shared by Miners and arrow damage so plan and reality agree. */
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
