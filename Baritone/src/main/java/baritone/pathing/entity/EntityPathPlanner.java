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

package baritone.pathing.entity;

import baritone.Baritone;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.entity.EntityPathRequest;
import baritone.api.pathing.entity.EntityPathResult;
import baritone.api.pathing.entity.EntityPathResult.Break;
import baritone.api.pathing.entity.EntityPathResult.InventoryUse;
import baritone.api.pathing.entity.EntityPathResult.Move;
import baritone.api.pathing.entity.EntityPathResult.Step;
import baritone.api.pathing.entity.EntityPathResult.WornOutTool;
import baritone.api.pathing.entity.IEntityPathPlanner;
import baritone.api.pathing.movement.IMovement;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.PathCalculationResult;
import baritone.pathing.calc.AStarPathFinder;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.movements.*;
import baritone.utils.BlockStateInterface;
import baritone.utils.ToolSet;
import baritone.utils.pathing.Favoring;
import baritone.utils.pathing.MutableMoveResult;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static baritone.api.pathing.movement.ActionCosts.COST_INF;

/**
 * Plans for entities other than the player with the player's pathfinder: the same A* and movements, with a
 * {@link CalculationContext} built from the request's inventory instead of the player's hotbar.
 *
 * @see IEntityPathPlanner
 */
public enum EntityPathPlanner implements IEntityPathPlanner {
    INSTANCE;

    private record Search(PathCalculationResult result, long millis) {
    }

    @Override
    public CompletableFuture<EntityPathResult> plan(EntityPathRequest request) {
        // Only this part touches the level's chunk map, so it runs here on the level's thread; the search just reads
        // the chunks it was handed, like the player's searches read the client's chunks.
        CalculationContext context = createContext(request);
        return CompletableFuture
                .supplyAsync(() -> search(request, context), Baritone.getExecutor())
                .thenApplyAsync(search -> summarize(request, context, search), request.mainThread());
    }

