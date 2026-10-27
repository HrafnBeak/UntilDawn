package org.kennyerf.untildawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A thin wall three blocks tall that the player builds to hold the line. It has a front and a back: it is placed facing
 * away from the player, and it takes much longer to break from the front (the block's own strength, the side the
 * enemy comes from) than from behind ({@link #backHardness}, where the defenders stand).
 * <p>
 * The three blocks are one structure, like a door's two: placing one fills all three, and breaking any of them
 * removes the rest. Only the bottom block drops the item.
 */
public class BarricadeBlock extends Block {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /** Which of the three blocks this is, from the ground up. */
    public enum Part implements StringRepresentable {
        BOTTOM("bottom"), MIDDLE("middle"), TOP("top");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public @Nullable Part above() {
            return this == TOP ? null : values()[ordinal() + 1];
        }

        public @Nullable Part below() {
            return this == BOTTOM ? null : values()[ordinal() - 1];
        }
    }

    // Two pixels thick, against the edge the wall faces.
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.box(0, 0, 0, 16, 16, 2));

    private final float backHardness;

    /** The block's own strength (in its properties) is the hardness from the front. */
    public BarricadeBlock(Properties properties, float backHardness) {
        super(properties);
        this.backHardness = backHardness;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.BOTTOM));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** True if {@code player} is on the back side of the wall (the side it was placed from). */
    protected static boolean isBehind(BlockState state, Player player, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        double side = (player.getX() - (pos.getX() + 0.5)) * facing.getStepX() + (player.getZ() - (pos.getZ() + 0.5)) * facing.getStepZ();
        return side < 0;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        float hardness = isBehind(state, player, pos) ? backHardness : state.getDestroySpeed(level, pos);
        int modifier = player.hasCorrectToolForDrops(state) ? 30 : 100;
        return player.getDestroySpeed(state) / hardness / modifier;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() + 2 > level.getMaxY()) {
            return null;
        }
        for (int i = 1; i <= 2; i++) {
            if (!level.getBlockState(pos.above(i)).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection()).setValue(PART, Part.BOTTOM);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        // Shape updates are held back so the first block doesn't see the second one missing.
        int flags = Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE;
        level.setBlock(pos.above(), state.setValue(PART, Part.MIDDLE), flags);
        level.setBlock(pos.above(2), state.setValue(PART, Part.TOP), flags);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        Part part = state.getValue(PART);
        if (part == Part.BOTTOM) {
            return belowState.isFaceSturdy(level, below, Direction.UP);
        }
        return belowState.is(this) && belowState.getValue(PART) == part.below();
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        Part part = state.getValue(PART);
        if (directionToNeighbour == Direction.UP && part != Part.TOP) {
            if (!neighbourState.is(this) || neighbourState.getValue(PART) != part.above()) {
                return Blocks.AIR.defaultBlockState();
            }
        } else if (directionToNeighbour == Direction.DOWN) {
            boolean held = part == Part.BOTTOM ? state.canSurvive(level, pos)
                    : neighbourState.is(this) && neighbourState.getValue(PART) == part.below();
            if (!held) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops()) {
            // Creative: take the rest away quietly, so the bottom block doesn't drop an item.
            BlockPos bottom = pos.below(state.getValue(PART).ordinal());
            for (int i = 0; i < Part.values().length; i++) {
                BlockPos other = bottom.above(i);
                BlockState otherState = level.getBlockState(other);
                if (!other.equals(pos) && otherState.is(this)) {
                    level.setBlock(other, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    level.levelEvent(player, 2001, other, Block.getId(otherState));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
