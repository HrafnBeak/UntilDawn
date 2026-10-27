package org.kennyerf.untildawn.pathing;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.config.UntilDawnConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Server-side A* for mobs, modeled on Baritone's pathfinder: nodes are feet positions, edges are
 * {@link MoveType movements} whose costs include the time to break and place blocks.
 * <p>
 * Every mob plans its own path, but with its squad in mind: work it can't do itself (digging, blasting,
 * bridging, stairs, ladders) is allowed when a squadmate can do it on request, at an extra cost; work a
 * squadmate has already claimed is cheap, so mobs follow specialists instead of duplicating their work; and
 * cells squadmates occupy cost a little more, so the squad spreads out instead of queueing in one file.
 * <p>
 * What Builders build is a way for the whole squad, so it is built the way a squadmate could follow:
 * <ul>
 *     <li>Up: a ladder, hung on a wall or else up the side of a pillar of planks put there to hold it, or a stair for a
 *     level or two. Stairs go straight on the way they started: a stair never turns, so there are no zig-zags. A tower
 *     (jumping up on blocks put underneath) is a last resort, as nobody can follow one.</li>
 *     <li>Down: where a drop is too far to take, a ladder hung over the edge and climbed down rung by rung, or a straight
 *     stair down.</li>
 *     <li>Along before up: the search's estimate of the climbing left isn't weighted like the rest, and a bridge up in
 *     the air costs extra, so a Builder walks to the foot of a climb and then builds straight up, rather than climbing
 *     early and bridging across.</li>
 * </ul>
 * Blocks a route plans to place stay planned while the search goes on from there: later moves treat a plank a few steps
 * back as solid, and a ladder as one to climb.
 * <p>
 * Spiders climb: straight up any wall beside them and down again, so their paths may run through mid-air cells
 * beside a wall.
 */
public final class MobPathfinder {
    /** Like Baritone, trade optimality for reach: a weighted heuristic explores far fewer nodes. */
    private static final double HEURISTIC_WEIGHT = 2.0;
    private static final int[][] HORIZONTALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONALS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int MAX_DROP_SCAN = 24;
    /** Longest sprint-jump, in blocks from take-off to landing (a gap of three), and the longest that lands a block up. */
    private static final int MAX_PARKOUR = 4;
    private static final int MAX_PARKOUR_ASCEND = 3;
    /** Longest neo, from take-off to landing: a wall at most two blocks thick. */
    private static final int MAX_NEO = 3;
    /** How many steps back along a route the search remembers the blocks it planned to place. */
    private static final int PLAN_MEMORY = 8;
    /** How far back along a run of built floor the search looks for the stair it is part of. */
    private static final int STAIR_MEMORY = 32;

    private static final class Node {
        final int x, y, z;
        double g;
        /** The estimated cost left, and the same estimate as the search weighs it (see {@link #node}). */
        final double h;
        final double weighted;
        @Nullable Node parent;
        PathStep step;
        boolean closed;

        Node(int x, int y, int z, double h, double weighted) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.h = h;
            this.weighted = weighted;
        }

