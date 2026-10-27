package org.kennyerf.untildawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The fire a Fire Flask leaves: it sets whoever touches it alight and hurts them like normal fire, but it burns
 * nothing around it, never spreads, and goes out by itself after one to three minutes.
 */
public class LowFireBlock extends BaseFireBlock {
    private static final int MIN_TICKS = 60 * 20;
    private static final int MAX_TICKS = 3 * 60 * 20;

    public LowFireBlock(Properties properties) {
        super(properties, 1.0F);
    }

    @Override
    protected boolean canBurn(BlockState state) {
        return false;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, MIN_TICKS + level.getRandom().nextInt(MAX_TICKS - MIN_TICKS + 1));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
    }
}
