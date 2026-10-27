package org.kennyerf.untildawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * A flat plate of spikes that goes on a floor, wall or ceiling. Anything alive that touches it is hurt hard, and then the
 * spikes need a second to come back up. Nothing collides with it, so mobs walk straight onto it.
 */
public class SpikeTrapBlock extends FaceAttachedHorizontalDirectionalBlock {
    public static final BooleanProperty READY = BooleanProperty.create("ready");

    private static final float DAMAGE = 20.0F;
    private static final int COOLDOWN_TICKS = 20;
    private static final Map<AttachFace, Map<Direction, VoxelShape>> SHAPES = Shapes.rotateAttachFace(Block.boxZ(16.0, 16.0, 9.0, 16.0));

    public SpikeTrapBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.WALL).setValue(READY, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE, READY);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACE)).get(state.getValue(FACING));
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier,
            boolean isPrecise) {
        if (level instanceof ServerLevel serverLevel && state.getValue(READY) && entity instanceof LivingEntity) {
            entity.hurtServer(serverLevel, level.damageSources().cactus(), DAMAGE);
            level.setBlock(pos, state.setValue(READY, false), Block.UPDATE_ALL);
            level.scheduleTick(pos, this, COOLDOWN_TICKS);
            level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.6F, 1.6F);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(READY)) {
            level.setBlock(pos, state.setValue(READY, true), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.4F, 1.8F);
        }
    }
}
