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

package baritone.pathing.movement.movements;

import baritone.api.IBaritone;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.MovementState;
import net.minecraft.core.Direction;

import java.util.Set;

/**
 * A spider's climb: one block straight up or down a wall it clings to, or over the edge it stands on and onto the wall
 * below it. A spider clings to any wall it pushes against, rising while it pushes and sliding down slowly otherwise.
 * <p>
 * Only entity paths plan these ({@link CalculationContext#allowWallClimb}): the entity's own controller presses it
 * against the wall. Players can't climb walls, so a player's Baritone never plans one.
 */
public class MovementWallClimb extends Movement {

    private static final BetterBlockPos[] EMPTY = new BetterBlockPos[]{};
    /** A spider rises 0.2 blocks a tick, and slides down at 0.15. */
    public static final double UP_ONE_COST = 1 / 0.2;
    public static final double DOWN_ONE_COST = 1 / 0.15;

    public MovementWallClimb(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest) {
        super(baritone, src, dest, EMPTY);
    }

    /** One block up the wall beside the feet. */
    public static double costUp(CalculationContext context, int x, int y, int z) {
        if (!context.allowWallClimb || !MovementHelper.fullyPassable(context, x, y + 2, z) || !wallBeside(context, x, y, z)) {
            return COST_INF;
        }
        return UP_ONE_COST;
    }

    /** One block down, with a wall beside the cell below to cling to on the way. */
    public static double costDown(CalculationContext context, int x, int y, int z) {
        if (!context.allowWallClimb || !MovementHelper.fullyPassable(context, x, y - 1, z)
                || MovementHelper.canWalkOn(context, x, y - 2, z) || !wallBeside(context, x, y - 1, z)) {
            // with a floor right below, it's an ordinary drop
            return COST_INF;
        }
        return DOWN_ONE_COST;
    }

    /**
     * Over the edge of the block it stands on and down onto the side of that block; the drop beyond is too far to
     * step down (a floor one below makes it a descend).
     */
    public static double costOverEdge(CalculationContext context, int x, int y, int z, Direction direction) {
        int destX = x + direction.getStepX();
        int destZ = z + direction.getStepZ();
        if (!context.allowWallClimb || !MovementHelper.canWalkOn(context, x, y - 1, z)
                || !MovementHelper.fullyPassable(context, destX, y + 1, destZ)
                || !MovementHelper.fullyPassable(context, destX, y, destZ)
                || !MovementHelper.fullyPassable(context, destX, y - 1, destZ)
                || MovementHelper.canWalkOn(context, destX, y - 2, destZ)) {
            return COST_INF;
        }
        return WALK_OFF_BLOCK_COST + DOWN_ONE_COST;
    }

    /** A block to cling to beside the cell. */
    private static boolean wallBeside(CalculationContext context, int x, int y, int z) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (MovementHelper.canWalkOn(context, x + direction.getStepX(), y, z + direction.getStepZ())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        if (dest.y > src.y) {
            return costUp(context, src.x, src.y, src.z);
        }
        if (dest.x == src.x && dest.z == src.z) {
            return costDown(context, src.x, src.y, src.z);
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (src.x + direction.getStepX() == dest.x && src.z + direction.getStepZ() == dest.z) {
                return costOverEdge(context, src.x, src.y, src.z, direction);
            }
        }
        return COST_INF;
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        return Set.of(src, dest);
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        // only entities plan wall climbs, and they press themselves to the wall (see the class comment)
        return state.setStatus(MovementStatus.UNREACHABLE);
    }
}
