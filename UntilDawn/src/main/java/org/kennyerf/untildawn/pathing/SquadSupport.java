package org.kennyerf.untildawn.pathing;

/**
 * What a mob's squad can do for it while it plans: squadmates who can dig, blast or build on request,
 * work they have already claimed or been asked for, and cells they are standing in or about to cross.
 */
public interface SquadSupport {
    SquadSupport NONE = new SquadSupport() {
    };

    /** A squadmate Miner could dig for this mob. */
    default boolean canAssistDig() {
        return false;
    }

    /** A squadmate Bomber could blast a wall for this mob. */
    default boolean canAssistBlast() {
        return false;
    }

    /** A squadmate Builder could place blocks for this mob. */
    default boolean canAssistBuild() {
        return false;
    }

    /** Squadmates who can't build and climb only what is built for them: a Builder's way up should be one they can use. */
    default boolean hasFollowers() {
        return false;
    }

    /** A squadmate is already digging this block, has been asked to, or will on its way. */
    default boolean isBreakPending(long pos) {
        return false;
    }

    /** A squadmate is already building in this cell, has been asked to, or will on its way. */
    default boolean isPlacePending(long pos) {
        return false;
    }

    default boolean isCrowded(long pos) {
        return false;
    }
}
