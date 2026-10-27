package org.kennyerf.monster.pathing;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.kennyerf.monster.config.MonsterConfig;
import org.kennyerf.monster.team.WaveTeam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Server-side A* for mobs, modeled on Baritone's pathfinder: nodes are feet positions, edges are
 * {@link MoveType movements} whose costs include the time to break and place blocks. A mob that can dig plans
 * straight through walls when that is cheaper than walking around; a mob that can build plans bridges,
 * staircases and pillars. Blocks a teammate has claimed count as "about to be opened/placed", so mobs
 * plan to follow specialists instead of duplicating their work.
 */
public final class MobPathfinder {
    /** Like Baritone, trade optimality for reach: a weighted heuristic explores far fewer nodes. */
    private static final double HEURISTIC_WEIGHT = 2.0;
    private static final int[][] HORIZONTALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONALS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private static final class Node {
        final int x, y, z;
        double g;
        final double h;
        @Nullable Node parent;
        PathStep step;
        boolean closed;

        Node(int x, int y, int z, double h) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.h = h;
        }

        double f() {
            return g + h * HEURISTIC_WEIGHT;
        }
    }

    private record Entry(Node node, double f) {
    }

    private final ServerLevel level;
    private final Mob mob;
    private final PathAbilities abilities;
    private final BlockView view;

    private MobPathfinder(ServerLevel level, Mob mob, PathAbilities abilities) {
        this.level = level;
        this.mob = mob;
        this.abilities = abilities;
        this.view = new BlockView(level);
    }

    /**
     * Searches from the mob's feet towards the goal. Returns null when the tick's node budget is spent
     * or no progress at all is possible.
     */
    public static @Nullable MobPath find(ServerLevel level, Mob mob, PathAbilities abilities, BlockPos start, PathGoal goal) {
        int budget = PathBudget.available();
        if (budget <= 0) {
            return null;
        }
        return new MobPathfinder(level, mob, abilities).search(start, goal, budget, System.nanoTime());
    }

    private @Nullable MobPath search(BlockPos start, PathGoal goal, int maxNodes, long began) {
        Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
        PriorityQueue<Entry> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));

        Node startNode = new Node(start.getX(), start.getY(), start.getZ(), goal.heuristic(start.getX(), start.getY(), start.getZ()));
        startNode.step = new PathStep(start, MoveType.START, List.of(), List.of());
        nodes.put(start.asLong(), startNode);
        open.add(new Entry(startNode, startNode.f()));

        Node best = startNode;
        int explored = 0;
        long timeLimitNanos = MonsterConfig.SEARCH_TIME_LIMIT.get() * 1_000_000L;
        boolean reached = false;
        List<Candidate> candidates = new ArrayList<>();

        while (!open.isEmpty() && explored < maxNodes) {
            Entry entry = open.poll();
            Node node = entry.node;
            if (node.closed || entry.f != node.f()) {
                continue;
            }
            node.closed = true;
            explored++;
            if (goal.isGoal(node.x, node.y, node.z)) {
                best = node;
                reached = true;
                break;
            }
            if (node.h < best.h) {
                best = node;
            }
            if ((explored & 127) == 0 && System.nanoTime() - began > timeLimitNanos) {
                break;
            }

            candidates.clear();
            expand(node, candidates);
            for (Candidate c : candidates) {
                double g = node.g + c.cost;
                long key = BlockPos.asLong(c.x, c.y, c.z);
                Node next = nodes.get(key);
                if (next == null) {
                    next = new Node(c.x, c.y, c.z, goal.heuristic(c.x, c.y, c.z));
                    nodes.put(key, next);
                } else if (next.closed || g >= next.g - 0.01) {
                    continue;
                }
                next.g = g;
                next.parent = node;
                next.step = new PathStep(new BlockPos(c.x, c.y, c.z), c.type, c.breaks, c.places);
                open.add(new Entry(next, next.f()));
            }
        }
        PathBudget.spend(explored);

        if (best == startNode) {
            return null;
        }
        List<PathStep> steps = new ArrayList<>();
        for (Node n = best; n != null; n = n.parent) {
            steps.add(n.step);
        }
        Collections.reverse(steps);
        return new MobPath(List.copyOf(steps), reached, best.g, explored, (System.nanoTime() - began) / 1000, goal.describe());
    }

    // ---------------------------------------------------------------- movements

    private record Candidate(int x, int y, int z, MoveType type, double cost, List<BlockPos> breaks, List<BlockPos> places) {
    }

    private void expand(Node n, List<Candidate> out) {
        int x = n.x, y = n.y, z = n.z;
        for (int[] d : HORIZONTALS) {
            int nx = x + d[0], nz = z + d[1];
            traverse(x, y, z, nx, nz, out);
            ascend(x, y, z, nx, nz, out);
            descendOrFall(x, y, z, nx, nz, out);
        }
        for (int[] d : DIAGONALS) {
            diagonal(x, y, z, x + d[0], z + d[1], out);
        }
        pillar(x, y, z, out);
        digDown(x, y, z, out);
    }

    private void traverse(int x, int y, int z, int nx, int nz, List<Candidate> out) {
        List<BlockPos> breaks = new ArrayList<>(2);
        List<BlockPos> places = new ArrayList<>(1);
        double cost = PathCosts.WALK;
        cost += clearCost(nx, y + 1, nz, breaks);
        cost += clearCost(nx, y, nz, breaks);
        if (cost >= PathCosts.INF) {
            return;
        }
        cost += floorCost(nx, y - 1, nz, places);
        if (cost >= PathCosts.INF) {
            return;
        }
        if (view.isWater(nx, y, nz)) {
            cost += PathCosts.WATER_EXTRA;
        }
        out.add(new Candidate(nx, y, nz, MoveType.TRAVERSE, cost, breaks, places));
    }

    private void ascend(int x, int y, int z, int nx, int nz, List<Candidate> out) {
        List<BlockPos> breaks = new ArrayList<>(3);
        List<BlockPos> places = new ArrayList<>(1);
        double cost = PathCosts.WALK + PathCosts.JUMP;
        cost += clearCost(x, y + 2, z, breaks);
        cost += clearCost(nx, y + 2, nz, breaks);
        cost += clearCost(nx, y + 1, nz, breaks);
        if (cost >= PathCosts.INF) {
            return;
        }
        // The block we step onto: solid already, or a step the Builder places.
        if (!view.canStandOn(nx, y, nz)) {
            if (!view.isPassable(nx, y, nz) && !view.isReplaceable(nx, y, nz)) {
                return;
            }
            cost += floorCost(nx, y, nz, places);
            if (cost >= PathCosts.INF) {
                return;
            }
        }
        out.add(new Candidate(nx, y + 1, nz, MoveType.ASCEND, cost, breaks, places));
    }

    private void descendOrFall(int x, int y, int z, int nx, int nz, List<Candidate> out) {
        List<BlockPos> breaks = new ArrayList<>(3);
        double cost = PathCosts.WALK + PathCosts.fall(1);
        cost += clearCost(nx, y + 1, nz, breaks);
        cost += clearCost(nx, y, nz, breaks);
        cost += clearCost(nx, y - 1, nz, breaks);
        if (cost >= PathCosts.INF) {
            return;
        }
        if (view.canStandOn(nx, y - 2, nz)) {
            out.add(new Candidate(nx, y - 1, nz, MoveType.DESCEND, cost, breaks, List.of()));
            return;
        }
        if (!breaks.isEmpty()) {
            return;
        }
        // Open drop: find where the mob lands.
        for (int drop = 2; drop <= 24; drop++) {
            int fy = y - drop;
            if (!view.isPassable(nx, fy, nz)) {
                return;
            }
            if (view.isWater(nx, fy, nz)) {
                out.add(new Candidate(nx, fy, nz, MoveType.FALL, PathCosts.WALK + PathCosts.fall(drop) + PathCosts.WATER_EXTRA, List.of(), List.of()));
                return;
            }
            if (view.canStandOn(nx, fy - 1, nz)) {
                if (drop <= MonsterConfig.MAX_FALL.get()) {
                    out.add(new Candidate(nx, fy, nz, MoveType.FALL, PathCosts.WALK + PathCosts.fall(drop), List.of(), List.of()));
                }
                return;
            }
        }
    }

    private void diagonal(int x, int y, int z, int nx, int nz, List<Candidate> out) {
        // Never cut corners: both side cells must be open too. Diagonals don't break or place.
        if (!view.isPassable(nx, y, nz) || !view.isPassable(nx, y + 1, nz)
                || !view.isPassable(nx, y, z) || !view.isPassable(nx, y + 1, z)
                || !view.isPassable(x, y, nz) || !view.isPassable(x, y + 1, nz)
                || !view.canStandOn(nx, y - 1, nz)) {
            return;
        }
        double cost = PathCosts.DIAGONAL + (view.isWater(nx, y, nz) ? PathCosts.WATER_EXTRA : 0.0);
        out.add(new Candidate(nx, y, nz, MoveType.DIAGONAL, cost, List.of(), List.of()));
    }

    private void pillar(int x, int y, int z, List<Candidate> out) {
        if (!abilities.canPlace() || !view.isReplaceable(x, y, z) || view.isWater(x, y, z)) {
            return;
        }
        List<BlockPos> breaks = new ArrayList<>(1);
        double cost = PathCosts.PILLAR + clearCost(x, y + 2, z, breaks);
        if (cost >= PathCosts.INF) {
            return;
        }
        out.add(new Candidate(x, y + 1, z, MoveType.PILLAR, cost, breaks, List.of(new BlockPos(x, y, z))));
    }

    private void digDown(int x, int y, int z, List<Candidate> out) {
        if (!abilities.canBreak() || view.isPassable(x, y - 1, z) || !view.canStandOn(x, y - 2, z)) {
            return;
        }
        List<BlockPos> breaks = new ArrayList<>(1);
        double cost = PathCosts.fall(1) + clearCost(x, y - 1, z, breaks);
        if (cost >= PathCosts.INF) {
            return;
        }
        out.add(new Candidate(x, y - 1, z, MoveType.DIG_DOWN, cost, breaks, List.of()));
    }

    // ---------------------------------------------------------------- costs

    /** Cost of making a body cell open, recording the block to break if one is needed. */
    private double clearCost(int x, int y, int z, List<BlockPos> breaks) {
        if (view.isPassable(x, y, z)) {
            return 0.0;
        }
        var state = view.get(x, y, z);
        if (state == null || BlockView.isHarmful(state)) {
            return PathCosts.INF;
        }
        if (WaveTeam.isBreakClaimedByOther(level, BlockPos.asLong(x, y, z), mob)) {
            breaks.add(new BlockPos(x, y, z));
            return PathCosts.TEAM_WAIT;
        }
        if (!abilities.canBreak()) {
            return PathCosts.INF;
        }
        double mine = view.mineCost(x, y, z);
        if (mine >= PathCosts.INF) {
            return PathCosts.INF;
        }
        boolean firstInMove = breaks.isEmpty();
        breaks.add(new BlockPos(x, y, z));
        // One explosion clears the whole opening.
        return abilities.explosive() ? (firstInMove ? PathCosts.BLAST : 2.0) : mine;
    }

    /** Cost of having something to stand on, recording the block to place if one is needed. */
    private double floorCost(int x, int y, int z, List<BlockPos> places) {
        if (view.canStandOn(x, y, z)) {
            return 0.0;
        }
        if (!view.isReplaceable(x, y, z)) {
            return PathCosts.INF;
        }
        if (WaveTeam.isPlaceClaimedByOther(level, BlockPos.asLong(x, y, z), mob)) {
            places.add(new BlockPos(x, y, z));
            return PathCosts.TEAM_WAIT;
        }
        if (!abilities.canPlace()) {
            return PathCosts.INF;
        }
        places.add(new BlockPos(x, y, z));
        return PathCosts.PLACE;
    }
}
