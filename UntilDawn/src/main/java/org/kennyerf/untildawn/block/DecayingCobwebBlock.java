package org.kennyerf.untildawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A cobweb a Webber's shot leaves: it holds like a normal one, except that spiders (who spun it) go through it
 * untouched, and it vanishes after 2 to 4 seconds.
 */
public class DecayingCobwebBlock extends WebBlock {
    private static final int MIN_TICKS = 40;
    private static final int MAX_TICKS = 80;

    public DecayingCobwebBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier,
            boolean isPrecise) {
        if (!(entity instanceof Spider)) {
            super.entityInside(state, level, pos, entity, effectApplier, isPrecise);
        }
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
