package org.kennyerf.untildawn.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.entity.EntityPathRequest;
import baritone.api.pathing.entity.EntityPathResult;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.pathing.MobPath;
import org.kennyerf.untildawn.pathing.MoveType;
import org.kennyerf.untildawn.pathing.PathGoal;
import org.kennyerf.untildawn.pathing.PathStep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The Baritone side of {@link BaritonePrediction}: builds requests and turns Baritone's answers into
 * {@link MobPath}s and chat lines. Loaded only when Baritone is installed.
 */
final class BaritonePlanner {
    /** Chunks beyond the farther of start and end that the search may wander into. */
    private static final int CHUNK_MARGIN = 8;
    private static final int MAX_CHUNK_RADIUS = 64;
    /** A Sprinter re-plans often, so it gets Baritone's normal (not plan-ahead) time limits. */
    private static final long SPRINTER_PRIMARY_TIMEOUT_MS = 500;
    private static final long SPRINTER_FAILURE_TIMEOUT_MS = 2000;

    /** Lets Baritone search towards the mod's goals; both count costs in ticks the same way. */
    private record GoalAdapter(PathGoal goal) implements Goal {
        @Override
        public boolean isInGoal(int x, int y, int z) {
            return goal.isGoal(x, y, z);
        }

        @Override
        public double heuristic(int x, int y, int z) {
            return goal.heuristic(x, y, z);
        }
    }

    private BaritonePlanner() {
    }

    static CompletableFuture<BaritonePrediction.Prediction> predict(ServerLevel level, BlockPos from, BlockPos to, List<ItemStack> items) {
        int distance = Math.max(Math.abs(to.getX() - from.getX()), Math.abs(to.getZ() - from.getZ()));
        EntityPathRequest request = EntityPathRequest.builder(level, from, new GoalBlock(to), level.getServer())
                .items(items)
                .chunkRadius(Math.min(MAX_CHUNK_RADIUS, distance / 16 + CHUNK_MARGIN))
                .build();
        return BaritoneAPI.getProvider().getEntityPathPlanner().plan(request)
                .thenApply(result -> new BaritonePrediction.Prediction(toMobPath(from, "Baritone's prediction to " + coords(to), result),
                        summarize(from, to, request.items(), result)));
    }

    /** A Sprinter's route: no digging or building, but it may sprint-jump gaps and jump around walls. */
    static CompletableFuture<@Nullable MobPath> planSprinterPath(ServerLevel level, BlockPos from, PathGoal goal) {
        BlockPos to = goal.anchor();
        int distance = Math.max(Math.abs(to.getX() - from.getX()), Math.abs(to.getZ() - from.getZ()));
        EntityPathRequest request = EntityPathRequest.builder(level, from, new GoalAdapter(goal), level.getServer())
                .allowBreak(false)
                .allowPlace(false)
                .allowParkour(true)
                .allowNeo(true)
                .chunkRadius(Math.min(MAX_CHUNK_RADIUS, distance / 16 + CHUNK_MARGIN))
                .timeouts(SPRINTER_PRIMARY_TIMEOUT_MS, SPRINTER_FAILURE_TIMEOUT_MS)
                .build();
        return BaritoneAPI.getProvider().getEntityPathPlanner().plan(request)
                .thenApply(result -> toMobPath(from, goal.describe(), result));
    }

    /** A wave spider's route: no digging or building, but it may climb straight up and down walls. */
    static CompletableFuture<@Nullable MobPath> planSpiderPath(ServerLevel level, BlockPos from, PathGoal goal) {
        BlockPos to = goal.anchor();
        int distance = Math.max(Math.abs(to.getX() - from.getX()), Math.abs(to.getZ() - from.getZ()));
        EntityPathRequest request = EntityPathRequest.builder(level, from, new GoalAdapter(goal), level.getServer())
                .allowBreak(false)
                .allowPlace(false)
                .canSprint(false)
                .allowParkour(false)
                .allowWallClimb(true)
                .chunkRadius(Math.min(MAX_CHUNK_RADIUS, distance / 16 + CHUNK_MARGIN))
                .timeouts(SPRINTER_PRIMARY_TIMEOUT_MS, SPRINTER_FAILURE_TIMEOUT_MS)
                .build();
        return BaritoneAPI.getProvider().getEntityPathPlanner().plan(request)
                .thenApply(result -> toMobPath(from, goal.describe(), result));
    }

    private static @Nullable MobPath toMobPath(BlockPos from, String description, EntityPathResult result) {
        if (!result.hasPath()) {
            return null;
        }
        List<PathStep> steps = new ArrayList<>();
        steps.add(new PathStep(from, MoveType.START, List.of(), List.of()));
        for (EntityPathResult.Step step : result.steps()) {
            List<BlockPos> breaks = step.breaks().stream().map(EntityPathResult.Break::pos).toList();
            steps.add(new PathStep(step.dest(), moveType(step.move()), breaks, step.places()));
        }
        return new MobPath(List.copyOf(steps), result.reachesGoal(), result.cost(), result.nodesConsidered(),
                result.millis() * 1000, description);
    }