    private static CalculationContext createContext(EntityPathRequest request) {
        Level level = request.level();
        Long2ObjectOpenHashMap<LevelChunk> chunks = new Long2ObjectOpenHashMap<>();
        int centerX = request.start().getX() >> 4;
        int centerZ = request.start().getZ() >> 4;
        int radius = request.chunkRadius();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk != null) {
                    chunks.put(ChunkPos.pack(x, z), chunk);
                }
            }
        }
        List<ItemStack> items = request.items();
        CalculationContext context = new CalculationContext(null, true, level, null, new BlockStateInterface(level, chunks), new ToolSet(items),
                request.allowPlace() && items.stream().anyMatch(EntityPathPlanner::isThrowaway),
                items.stream().anyMatch(stack -> stack.is(Items.WATER_BUCKET)),
                request.canSprint(),
                items.stream().filter(stack -> stack.has(DataComponents.EQUIPPABLE)).toList()) {
            @Override
            public double breakCostMultiplierAt(int x, int y, int z, BlockState current) {
                return request.allowBreak() ? super.breakCostMultiplierAt(x, y, z, current) : COST_INF;
            }
        };
        context.allowParkour = request.allowParkour();
        context.allowNeo = request.allowNeo();
        context.allowWallClimb = request.allowWallClimb();
        return context;
    }

    private static Search search(EntityPathRequest request, CalculationContext context) {
        long began = System.currentTimeMillis();
        BetterBlockPos start = new BetterBlockPos(request.start());
        AStarPathFinder finder = new AStarPathFinder(start, start.x, start.y, start.z, request.goal(), new Favoring(null, context), context);
        PathCalculationResult result = finder.calculate(request.primaryTimeoutMs(), request.failureTimeoutMs());
        return new Search(result, System.currentTimeMillis() - began);
    }

    private static EntityPathResult summarize(EntityPathRequest request, CalculationContext context, Search search) {
        IPath path = search.result.getPath().orElse(null);
        if (path == null) {
            return EntityPathResult.failed(search.result.getType(), search.millis);
        }
        List<ItemStack> items = request.items();
        List<Step> steps = new ArrayList<>();
        double cost = 0;
        for (IMovement iMovement : path.movements()) {
            Movement movement = (Movement) iMovement;
            List<Break> breaks = new ArrayList<>();
            for (BlockPos pos : movement.toBreak(context.bsi)) {
                BlockState state = context.get(pos);
                // the same tool the cost was calculated with (see ToolSet#getBestDestructionTime)
                ItemStack tool = items.isEmpty() ? ItemStack.EMPTY : items.get(context.toolSet.getBestSlot(state.getBlock(), false, true));
                double strength = context.toolSet.getStrVsBlock(state);
                breaks.add(new Break(pos.immutable(), state, tool, strength > 0 ? 1 / strength : Double.POSITIVE_INFINITY));
            }
            List<BlockPos> places = movement.toPlace(context.bsi).stream().map(BlockPos::immutable).toList();
            steps.add(new Step(movement.getSrc(), movement.getDest(), move(movement), movement.getCost(), breaks, places,
                    needsWaterBucket(context, movement)));
            cost += movement.getCost();
        }
        return new EntityPathResult(search.result.getType(), steps, cost, path.getNumNodesConsidered(), search.millis,
                inventoryUse(request, steps));
    }

    private static Move move(Movement movement) {
        if (movement instanceof MovementTraverse) {
            return Move.TRAVERSE;
        }
        if (movement instanceof MovementDiagonal) {
            return Move.DIAGONAL;
        }
        if (movement instanceof MovementAscend) {
            return Move.ASCEND;
        }
        if (movement instanceof MovementDescend) {
            return Move.DESCEND;
        }
        if (movement instanceof MovementFall) {
            return Move.FALL;
        }
        if (movement instanceof MovementPillar) {
            return Move.PILLAR;
        }
        if (movement instanceof MovementDownward) {
            return Move.DOWNWARD;
        }
        if (movement instanceof MovementParkour) {
            return Move.PARKOUR;
        }
        if (movement instanceof MovementNeo) {
            return Move.NEO;
        }
        if (movement instanceof MovementWallClimb) {
            return movement.getDest().y > movement.getSrc().y ? Move.WALL_CLIMB : Move.WALL_CLIMB_DOWN;
        }
        throw new IllegalStateException("Unknown movement " + movement);
    }

    /**
     * The same check {@link MovementFall} makes before it falls.
     */
    private static boolean needsWaterBucket(CalculationContext context, Movement movement) {
        if (!(movement instanceof MovementFall)) {
            return false;
        }
        BetterBlockPos src = movement.getSrc();
        BetterBlockPos dest = movement.getDest();
        if (MovementHelper.isWater(context.get(dest))) {
            return false;
        }
        return MovementDescend.dynamicFallCost(context, src.x, src.y, src.z, dest.x, dest.z, 0, context.get(dest.x, src.y - 2, dest.z), new MutableMoveResult());
    }

    /**
     * Runs the inventory along the path. On the level's thread, because working out drops rolls loot tables.
     */
    private static InventoryUse inventoryUse(EntityPathRequest request, List<Step> steps) {
        int carried = request.items().stream().filter(EntityPathPlanner::isThrowaway).mapToInt(ItemStack::getCount).sum();
        int blocks = carried;
        int pickedUp = 0;
        int placed = 0;
        int runsOutAt = -1;
        Map<ItemStack, Integer> usesLeft = new IdentityHashMap<>();
        List<WornOutTool> wornOut = new ArrayList<>();
        Level level = request.level();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            for (Break mined : step.breaks()) {
                // loot tables don't check the tool for this, breaking does (Block#playerDestroy)
                boolean drops = !mined.state().requiresCorrectToolForDrops() || mined.tool().isCorrectToolForDrops(mined.state());
                if (drops && level instanceof ServerLevel server) {
                    for (ItemStack drop : Block.getDrops(mined.state(), server, mined.pos(), server.getBlockEntity(mined.pos()), null, mined.tool())) {
                        if (isThrowaway(drop)) {
                            pickedUp += drop.getCount();
                            blocks += drop.getCount();
                        }
                    }
                }
                // as Item#mineBlock wears tools down
                ItemStack tool = mined.tool();
                Tool component = tool.get(DataComponents.TOOL);
                if (component != null && component.damagePerBlock() > 0 && tool.isDamageableItem()
                        && mined.state().getDestroySpeed(level, mined.pos()) != 0) {
                    int before = usesLeft.computeIfAbsent(tool, stack -> stack.getMaxDamage() - stack.getDamageValue());
                    int after = before - component.damagePerBlock();
                    usesLeft.put(tool, after);
                    if (before > 0 && after <= 0) {
                        wornOut.add(new WornOutTool(tool, i));
                    }
                }
            }
            for (int p = 0; p < step.places().size(); p++) {
                placed++;
                if (--blocks < 0 && runsOutAt < 0) {
                    runsOutAt = i;
                }
            }
        }
        return new InventoryUse(carried, pickedUp, placed, runsOutAt, List.copyOf(wornOut));
    }

    private static boolean isThrowaway(ItemStack stack) {
        return Baritone.settings().acceptableThrowawayItems.value.contains(stack.getItem());
    }
}