        double f() {
            return g + weighted;
        }
    }

    private record Entry(Node node, double f) {
    }

    private record Candidate(int x, int y, int z, MoveType type, double cost, List<BlockPos> breaks, List<BlockPos> places,
                             List<BlockPos> ladders) {
        Candidate(int x, int y, int z, MoveType type, double cost, List<BlockPos> breaks, List<BlockPos> places) {
            this(x, y, z, type, cost, breaks, places, List.of());
        }
    }

    private final PathAbilities abilities;
    private final SquadSupport squad;
    private final LongSet avoid;
    private final BlockView view;
    private final ServerLevel level;
    private final PathGoal goal;
    /** It can build, or ask a squadmate who can. */
    private final boolean builds;
    /** What a level of climbing costs this mob on top of the goal's estimate of a jump; see {@link PathCosts#BUILT_CLIMB}. */
    private final double climbExtra;
    /** The node being expanded: the route to it is what moves from it build on. */
    private Node current;
    /** The line of the built stair the mob starts on, if it does; see {@link #builtStairAxis}. */
    private int @Nullable [] startStairAxis;

    private MobPathfinder(ServerLevel level, PathAbilities abilities, SquadSupport squad, LongSet avoid, PathGoal goal) {
        this.level = level;
        this.abilities = abilities;
        this.squad = squad;
        this.avoid = avoid;
        this.goal = goal;
        this.view = new BlockView(level);
        this.builds = abilities.canPlace() || squad.canAssistBuild();
        // Spiders climb walls for less than that, and other mobs can only climb what is there already.
        this.climbExtra = builds && !abilities.climbWalls() ? PathCosts.BUILT_CLIMB - PathCosts.JUMP : 0.0;
    }

    /**
     * Searches from {@code start} towards the goal. Returns null when the tick's node budget is spent or no
     * progress at all is possible.
     *
     * @param avoid cells whose work this mob gave up waiting for; it won't plan to need them again
     */
    public static @Nullable MobPath find(ServerLevel level, PathAbilities abilities, SquadSupport squad, LongSet avoid,
                                         BlockPos start, PathGoal goal) {
        int budget = PathBudget.available();
        if (budget <= 0) {
            return null;
        }
        return new MobPathfinder(level, abilities, squad, avoid, goal).search(start, budget, System.nanoTime());
    }

    private @Nullable MobPath search(BlockPos start, int maxNodes, long began) {
        Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
        PriorityQueue<Entry> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));

        Node startNode = node(start.getX(), start.getY(), start.getZ());
        startNode.step = new PathStep(start, MoveType.START, List.of(), List.of());
        startStairAxis = builtStairAxis(start.getX(), start.getY(), start.getZ());
        nodes.put(start.asLong(), startNode);
        open.add(new Entry(startNode, startNode.f()));

        Node best = startNode;
        int explored = 0;
        long timeLimitNanos = UntilDawnConfig.SEARCH_TIME_LIMIT.get() * 1_000_000L;
        boolean reached = false;
        // The first goal cell found where the mob would hang on a ladder: taken if the search finds no floor in the goal.
        Node hanging = null;
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
                if (!hangsOnLadder(node)) {
                    best = node;
                    reached = true;
                    break;
                }
                if (hanging == null) {
                    hanging = node;
                }
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
                long key = BlockPos.asLong(c.x, c.y, c.z);
                if (avoid.contains(key)) {
                    // A cell the mob got stuck at or gave up on: plan another way.
                    continue;
                }
                if (isPlannedPlank(c.x, c.y, c.z) || isPlannedPlank(c.x, c.y + 1, c.z)) {
                    // Into a plank this route puts in a few steps back: solid by the time the mob gets there.
                    continue;
                }
                double g = node.g + c.cost + (squad.isCrowded(key) ? PathCosts.CROWD : 0.0) + goal.extraCost(c.x, c.y, c.z);
                Node next = nodes.get(key);
                if (next == null) {
                    next = node(c.x, c.y, c.z);
                    nodes.put(key, next);
                } else if (next.closed || g >= next.g - 0.01) {
                    continue;
                }
                next.g = g;
                next.parent = node;
                next.step = new PathStep(new BlockPos(c.x, c.y, c.z), c.type, c.breaks, c.places, c.ladders);
                open.add(new Entry(next, next.f()));
            }
        }
        PathBudget.spend(explored);
        if (!reached && hanging != null) {
            best = hanging;
            reached = true;
        }

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

    /**
     * A node, with its estimate. Only the goal's own estimate is weighted: the extra a mob that builds pays for each level
     * of climbing is counted as it is, so a level climbed early looks no better than the climb really is and the search
     * makes its way along first, climbing at the end.
     */
    private Node node(int x, int y, int z) {
        double flat = goal.heuristic(x, y, z);
        double climbing = goal.rise(x, y, z) * climbExtra;
        return new Node(x, y, z, flat + climbing, flat * HEURISTIC_WEIGHT + climbing);
    }

    /**
     * Ending here, the mob would be left hanging on a ladder with nothing under it: in the way of everyone climbing after
     * it, and not standing anywhere. The search goes on for a floor in the goal, and only ends on a ladder if it finds none.
     */
    private boolean hangsOnLadder(Node n) {
        BlockPos pos = new BlockPos(n.x, n.y, n.z);
        boolean ladder = view.isClimbable(n.x, n.y, n.z) || n.step != null && n.step.ladders().contains(pos);
        boolean floor = view.canStandOn(n.x, n.y - 1, n.z) || n.step != null && n.step.places().contains(pos.below())
                && !n.step.ladders().contains(pos.below());
        return ladder && !floor;
    }

    // ---------------------------------------------------------------- the route so far

    /** A plank the route to the current node puts in this cell, a few steps back. */
    private boolean isPlannedPlank(int x, int y, int z) {
        Node n = current;
        for (int i = 0; i < PLAN_MEMORY && n != null; i++, n = n.parent) {
            for (BlockPos spot : n.step.places()) {
                if (spot.getX() == x && spot.getY() == y && spot.getZ() == z) {
                    return !n.step.ladders().contains(spot);
                }
            }
        }
        return false;
    }

    /**
     * A plank the route to the current node puts in this cell, a few steps back, as something to stand on: a bridge, a stair
     * step or a tower block. The planks ladders hang on aren't: the top of one is a single block in mid-air.
     */
    private boolean isPlannedFloor(int x, int y, int z) {
        Node n = current;
        for (int i = 0; i < PLAN_MEMORY && n != null; i++, n = n.parent) {
            BlockPos floor = n.step.pos().below();
            if (floor.getX() == x && floor.getY() == y && floor.getZ() == z && n.step.places().contains(floor)
                    && !n.step.ladders().contains(floor)) {
                return true;
            }
        }
        return false;
    }

    /** A ladder the route to the current node hangs in this cell, a few steps back. */
    private boolean isPlannedLadder(int x, int y, int z) {
        Node n = current;
        for (int i = 0; i < PLAN_MEMORY && n != null; i++, n = n.parent) {
            for (BlockPos spot : n.step.ladders()) {
                if (spot.getX() == x && spot.getY() == y && spot.getZ() == z) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a stair step built from the current node, heading this way, keeps to the stair the mob is on. A stair goes on
     * the way it started: standing on a built stair (or on a bridge or landing off one), a new step must go the same way.
     * Back at the start, on a stair built before this plan, it must keep to that stair's line. Standing on ground (or a
     * ladder) it may start any way.
     */
    private boolean keepsHeading(int dx, int dz) {
        Node n = current;
        for (int i = 0; i < STAIR_MEMORY && n != null; i++, n = n.parent) {
            if (n.parent == null) {
                return startStairAxis == null || startStairAxis[0] == Math.abs(dx) && startStairAxis[1] == Math.abs(dz);
            }
            if (!n.step.standsOnBuilt()) {
                return true;
            }
            if (n.step.isBuiltStair()) {
                return n.x - n.parent.x == dx && n.z - n.parent.z == dz;
            }
        }
        return true;
    }

    /**
     * Standing on a stair a Builder built (a re-plan halfway up one), the line it runs along: {1, 0} along x, {0, 1} along
     * z. A stair's next step down is beside and a block lower, and its next step up beside at the height of the feet.
     */
    private int @Nullable [] builtStairAxis(int x, int y, int z) {
        if (!view.isBuiltPlank(x, y - 1, z)) {
            return null;
        }
        int[] axis = null;
        for (int[] d : HORIZONTALS) {
            int sx = x + d[0], sz = z + d[1];
            if (view.isBuiltPlank(sx, y - 2, sz) || view.isBuiltPlank(sx, y, sz)) {
                int[] line = {Math.abs(d[0]), Math.abs(d[1])};
                if (axis != null && axis[0] != line[0]) {
                    // Steps on both lines: where two stairs meet, either way goes.
                    return null;
                }
                axis = line;
            }
        }
        return axis;
    }

    // ---------------------------------------------------------------- movements

    private void expand(Node n, List<Candidate> out) {
        current = n;
        int x = n.x, y = n.y, z = n.z;
        for (int[] d : HORIZONTALS) {
            int nx = x + d[0], nz = z + d[1];
            traverse(x, y, z, nx, nz, out);
            ascend(x, y, z, d[0], d[1], out);
            descendOrFall(x, y, z, d[0], d[1], out);
            parkour(x, y, z, d[0], d[1], out);
            neo(x, y, z, d[0], d[1], out);
            overEdge(x, y, z, nx, nz, out);
        }
        for (int[] d : DIAGONALS) {
            diagonal(x, y, z, x + d[0], z + d[1], out);
        }
        climb(x, y, z, out);
        climbDown(x, y, z, out);
        wallClimb(x, y, z, out);
        wallDown(x, y, z, out);
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
        if (view.isClimbable(nx, y, nz) || isPlannedLadder(nx, y, nz)) {
            // A ladder is not a floor: walking sideways onto one the mob stops pressing on anything and slides off. Only
            // with a floor under it; a ladder is got onto from below (a climb) or from its top (a step down onto it).
            if (!view.canStandOn(nx, y - 1, nz) && !isPlannedFloor(nx, y - 1, nz)) {
                return;
            }
        } else {
            // A bridge with nothing under it either is up in the air: the way along is better taken lower down.
            double price = view.isPassable(nx, y - 2, nz) ? PathCosts.PLACE + PathCosts.AIR_BRIDGE : PathCosts.PLACE;
            cost += floorCost(nx, y - 1, nz, price, places);
        }
        if (cost >= PathCosts.INF) {
            return;
        }
        if (view.isWater(nx, y, nz)) {
            cost += PathCosts.WATER_EXTRA;
        }
        out.add(new Candidate(nx, y, nz, MoveType.TRAVERSE, cost, breaks, places));
    }

    private void ascend(int x, int y, int z, int dx, int dz, List<Candidate> out) {
        int nx = x + dx, nz = z + dz;
        List<BlockPos> breaks = new ArrayList<>(3);
        List<BlockPos> places = new ArrayList<>(1);
        double cost = PathCosts.WALK + PathCosts.JUMP;
        cost += clearCost(x, y + 2, z, breaks);
        cost += clearCost(nx, y + 2, nz, breaks);
        cost += clearCost(nx, y + 1, nz, breaks);
        if (cost >= PathCosts.INF) {
            return;
        }
        // The block we step onto: solid already, or a stair the Builder places, straight on from the stair it is on.
        if (!view.canStandOn(nx, y, nz)) {
            if (!isPlannedPlank(nx, y, nz) && (!view.isPassable(nx, y, nz) && !view.isReplaceable(nx, y, nz) || !keepsHeading(dx, dz))) {
                return;
            }
            cost += floorCost(nx, y, nz, PathCosts.STAIR, places);
            if (cost >= PathCosts.INF) {
                return;
            }
        }
        out.add(new Candidate(nx, y + 1, nz, MoveType.ASCEND, cost, breaks, places));
    }

    private void descendOrFall(int x, int y, int z, int dx, int dz, List<Candidate> out) {
        int nx = x + dx, nz = z + dz;
        List<BlockPos> breaks = new ArrayList<>(3);
        double cost = PathCosts.WALK + PathCosts.fall(1);
        cost += clearCost(nx, y + 1, nz, breaks);
        cost += clearCost(nx, y, nz, breaks);
        cost += clearCost(nx, y - 1, nz, breaks);
        if (cost >= PathCosts.INF) {
            return;
        }
        if (view.canStandOn(nx, y - 2, nz) || view.isClimbable(nx, y - 1, nz)
                || isPlannedFloor(nx, y - 2, nz) || isPlannedLadder(nx, y - 1, nz)) {
            out.add(new Candidate(nx, y - 1, nz, MoveType.DESCEND, cost, breaks, List.of()));
            return;
        }
        if (!breaks.isEmpty() || fall(nx, y, nz, out)) {
            return;
        }
        // The drop is too far to take: build the way down.
        buildDown(nx, y, nz, dx, dz, cost, out);
    }

    /** Walks off an edge into the open column at {@code nx, nz}. Returns false if there is nowhere safe to land. */
    private boolean fall(int nx, int y, int nz, List<Candidate> out) {
        for (int drop = 2; drop <= MAX_DROP_SCAN; drop++) {
            int fy = y - drop;
            if (!view.isPassable(nx, fy, nz)) {
                return false;
            }
            if (view.isWater(nx, fy, nz)) {
                out.add(new Candidate(nx, fy, nz, MoveType.FALL, PathCosts.WALK + PathCosts.fall(drop) + PathCosts.WATER_EXTRA,
                        List.of(), List.of()));
                return true;
            }
            if (view.canStandOn(nx, fy - 1, nz)) {
                if (drop > UntilDawnConfig.MAX_FALL.get()) {
                    return false;
                }
                out.add(new Candidate(nx, fy, nz, MoveType.FALL, PathCosts.WALK + PathCosts.fall(drop), List.of(), List.of()));
                return true;
            }
        }
        return false;
    }

    /**
     * A Builder's ways down a drop too far to take (squadmates can ask one for the same): a stair step down, straight on
     * from the stair it is on, or a ladder hung over the edge, on the side of the block it stands on, which it then climbs
     * down rung by rung (see {@link #climbDown}).
     */
    private void buildDown(int nx, int y, int nz, int dx, int dz, double base, List<Candidate> out) {
        if (!builds) {
            return;
        }
        if (keepsHeading(dx, dz)) {
            List<BlockPos> places = new ArrayList<>(1);
            double cost = base + floorCost(nx, y - 2, nz, PathCosts.STAIR, places);
            if (cost < PathCosts.INF) {
                out.add(new Candidate(nx, y - 1, nz, MoveType.DESCEND, cost, List.of(), places));
            }
        }
        List<BlockPos> places = new ArrayList<>(2);
        List<BlockPos> ladders = new ArrayList<>(1);
        double cost = base + ladderCost(nx, y - 1, nz, places, ladders);
        if (cost < PathCosts.INF) {
            out.add(new Candidate(nx, y - 1, nz, MoveType.DESCEND, cost, List.of(), places, ladders));
        }
    }

    private void diagonal(int x, int y, int z, int nx, int nz, List<Candidate> out) {
        // Never cut corners: both side cells must be open too. Diagonals don't break or place, and never start on a
        // ladder: a mob getting off one corner-wise lets go before it is over the floor, and falls.
        if (view.isClimbable(x, y, z) || isPlannedLadder(x, y, z) || !view.isPassable(nx, y, nz) || !view.isPassable(nx, y + 1, nz)
                || !view.isPassable(nx, y, z) || !view.isPassable(nx, y + 1, z)
                || !view.isPassable(x, y, nz) || !view.isPassable(x, y + 1, nz)
                || !view.canStandOn(nx, y - 1, nz)
                || isPlannedPlank(nx, y, z) || isPlannedPlank(nx, y + 1, z) || isPlannedPlank(x, y, nz) || isPlannedPlank(x, y + 1, nz)) {
            return;
        }
        double cost = PathCosts.DIAGONAL + (view.isWater(nx, y, nz) ? PathCosts.WATER_EXTRA : 0.0);
        out.add(new Candidate(nx, y, nz, MoveType.DIAGONAL, cost, List.of(), List.of()));
    }

    /**
     * Sprinters only: sprint-jump a gap straight ahead, as Baritone's parkour does. Lands on the first block there is
     * to land on, level with the take-off or one block up. Never breaks or places.
     */
    private void parkour(int x, int y, int z, int dx, int dz, List<Candidate> out) {
        if (!abilities.parkour() || !view.canStandOn(x, y - 1, z) || view.isWater(x, y, z) || view.isClimbable(x, y, z)
                || !view.isPassable(x, y + 2, z)) {
            return;
        }
        // Only a gap is worth jumping; with a floor next to it the mob just walks.
        if (view.canStandOn(x + dx, y - 1, z + dz) || view.isClimbable(x + dx, y - 1, z + dz)) {
            return;
        }
        for (int i = 1; i <= MAX_PARKOUR; i++) {
            int nx = x + dx * i, nz = z + dz * i;
            if (!view.isPassable(nx, y, nz)) {
                // A block in the way at foot level: land on top of it if the jump is short enough, else no jump.
                if (i >= 2 && i <= MAX_PARKOUR_ASCEND && view.canStandOn(nx, y, nz)
                        && view.isPassable(nx, y + 1, nz) && view.isPassable(nx, y + 2, nz)) {
                    out.add(new Candidate(nx, y + 1, nz, MoveType.PARKOUR, PathCosts.JUMP * 2 + PathCosts.SPRINT * i,
                            List.of(), List.of()));
                }
                return;
            }
            // The body arcs through these cells, a little over a block high.
            if (!view.isPassable(nx, y + 1, nz) || !view.isPassable(nx, y + 2, nz)) {
                return;
            }
            if (i >= 2 && view.canStandOn(nx, y - 1, nz)) {
                out.add(new Candidate(nx, y, nz, MoveType.PARKOUR, PathCosts.JUMP + PathCosts.SPRINT * i, List.of(), List.of()));
                return;
            }
        }
    }

    /**
     * Sprinters only: jump around the end of a wall that blocks the way straight ahead, out to the side, past it and back in
     * behind it, landing level with the take-off. Needs a column of open air, three high, beside the take-off, the wall and
     * the landing; there need not be any floor in it, which is what sets a neo apart from just walking round.
     */
    private void neo(int x, int y, int z, int dx, int dz, List<Candidate> out) {
        if (!abilities.parkour() || !view.canStandOn(x, y - 1, z) || view.isWater(x, y, z) || view.isClimbable(x, y, z)
                || !view.isPassable(x, y + 2, z)) {
            return;
        }
        boolean blocked = false;
        for (int length = 1; length <= MAX_NEO; length++) {
            int nx = x + dx * length, nz = z + dz * length;
            boolean open = view.isPassable(nx, y, nz) && view.isPassable(nx, y + 1, nz);
            if (!open) {
                blocked = true;
            }
            if (length < 2 || !blocked || !open || !view.isPassable(nx, y + 2, nz) || !view.canStandOn(nx, y - 1, nz)) {
                continue;
            }
            if (sideColumnOpen(x, y, z, dx, dz, length, -dz, dx) || sideColumnOpen(x, y, z, dx, dz, length, dz, -dx)) {
                out.add(new Candidate(nx, y, nz, MoveType.NEO, PathCosts.JUMP + PathCosts.SPRINT * (length + 1), List.of(), List.of()));
            }
        }
    }

    /** The cells one block to a side, from beside the take-off to beside the landing and three high, are all open. */
    private boolean sideColumnOpen(int x, int y, int z, int dx, int dz, int length, int sx, int sz) {
        for (int i = 0; i <= length; i++) {
            for (int h = 0; h <= 2; h++) {
                if (!view.isPassable(x + dx * i + sx, y + h, z + dz * i + sz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Up one block on a ladder in the cells the mob climbs from and into. Where there is none yet, a Builder hangs one (or a
     * squadmate asks it to): on a wall beside the cell, or out in the open on a plank it puts beside it, so it climbs a
     * pillar of planks with a ladder up its side, which squadmates can climb after it. The rung below may be planned already;
     * from a floor there need be none, as the mob jumps up into the ladder above.
     */
    private void climb(int x, int y, int z, List<Candidate> out) {
        List<BlockPos> places = new ArrayList<>(4);
        List<BlockPos> ladders = new ArrayList<>(2);
        double cost = PathCosts.CLIMB;
        if (!view.isClimbable(x, y, z) && !isPlannedLadder(x, y, z) && !view.canStandOn(x, y - 1, z) && !isPlannedFloor(x, y - 1, z)) {
            cost += ladderCost(x, y, z, places, ladders);
        }
        if (cost < PathCosts.INF && !view.isClimbable(x, y + 1, z)) {
            cost += ladderCost(x, y + 1, z, places, ladders);
        }
        if (cost >= PathCosts.INF) {
            return;
        }
        List<BlockPos> breaks = new ArrayList<>(1);
        cost += clearCost(x, y + 2, z, breaks);
        if (cost >= PathCosts.INF) {
            return;
        }
        out.add(new Candidate(x, y + 1, z, MoveType.CLIMB, cost, breaks, places, ladders));
    }

    /**
     * Down one block on a ladder. Below a ladder the mob hangs on (one there already, or one it planned), a Builder hangs the
     * next rung as it goes; from the last rung it lets go onto the floor a block below.
     */
    private void climbDown(int x, int y, int z, List<Candidate> out) {
        if (!view.isPassable(x, y - 1, z)) {
            return;
        }
        if (view.isClimbable(x, y - 1, z)) {
            out.add(new Candidate(x, y - 1, z, MoveType.CLIMB_DOWN, PathCosts.CLIMB * 0.8, List.of(), List.of()));
            return;
        }
        if (!view.isClimbable(x, y, z) && !isPlannedLadder(x, y, z) || view.isWater(x, y - 1, z)) {
            return;
        }
        if (view.canStandOn(x, y - 2, z) || isPlannedFloor(x, y - 2, z)) {
            out.add(new Candidate(x, y - 1, z, MoveType.CLIMB_DOWN, PathCosts.CLIMB * 0.8, List.of(), List.of()));
            return;
        }
        if (!builds) {
            return;
        }
        List<BlockPos> places = new ArrayList<>(2);
        List<BlockPos> ladders = new ArrayList<>(1);
        double cost = PathCosts.CLIMB * 0.8 + ladderCost(x, y - 1, z, places, ladders);
        if (cost < PathCosts.INF) {
            out.add(new Candidate(x, y - 1, z, MoveType.CLIMB_DOWN, cost, List.of(), places, ladders));
        }
    }

    /**
     * Spiders only: up one block, clinging to a wall beside the feet. The cell it climbs into may be in mid-air beside
     * the wall; from the top of the wall it walks on (a traverse onto the wall's top block).
     */
    private void wallClimb(int x, int y, int z, List<Candidate> out) {
        if (!abilities.climbWalls() || view.isWater(x, y, z) || !view.canCling(x, y, z)
                || !view.isPassable(x, y + 1, z) || !view.isPassable(x, y + 2, z)) {
            return;
        }
        out.add(new Candidate(x, y + 1, z, MoveType.WALL_CLIMB, PathCosts.WALL_CLIMB, List.of(), List.of()));
    }

    /** Spiders only: down one block, clinging to a wall beside the cell below, or onto the floor at the foot of it. */
    private void wallDown(int x, int y, int z, List<Candidate> out) {
        if (!abilities.climbWalls() || !view.isPassable(x, y - 1, z)
                || !(view.canCling(x, y - 1, z) || view.canStandOn(x, y - 2, z))) {
            return;
        }
        out.add(new Candidate(x, y - 1, z, MoveType.WALL_DOWN, PathCosts.WALL_DOWN, List.of(), List.of()));
    }

    /**
     * Spiders only: over the edge of the block it stands on and onto the side of it, where a drop beyond is too far to
     * just step down (with a floor one below, it's a descend). From there it climbs down the wall.
     */
    private void overEdge(int x, int y, int z, int nx, int nz, List<Candidate> out) {
        if (!abilities.climbWalls() || !view.canStandOn(x, y - 1, z) || !view.isPassable(nx, y + 1, nz)
                || !view.isPassable(nx, y, nz) || !view.isPassable(nx, y - 1, nz) || view.isClimbable(nx, y - 1, nz)
                || view.canStandOn(nx, y - 2, nz)) {
            return;
        }
        out.add(new Candidate(nx, y - 1, nz, MoveType.WALL_DOWN, PathCosts.WALK * 0.8 + PathCosts.WALL_DOWN, List.of(), List.of()));
    }

    /**
     * Builders only: tower up. Nobody can follow a tower, and a ladder up a pillar of planks does the same job, so it is a
     * last resort; with squadmates who can't build, all the more so.
     */
    private void pillar(int x, int y, int z, List<Candidate> out) {
        if (!abilities.canPlace() || !view.isReplaceable(x, y, z) || view.isWater(x, y, z) || view.isClimbable(x, y, z)
                || isPlannedLadder(x, y, z) || AccessCorridors.isReserved(level, x, y, z)) {
            return;
        }
        List<BlockPos> breaks = new ArrayList<>(1);
        double cost = PathCosts.PILLAR + (squad.hasFollowers() ? PathCosts.PILLAR_ALONE : 0.0) + clearCost(x, y + 2, z, breaks);
        if (cost >= PathCosts.INF) {
            return;
        }
        out.add(new Candidate(x, y + 1, z, MoveType.PILLAR, cost, breaks, List.of(new BlockPos(x, y, z))));
    }

    private void digDown(int x, int y, int z, List<Candidate> out) {
        if (!abilities.canBreak() || abilities.explosive() || view.isPassable(x, y - 1, z) || !view.canStandOn(x, y - 2, z)) {
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
        long key = BlockPos.asLong(x, y, z);
        if (squad.isBreakPending(key)) {
            breaks.add(new BlockPos(x, y, z));
            return PathCosts.TEAM_WAIT;
        }
        double mine = view.mineCost(x, y, z);
        if (mine >= PathCosts.INF || avoid.contains(key)) {
            return PathCosts.INF;
        }
        // One explosion clears the whole opening.
        boolean firstInMove = breaks.isEmpty();
        double cost;
        if (abilities.canBreak()) {
            cost = abilities.explosive() ? (firstInMove ? PathCosts.BLAST : 2.0) : mine;
        } else if (abilities.canPlace() && BlockView.isUnstable(state)) {
            // A Builder takes its own planks down quickly.
            cost = UntilDawnConfig.BUILDER_BREAK_TICKS.get();
        } else if (squad.canAssistDig()) {
            cost = mine + (firstInMove ? PathCosts.ASSIST : 0.0);
        } else if (squad.canAssistBlast() && view.isBlastable(x, y, z)) {
            cost = firstInMove ? PathCosts.BLAST + PathCosts.ASSIST : 2.0;
        } else {
            return PathCosts.INF;
        }
        breaks.add(new BlockPos(x, y, z));
        return cost;
    }

    /**
     * Cost of hanging a ladder in a cell, recording it as a block to place: on a wall beside it if there is one, else on a
     * plank put beside it to hold it (see {@link #supportCost}).
     */
    private double ladderCost(int x, int y, int z, List<BlockPos> places, List<BlockPos> ladders) {
        if (!view.isReplaceable(x, y, z) || view.isWater(x, y, z)) {
            return PathCosts.INF;
        }
        BlockPos pos = new BlockPos(x, y, z);
        long key = pos.asLong();
        if (squad.isPlacePending(key)) {
            // A squadmate is hanging it, and whatever holds it up.
            places.add(pos);
            ladders.add(pos);
            return PathCosts.TEAM_WAIT;
        }
        if (avoid.contains(key)) {
            return PathCosts.INF;
        }
        double cost;
        if (abilities.canPlace()) {
            cost = PathCosts.LADDER;
        } else if (squad.canAssistBuild()) {
            cost = PathCosts.LADDER + (places.isEmpty() ? PathCosts.ASSIST : 0.0);
        } else {
            return PathCosts.INF;
        }
        if (!view.canHoldLadder(x, y, z) && !plankBeside(x, y, z, places)) {
            cost += supportCost(x, y, z, places);
            if (cost >= PathCosts.INF) {
                return PathCosts.INF;
            }
        }
        places.add(pos);
        ladders.add(pos);
        return cost;
    }

    /** A plank planned beside this cell, by this move or the route a few steps back: a ladder can hang on it. */
    private boolean plankBeside(int x, int y, int z, List<BlockPos> places) {
        for (int[] d : HORIZONTALS) {
            int sx = x + d[0], sz = z + d[1];
            if (places.contains(new BlockPos(sx, y, sz)) || isPlannedPlank(sx, y, sz)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cost of a plank for a ladder to hang on beside its cell, recording it as a block to place. It goes where it carries on
     * the pillar of planks above or below it, so the ladder runs up one straight pillar; otherwise on something solid; and
     * otherwise on the side away from the goal, so the mob climbs with its back to the pillar and steps off the top the way
     * it is going.
     */
    private double supportCost(int x, int y, int z, List<BlockPos> places) {
        BlockPos anchor = goal.anchor();
        int towardX = Integer.signum(anchor.getX() - x);
        int towardZ = Integer.signum(anchor.getZ() - z);
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int[] d : HORIZONTALS) {
            int sx = x + d[0], sz = z + d[1];
            if (!view.isReplaceable(sx, y, sz) || view.isWater(sx, y, sz) || view.isClimbable(sx, y, sz)
                    || AccessCorridors.isReserved(level, sx, y, sz) || avoid.contains(BlockPos.asLong(sx, y, sz))) {
                continue;
            }
            int rank;
            if (places.contains(new BlockPos(sx, y - 1, sz)) || places.contains(new BlockPos(sx, y + 1, sz))
                    || isPlannedPlank(sx, y - 1, sz) || isPlannedPlank(sx, y + 1, sz)) {
                rank = 0;
            } else if (view.canStandOn(sx, y - 1, sz)) {
                rank = 1;
            } else {
                rank = 2;
            }
            // Away from the goal is 0, sideways 1, towards it 2.
            int score = rank * 3 + d[0] * towardX + d[1] * towardZ + 1;
            if (score < bestScore) {
                bestScore = score;
                best = new BlockPos(sx, y, sz);
            }
        }
        if (best == null) {
            return PathCosts.INF;
        }
        places.add(best);
        return PathCosts.LADDER_SUPPORT;
    }

    /**
     * Cost of having something to stand on, recording the block to place if one is needed: {@code price} for a Builder (a
     * bridge plank or a stair step), and more to ask one.
     */
    private double floorCost(int x, int y, int z, double price, List<BlockPos> places) {
        if (view.canStandOn(x, y, z) || isPlannedFloor(x, y, z)) {
            return 0.0;
        }
        if (isPlannedPlank(x, y, z)) {
            // The top of the pillar a ladder hangs on: one block wide, in mid-air, no place to stand.
            return PathCosts.INF;
        }
        if (!view.isReplaceable(x, y, z)) {
            return PathCosts.INF;
        }
        // Air an existing stair, bridge or tower needs for its users: building there would wall it in.
        if (AccessCorridors.isReserved(level, x, y, z)) {
            return PathCosts.INF;
        }
        long key = BlockPos.asLong(x, y, z);
        if (squad.isPlacePending(key)) {
            places.add(new BlockPos(x, y, z));
            return PathCosts.TEAM_WAIT;
        }
        if (avoid.contains(key)) {
            return PathCosts.INF;
        }
        double cost;
        if (abilities.canPlace()) {
            cost = price;
        } else if (squad.canAssistBuild()) {
            cost = price + PathCosts.ASSIST;
        } else {
            return PathCosts.INF;
        }
        places.add(new BlockPos(x, y, z));
        return cost;
    }
}
