package org.kennyerf.monster.pathing;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * One node of a path: where the mob's feet end up, how it gets there,
 * and which blocks must be broken or placed first (in order).
 */
public record PathStep(BlockPos pos, MoveType type, List<BlockPos> breaks, List<BlockPos> places) {
}
