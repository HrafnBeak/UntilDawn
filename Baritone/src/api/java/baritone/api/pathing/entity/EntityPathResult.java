/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.api.pathing.entity;

import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.PathCalculationResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A path planned for an entity: what Baritone would do, step by step, and what it would use up doing it.
 *
 * @param type            {@code SUCCESS_TO_GOAL} for a complete path, {@code SUCCESS_SEGMENT} for one that gets as close
 *                        as it could; otherwise there are no steps
 * @param steps           the movements in order; the first starts at the request's start
 * @param cost            estimated ticks to walk it at the player's speed, mining and placing included
 * @param nodesConsidered how many positions the search looked at
 * @param millis          how long the search took
 * @param inventory       what the path takes out of (and puts into) the inventory
 */
public record EntityPathResult(PathCalculationResult.Type type, List<Step> steps, double cost, int nodesConsidered,
                               long millis, InventoryUse inventory) {

    public static EntityPathResult failed(PathCalculationResult.Type type, long millis) {
        return new EntityPathResult(type, List.of(), 0, 0, millis, InventoryUse.NONE);
    }

    public boolean reachesGoal() {
        return type == PathCalculationResult.Type.SUCCESS_TO_GOAL;
    }

    public boolean hasPath() {
        return !steps.isEmpty();
    }

    public int breakCount() {
        return steps.stream().mapToInt(step -> step.breaks().size()).sum();
    }

    public int placeCount() {
        return steps.stream().mapToInt(step -> step.places().size()).sum();
    }

    /**
     * Baritone's movements.
     */
    public enum Move {
        /** One block sideways, maybe through blocks it breaks or over one it places. */
        TRAVERSE,
        /** One block diagonally, maybe up or down one. */
        DIAGONAL,
        /** Jump up one block sideways. */
        ASCEND,
        /** Step down one block sideways. */
        DESCEND,
        /** Walk off an edge and drop, maybe into a water bucket placed on landing. */
        FALL,
        /** Jump straight up and place a block underneath. */
        PILLAR,
        /** Mine the block underneath and drop into the hole. */
        DOWNWARD,
        /** Sprint-jump over a gap. */
        PARKOUR,
        /** Jump around the end of a wall: out to the side, past it, and back in behind it. */
        NEO,
        /** One block straight up a wall, clinging to it like a spider. */
        WALL_CLIMB,
        /** One block down a wall, or over an edge onto the wall below it. */
        WALL_CLIMB_DOWN
    }

    /**
     * One movement.
     *
     * @param breaks      blocks to mine first, in order
     * @param places      blocks to place (from the throwaway blocks carried)
     * @param waterBucket the fall is too high to survive without placing water on landing
     */
    public record Step(BetterBlockPos src, BetterBlockPos dest, Move move, double cost, List<Break> breaks,
                       List<BlockPos> places, boolean waterBucket) {
    }

    /**
     * A block to mine.
     *
     * @param tool  the carried item that mines it fastest ({@link ItemStack#EMPTY} for bare hands)
     * @param ticks how long that takes
     */
    public record Break(BlockPos pos, BlockState state, ItemStack tool, double ticks) {
    }

    /**
     * Running the path's inventory: drops from mined blocks are picked up, placed blocks are used up, tools wear down
     * (Unbreaking is not taken into account).
     *
     * @param blocksCarried     throwaway blocks carried at the start
     * @param blocksPickedUp    throwaway blocks dropped by blocks the path mines
     * @param blocksPlaced      blocks the path places
     * @param runsOutOfBlocksAt the index of the first step that needs a block when none are left, or -1
     * @param wornOutTools      tools that break before the end of the path
     */
    public record InventoryUse(int blocksCarried, int blocksPickedUp, int blocksPlaced, int runsOutOfBlocksAt,
                               List<WornOutTool> wornOutTools) {

        public static final InventoryUse NONE = new InventoryUse(0, 0, 0, -1, List.of());

        public boolean runsOutOfBlocks() {
            return runsOutOfBlocksAt >= 0;
        }
    }

    /**
     * A tool that breaks partway: from step {@code step} on, the path's estimate for it is too optimistic.
     */
    public record WornOutTool(ItemStack tool, int step) {
    }
}
