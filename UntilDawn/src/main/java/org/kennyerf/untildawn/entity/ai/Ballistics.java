package org.kennyerf.untildawn.entity.ai;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Arrow flight for plunging fire: finds a steep arc that comes down on a target, and checks it clears the terrain.
 * Arrows lose 1% of their speed and fall 0.05 blocks/tick faster every tick.
 */
public final class Ballistics {
    private static final double GRAVITY = 0.05;
    private static final double DRAG = 0.99;
    private static final int MAX_TICKS = 400;
    private static final double MIN_SPEED = 0.3;
    /** Steepest first: plunging fire should drop onto the target from as high as possible. */
    private static final double[] ANGLES = {80, 75, 70, 65, 60, 55, 50, 45};
    /** Hitting a block this close to the target still counts: the arrow lands on top of them. */
    private static final double LANDING_SLACK = 1.5;

    /** A launch direction (unit vector) and speed. */
    public record Shot(Vec3 direction, double speed) {
    }

    private Ballistics() {
    }

    /**
     * The steepest arc from {@code start} that comes down on {@code target} without hitting anything on the way,
     * with the bow drawn at most to {@code maxSpeed}; null if the target is out of reach or every arc is blocked.
     */
    public static @Nullable Shot plungingShot(Level level, Entity shooter, Vec3 start, Vec3 target, double maxSpeed) {
        double dx = target.x - start.x;
        double dz = target.z - start.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double rise = target.y - start.y;
        if (distance < 2.0) {
            return null;
        }
        for (double degrees : ANGLES) {
            double angle = Math.toRadians(degrees);
            double fastest = reach(angle, maxSpeed, rise);
            if (Double.isNaN(fastest) || fastest < distance) {
                continue;
            }
            // Faster means further, so the speed can be bisected.
            double low = MIN_SPEED;
            double high = maxSpeed;
            for (int i = 0; i < 24; i++) {
                double mid = (low + high) / 2.0;
                double reach = reach(angle, mid, rise);
                if (Double.isNaN(reach) || reach < distance) {
                    low = mid;
                } else {
                    high = mid;
                }
            }
            double speed = (low + high) / 2.0;
            Vec3 direction = new Vec3(dx / distance * Math.cos(angle), Math.sin(angle), dz / distance * Math.cos(angle));
            if (isClear(level, shooter, start, direction.scale(speed), target)) {
                return new Shot(direction, speed);
            }
        }
        return null;
    }

    /** How far the arrow travels horizontally before coming back down to {@code rise}; NaN if it never does. */
    static double reach(double angle, double speed, double rise) {
        double horizontal = 0.0;
        double height = 0.0;
        double vh = Math.cos(angle) * speed;
        double vy = Math.sin(angle) * speed;
        for (int tick = 0; tick < MAX_TICKS; tick++) {
            double nextHeight = height + vy;
            double nextHorizontal = horizontal + vh;
            if (vy < 0 && height >= rise && nextHeight < rise) {
                return horizontal + (nextHorizontal - horizontal) * (height - rise) / (height - nextHeight);
            }
            height = nextHeight;
            horizontal = nextHorizontal;
            vh *= DRAG;
            vy = vy * DRAG - GRAVITY;
        }
        return Double.NaN;
    }

    /** Follows the arc tick by tick and checks no block is in the way before it reaches the target. */
    private static boolean isClear(Level level, Entity shooter, Vec3 start, Vec3 velocity, Vec3 target) {
        Vec3 position = start;
        Vec3 motion = velocity;
        for (int tick = 0; tick < MAX_TICKS; tick++) {
            Vec3 next = position.add(motion);
            BlockHitResult hit = level.clip(new ClipContext(position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
            if (hit.getType() != HitResult.Type.MISS) {
                return hit.getLocation().distanceTo(target) <= LANDING_SLACK;
            }
            if (motion.y < 0 && next.y <= target.y) {
                return true;
            }
            position = next;
            motion = new Vec3(motion.x * DRAG, motion.y * DRAG - GRAVITY, motion.z * DRAG);
        }
        return false;
    }
}
