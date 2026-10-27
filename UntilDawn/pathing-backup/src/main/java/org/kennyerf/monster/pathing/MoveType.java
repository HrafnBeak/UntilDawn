package org.kennyerf.monster.pathing;

/**
 * Movement types, modeled on Baritone's movement set.
 */
public enum MoveType {
    START,
    /** One block sideways on the same level (may break the way or bridge the floor). */
    TRAVERSE,
    /** One block diagonally, never through corners. */
    DIAGONAL,
    /** Jump up one block sideways (may break headroom or place a step). */
    ASCEND,
    /** Step down one block sideways. */
    DESCEND,
    /** Walk off an edge and drop several blocks, or into water. */
    FALL,
    /** Jump straight up and place a block underneath. */
    PILLAR,
    /** Break the block underneath and drop into the hole. */
    DIG_DOWN
}
