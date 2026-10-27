package org.kennyerf.monster.pathing;

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
            public BlockPos anchor() {
                return target;
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
            public BlockPos anchor() {
                return core;
            }

            @Override
            public String describe() {
                return "the Core";
            }
        };
    }
}
