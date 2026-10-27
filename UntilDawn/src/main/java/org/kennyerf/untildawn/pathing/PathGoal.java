package org.kennyerf.untildawn.pathing;

import net.minecraft.core.BlockPos;

/**
 * Where a search should end. Like Baritone's goals: a test plus an admissible-ish cost estimate.
 */
public interface PathGoal {
    boolean isGoal(int x, int y, int z);

    double heuristic(int x, int y, int z);

    /** The block the goal is centred on, used to notice when a moving target has wandered off. */
    BlockPos anchor();

    String describe();

    /** Extra cost of moving into this cell, for goals that want to be come at a particular way. */
    default double extraCost(int x, int y, int z) {
        return 0.0;
    }

    /**
     * How many blocks the goal is above this cell. {@link #heuristic} prices climbing at a jump a block, as if there
     * were steps; mobs that have to build their way up add what that really costs them (see MobPathfinder).
     */
    default int rise(int x, int y, int z) {
        return 0;
    }

    static double estimate(double dx, double dy, double dz) {
        double ax = Math.abs(dx);
        double az = Math.abs(dz);
        double straight = Math.abs(ax - az);
        double diagonal = Math.min(ax, az);
        double horizontal = (straight + diagonal * Math.sqrt(2)) * PathCosts.WALK;
        double vertical = dy > 0 ? dy * PathCosts.JUMP : -dy * PathCosts.FALL_PER_BLOCK;
        return horizontal + vertical;
    }

    /** Get within {@code radius} blocks horizontally and one block vertically of a position. */
    static PathGoal near(BlockPos target, int radius, String description) {
        return new PathGoal() {
            @Override
            public boolean isGoal(int x, int y, int z) {
                int dx = x - target.getX();
                int dz = z - target.getZ();
                return dx * dx + dz * dz <= radius * radius && Math.abs(y - target.getY()) <= 1;
            }

            @Override
            public double heuristic(int x, int y, int z) {
                return estimate(target.getX() - x, target.getY() - y, target.getZ() - z);
            }

            @Override
            public int rise(int x, int y, int z) {
                return Math.max(0, target.getY() - 1 - y);
            }

            @Override
            public BlockPos anchor() {
                return target;
            }

            @Override
            public String describe() {
                return description;
            }
        };
    }

    /** Get at least {@code distance} blocks away (horizontally) from a position, to keep out of a target's reach. */
    static PathGoal awayFrom(BlockPos from, double distance, String description) {
        return new PathGoal() {
            @Override
            public boolean isGoal(int x, int y, int z) {
                int dx = x - from.getX();
                int dz = z - from.getZ();
                return dx * dx + dz * dz >= distance * distance;
            }

            @Override
            public double heuristic(int x, int y, int z) {
                return Math.max(0.0, distance - Math.hypot(x - from.getX(), z - from.getZ())) * PathCosts.WALK;
            }

            @Override
            public BlockPos anchor() {
                return from;
            }

            @Override
            public String describe() {
                return description;
            }
        };
    }

    /** Stand anywhere inside the Core area (the Core itself excluded). */
    static PathGoal coreArea(BlockPos core, int radius, int height) {
        return new PathGoal() {
            @Override
            public boolean isGoal(int x, int y, int z) {
                return Math.abs(x - core.getX()) <= radius && Math.abs(z - core.getZ()) <= radius
                        && y >= core.getY() && y <= core.getY() + height - 1
                        && !(x == core.getX() && y == core.getY() && z == core.getZ());
            }

            @Override
            public double heuristic(int x, int y, int z) {
                int dx = Math.max(0, Math.abs(x - core.getX()) - radius);
                int dz = Math.max(0, Math.abs(z - core.getZ()) - radius);
                int dy = y < core.getY() ? core.getY() - y : Math.min(0, core.getY() + height - 1 - y);
                return estimate(dx, dy, dz);
            }

            @Override
            public int rise(int x, int y, int z) {
                return Math.max(0, core.getY() - y);
            }

            @Override
            public BlockPos anchor() {
                return core;
            }

            @Override
            public String describe() {
                return "the Core";
            }
        };
    }

    /**
     * Get into the Core area from one direction: stand on its edge on that side ({@code sx}, {@code sz} each -1, 0 or 1;
     * a corner when both are set). The rest of the area costs extra to cross, so the route goes round to that side.
     */
    static PathGoal coreSide(BlockPos core, int radius, int height, int sx, int sz) {
        PathGoal area = coreArea(core, radius, height);
        BlockPos edge = core.offset(sx * radius, 0, sz * radius);
        return new PathGoal() {
            @Override
            public boolean isGoal(int x, int y, int z) {
                return area.isGoal(x, y, z) && (sx == 0 || x == edge.getX()) && (sz == 0 || z == edge.getZ());
            }

            @Override
            public double heuristic(int x, int y, int z) {
                int dx = sx != 0 ? edge.getX() - x : Math.max(0, Math.abs(x - core.getX()) - radius);
                int dz = sz != 0 ? edge.getZ() - z : Math.max(0, Math.abs(z - core.getZ()) - radius);
                int dy = y < core.getY() ? core.getY() - y : Math.min(0, core.getY() + height - 1 - y);
                return estimate(dx, dy, dz);
            }

            @Override
            public double extraCost(int x, int y, int z) {
                return area.isGoal(x, y, z) && !isGoal(x, y, z) ? PathCosts.OFF_SIDE : 0.0;
            }

            @Override
            public int rise(int x, int y, int z) {
                return area.rise(x, y, z);
            }

            @Override
            public BlockPos anchor() {
                return edge;
            }

            @Override
            public String describe() {
                return "the Core from the " + (sz < 0 ? "north" : sz > 0 ? "south" : "") + (sx < 0 ? "west" : sx > 0 ? "east" : "");
            }
        };
    }
}
