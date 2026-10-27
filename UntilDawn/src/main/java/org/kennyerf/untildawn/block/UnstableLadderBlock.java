package org.kennyerf.untildawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The ladders Builders hang: crumble away after the night, see {@link UnstableDecay}. */
public class UnstableLadderBlock extends LadderBlock {
    public UnstableLadderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            UnstableDecay.begin(level, pos, this);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        UnstableDecay.check(level, pos, this, random);
    }

    /** Ladders placed before blocks decayed have no check scheduled; the random tick (see the block's properties) starts them. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        UnstableDecay.begin(level, pos, this);
    }
}
