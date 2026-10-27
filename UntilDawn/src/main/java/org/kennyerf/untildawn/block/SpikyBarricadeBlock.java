package org.kennyerf.untildawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A barricade studded with spikes: anything that touches it, or starts to break it, is hurt.
 * Touching is checked a few times a second, since a mob pushing on a thin wall never overlaps it.
 */
public class SpikyBarricadeBlock extends BarricadeBlock {
    private static final float DAMAGE = 3.0F;
    private static final int TOUCH_INTERVAL = 10;

    public SpikyBarricadeBlock(Properties properties, float backHardness) {
        super(properties, backHardness);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, TOUCH_INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        AABB touching = state.getShape(level, pos).bounds().move(pos).inflate(0.1);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, touching)) {
            entity.hurtServer(level, level.damageSources().cactus(), DAMAGE);
        }
        level.scheduleTick(pos, this, TOUCH_INTERVAL);
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel serverLevel) {
            player.hurtServer(serverLevel, level.damageSources().cactus(), DAMAGE);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel) {
            player.hurtServer(serverLevel, level.damageSources().cactus(), DAMAGE);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
