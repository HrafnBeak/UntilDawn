package org.kennyerf.untildawn.pathing;

/**
 * Movement types, modeled on Baritone's movement set.
 */
public enum MoveType {
    START,
    /** One block sideways on the same level (may break the way or bridge the floor). */
    TRAVERSE,
    /** One block diagonally, never through corners. */
    DIAGONAL,
    /** Jump up one block sideways (may break headroom or place a stair step). */
    ASCEND,
    /** Step down one block sideways: onto a floor, a stair step a Builder puts there, or a ladder it hangs over the edge. */
    DESCEND,
    /** Walk off an edge and drop several blocks, or into water. */
    FALL,
    /** Jump straight up and place a block underneath (Builder only, as a last resort). */
    PILLAR,
    /**
     * One block up a ladder: one already there, or one a Builder hangs on a wall beside it, or on a plank it puts there to
     * hold the ladder.
     */
    CLIMB,
    /** One block down a ladder, a Builder hanging the next rung as it goes; off the last rung, onto the floor. */
    CLIMB_DOWN,
    /** Break the block underneath and drop into the hole. */
    DIG_DOWN,
    /** Sprint-jump over a gap (Sprinters, and Baritone predictions). */
    PARKOUR,
    /** Jump around the end of a wall: out to the side, past it, and back in behind it. Only Baritone plans these. */
    NEO,
    /** One block straight up a wall, clinging to it (spiders). */
    WALL_CLIMB,
    /** One block down a wall, or over an edge onto the wall below it (spiders). */
    WALL_DOWN;

    public boolean isVertical() {
        return this == ASCEND || this == DESCEND || this == FALL || this == PILLAR || this == CLIMB || this == CLIMB_DOWN || this == DIG_DOWN
                || this == PARKOUR || this == NEO || this == WALL_CLIMB || this == WALL_DOWN;
    }

    /** A spider's move along a wall, pressed against it. */
    public boolean isWallClimb() {
        return this == WALL_CLIMB || this == WALL_DOWN;
    }
}
