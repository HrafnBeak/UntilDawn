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
import baritone.utils.pathing.MutableMoveResult;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * A neo: a jump around the end of a wall. The wall blocks the straight line from src to dest, so the jump arcs out
 * into the column beside it, past the wall's end, and back in to land behind it, on the same level.
 * <p>
 * Only entity paths plan these ({@link CalculationContext#allowNeo}): the entity's own controller steers the curve.
 * Steering a player through one isn't implemented, so a player's Baritone never plans one.
 */
public class MovementNeo extends Movement {

    private static final BetterBlockPos[] EMPTY = new BetterBlockPos[]{};

    private final Direction direction;
    private final Direction side;
    private final int dist;

    private MovementNeo(IBaritone baritone, BetterBlockPos src, Direction direction, Direction side, int dist) {
        super(baritone, src, src.relative(direction, dist), EMPTY);
        this.direction = direction;
        this.side = side;
        this.dist = dist;
    }

    public static MovementNeo cost(CalculationContext context, BetterBlockPos src, Direction direction) {
        MutableMoveResult res = new MutableMoveResult();
        Direction side = findNeo(context, src.x, src.y, src.z, direction, res);
        int dist = Math.abs(res.x - src.x) + Math.abs(res.z - src.z);
        return new MovementNeo(context.getBaritone(), src, direction, side == null ? direction.getClockWise() : side, dist);
    }

    public static void cost(CalculationContext context, int x, int y, int z, Direction direction, MutableMoveResult res) {
        findNeo(context, x, y, z, direction, res);
    }

    /**
     * Tries a 2 then a 3 block neo around either side, the clockwise side first.
     *
     * @return the side the jump goes around, or null if there is no neo here
     */
    private static Direction findNeo(CalculationContext context, int x, int y, int z, Direction direction, MutableMoveResult res) {
        if (!context.allowNeo) {
            return null;
        }
        if (!context.allowJumpAtBuildLimit && y >= context.world.getMaxY()) {
            return null;
        }
        if (!MovementHelper.fullyPassable(context, x, y + 2, z)) {
            return null;
        }
        BlockState standingOn = context.get(x, y - 1, z);
        if (MovementHelper.isClimbable(standingOn.getBlock()) || standingOn.getBlock() instanceof StairBlock || MovementHelper.isBottomSlab(standingOn)) {
            return null;
        }
        if (!context.get(x, y, z).getFluidState().isEmpty() || !standingOn.getFluidState().isEmpty()) {
            return null; // can't jump out of water
        }
        int dx = direction.getStepX();
        int dz = direction.getStepZ();
        for (int dist = 2; dist <= 3; dist++) {
            if (!wallInTheWay(context, x, y, z, dx, dz, dist)) {
                continue;
            }
            int destX = x + dx * dist;
            int destZ = z + dz * dist;
            if (!MovementHelper.fullyPassable(context, destX, y, destZ)
                    || !MovementHelper.fullyPassable(context, destX, y + 1, destZ)
                    || !MovementHelper.fullyPassable(context, destX, y + 2, destZ)) {
                continue;
            }
            BlockState landingOn = context.get(destX, y - 1, destZ);
            if (landingOn.getBlock() == Blocks.FARMLAND || !MovementHelper.canWalkOn(context, destX, y - 1, destZ, landingOn)) {
                continue;
            }
            for (Direction side : new Direction[]{direction.getClockWise(), direction.getCounterClockWise()}) {
                if (arcIsClear(context, x + side.getStepX(), y, z + side.getStepZ(), dx, dz, dist)) {
                    res.x = destX;
                    res.y = y;
                    res.z = destZ;
                    // the arc is about two blocks longer than the straight line
                    res.cost = SPRINT_ONE_BLOCK_COST * (dist + 2) + context.jumpPenalty;
                    return side;
                }
            }
        }
        return null;
    }

    /**
     * Something blocks the body on the straight line between src and dest; otherwise this is a parkour jump or a walk.
     */
    private static boolean wallInTheWay(CalculationContext context, int x, int y, int z, int dx, int dz, int dist) {
        for (int i = 1; i < dist; i++) {
            if (!MovementHelper.fullyPassable(context, x + dx * i, y, z + dz * i)
                    || !MovementHelper.fullyPassable(context, x + dx * i, y + 1, z + dz * i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The column beside the line, from beside src to beside dest, is open from the feet to above the head.
     */
    private static boolean arcIsClear(CalculationContext context, int x, int y, int z, int dx, int dz, int dist) {
        for (int i = 0; i <= dist; i++) {
            for (int h = 0; h <= 2; h++) {
                if (!MovementHelper.fullyPassable(context, x + dx * i, y + h, z + dz * i)) {
                    return false;
                }
            }
        }
        return true;
    }

    public Direction getSide() {
        return side;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        MutableMoveResult res = new MutableMoveResult();
        cost(context, src.x, src.y, src.z, direction, res);
        if (res.x != dest.x || res.y != dest.y || res.z != dest.z) {
            return COST_INF;
        }
        return res.cost;
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        Set<BetterBlockPos> set = new HashSet<>();
        for (int i = 0; i <= dist; i++) {
            set.add(src.relative(direction, i));
            set.add(src.relative(direction, i).relative(side));
        }
        return set;
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        // only entities plan neos, and they steer themselves (see the class comment)
        return state.setStatus(MovementStatus.UNREACHABLE);
    }
}