    private static MoveType moveType(EntityPathResult.Move move) {
        return switch (move) {
            case TRAVERSE -> MoveType.TRAVERSE;
            case DIAGONAL -> MoveType.DIAGONAL;
            case ASCEND -> MoveType.ASCEND;
            case DESCEND -> MoveType.DESCEND;
            case FALL -> MoveType.FALL;
            case PILLAR -> MoveType.PILLAR;
            case DOWNWARD -> MoveType.DIG_DOWN;
            case PARKOUR -> MoveType.PARKOUR;
            case NEO -> MoveType.NEO;
            case WALL_CLIMB -> MoveType.WALL_CLIMB;
            case WALL_CLIMB_DOWN -> MoveType.WALL_DOWN;
        };
    }

    private static List<Component> summarize(BlockPos from, BlockPos to, List<ItemStack> items, EntityPathResult result) {
        List<Component> lines = new ArrayList<>();
        String route = "Baritone, " + coords(from) + " to " + coords(to) + ": ";
        if (!result.hasPath()) {
            String why = switch (result.type()) {
                case EXCEPTION -> "Baritone hit an error, see the log";
                case CANCELLATION -> "cancelled";
                default -> "can't get anywhere closer with this inventory";
            };
            lines.add(line(route + "no path (" + why + ", " + result.millis() + " ms)", ChatFormatting.RED));
            lines.add(line("Inventory: " + describe(items), ChatFormatting.GRAY));
            return lines;
        }
        lines.add(line(route + (result.reachesGoal() ? "gets there" : "PARTIAL, gets as close as it can"),
                result.reachesGoal() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        lines.add(line(String.format(Locale.ROOT, "%d moves, about %.1f s at a player's speed (%.0f ticks); searched %d nodes in %d ms",
                result.steps().size(), result.cost() / 20.0, result.cost(), result.nodesConsidered(), result.millis()), ChatFormatting.GRAY));

        if (result.breakCount() > 0) {
            // tool name -> {blocks, ticks}
            Map<String, double[]> byTool = new LinkedHashMap<>();
            for (EntityPathResult.Step step : result.steps()) {
                for (EntityPathResult.Break mined : step.breaks()) {
                    double[] total = byTool.computeIfAbsent(toolName(mined.tool()), name -> new double[2]);
                    total[0]++;
                    total[1] += mined.ticks();
                }
            }
            List<String> parts = new ArrayList<>();
            byTool.forEach((tool, total) -> parts.add(String.format(Locale.ROOT, "%d with %s (%.1f s)", (int) total[0], tool, total[1] / 20.0)));
            lines.add(line("Breaks " + result.breakCount() + " blocks: " + String.join(", ", parts), ChatFormatting.GRAY));
        }
        EntityPathResult.InventoryUse inventory = result.inventory();
        if (inventory.blocksPlaced() > 0) {
            lines.add(line(String.format(Locale.ROOT, "Places %d blocks (carries %d, picks up %d on the way)",
                    inventory.blocksPlaced(), inventory.blocksCarried(), inventory.blocksPickedUp()), ChatFormatting.GRAY));
        }
        long bucketFalls = result.steps().stream().filter(EntityPathResult.Step::waterBucket).count();
        if (bucketFalls > 0) {
            lines.add(line("Lands " + bucketFalls + " long fall(s) in water from the bucket", ChatFormatting.GRAY));
        }
        if (inventory.runsOutOfBlocks()) {
            int move = inventory.runsOutOfBlocksAt();
            lines.add(line("Runs out of blocks at move " + (move + 1) + " (" + coords(result.steps().get(move).dest())
                    + "); from there it would need to re-plan", ChatFormatting.GOLD));
        }
        for (EntityPathResult.WornOutTool worn : inventory.wornOutTools()) {
            lines.add(line(toolName(worn.tool()) + " wears out at move " + (worn.step() + 1) + " ("
                    + coords(result.steps().get(worn.step()).dest()) + "); later digging will be slower", ChatFormatting.GOLD));
        }
        lines.add(line("Inventory: " + describe(items), ChatFormatting.GRAY));
        return lines;
    }

    private static String describe(List<ItemStack> items) {
        if (items.isEmpty()) {
            return "nothing";
        }
        List<String> parts = new ArrayList<>();
        for (ItemStack stack : items) {
            parts.add(stack.getCount() > 1 ? stack.getCount() + " " + stack.getHoverName().getString() : stack.getHoverName().getString());
        }
        return String.join(", ", parts);
    }

    private static String toolName(ItemStack tool) {
        return tool.isEmpty() ? "bare hands" : tool.getHoverName().getString();
    }

    private static String coords(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static Component line(String text, ChatFormatting colour) {
        return Component.literal(text).withStyle(colour);
    }
}
