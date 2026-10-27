package org.kennyerf.untildawn.pathing;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * One node of a path: where the mob's feet end up, how it gets there, and which blocks must be broken or
 * placed first. {@code places} lists everything to place in the order it goes in; {@code ladders} says which of those are
 * ladders, and the rest are Unstable Planks (floors, stair steps, and the planks ladders hang on).
 */
public record PathStep(BlockPos pos, MoveType type, List<BlockPos> breaks, List<BlockPos> places, List<BlockPos> ladders) {
    public PathStep(BlockPos pos, MoveType type, List<BlockPos> breaks, List<BlockPos> places) {
        this(pos, type, breaks, places, List.of());
    }

    public boolean hasWork() {
        return !breaks.isEmpty() || !places.isEmpty();
    }

    /** The step stands on a plank it puts down itself: a bridge, or a stair step. */
    public boolean standsOnBuilt() {
        BlockPos floor = pos.below();
        return places.contains(floor) && !ladders.contains(floor);
    }

    /** A built stair step, up or down. */
    public boolean isBuiltStair() {
        return (type == MoveType.ASCEND || type == MoveType.DESCEND) && standsOnBuilt();
    }
}
