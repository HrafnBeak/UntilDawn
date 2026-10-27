package org.kennyerf.untildawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.kennyerf.untildawn.game.WaveManager;

/**
 * How Unstable Planks and Unstable Ladders crumble away once the night is over, a few at a time like leaves, so that none
 * is left when the next night comes. Each block checks on a scheduled tick (which a saved chunk keeps, so blocks in
 * unloaded chunks catch up when loaded): at night it only checks again later. By day it has a chance to go that grows as
 * the next night nears, and goes for certain just before it. While the Core screen is skipping to night the clock runs
 * over a hundred times faster, so blocks check every few ticks, go more readily, and go for certain a dozen ticks before.
 */
public final class UnstableDecay {
    /** Chance per check, by day: at least this much, or more as the next night is near (see {@link #chance}). */
    private static final double MIN_CHANCE = 0.05;
    private static final double MIN_CHANCE_SKIPPING = 0.3;
    /** Clock ticks before night at which the chance rises to a half. */
    private static final double HALF_CHANCE_TICKS = 60.0;
    /** Clock ticks before night from which a block goes for certain. */
    private static final long CERTAIN_TICKS = 40;
    private static final int CERTAIN_TICKS_SKIPPING = 12;

    private UnstableDecay() {
    }

    /** Starts a block's checks; call when it is placed. */
    public static void begin(Level level, BlockPos pos, Block block) {
        level.scheduleTick(pos, block, 20 + level.getRandom().nextInt(20));
    }

    /** A block's check: it goes, or checks again later. */
    public static void check(ServerLevel level, BlockPos pos, Block block, RandomSource random) {
        if (WaveManager.isNight(level)) {
            level.scheduleTick(pos, block, 40);
            return;
        }
        boolean skipping = WaveManager.isSkippingToNight(level);
        long left = WaveManager.ticksUntilNight(level);
        long certain = skipping ? (long) WaveManager.skipRate() * CERTAIN_TICKS_SKIPPING : CERTAIN_TICKS;
        if (left <= certain || random.nextDouble() < chance(left, skipping)) {
            level.destroyBlock(pos, false);
        } else {
            level.scheduleTick(pos, block, skipping ? 1 + random.nextInt(5) : 10 + random.nextInt(21));
        }
    }

    /** Chance a block goes on one check, {@code left} clock ticks before the next night. */
    static double chance(long left, boolean skipping) {
        return Math.max(skipping ? MIN_CHANCE_SKIPPING : MIN_CHANCE, 1.0 / (1.0 + left / HALF_CHANCE_TICKS));
    }
}
