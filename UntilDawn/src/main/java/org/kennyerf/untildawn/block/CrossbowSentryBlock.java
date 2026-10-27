package org.kennyerf.untildawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/**
 * A crossbow turret in a solid block, which goes on any surface like a button. It shoots the nearest monster it can see
 * within a 160 degree cone in front of it (out from a wall, straight up from a floor, straight down from a ceiling),
 * then takes three seconds to reload.
 */
public class CrossbowSentryBlock extends FaceAttachedHorizontalDirectionalBlock {
    private static final double RANGE = 20.0;
    /** Half of the 160 degree view, as the cosine of the angle from straight ahead. */
    private static final double VIEW_COS = Math.cos(Math.toRadians(80.0));
    private static final int RELOAD_TICKS = 60;
    private static final int SEARCH_TICKS = 10;
    private static final float ARROW_SPEED = 3.0F;
    /** An arrow hits for its speed times this, rounded up: 3.0 * 2.66 = 7.98, so 8 even after a little drag. */
    private static final double ARROW_DAMAGE = 2.66;

    public CrossbowSentryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.WALL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, SEARCH_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction facing = aim(state);
        Vec3 aimed = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        Vec3 muzzle = Vec3.atCenterOf(pos).add(aimed.scale(0.6));
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(RANGE),
                        entity -> entity instanceof Enemy && entity.isAlive() && canShoot(level, aimed, muzzle, entity))
                .stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(muzzle))).orElse(null);
        if (target == null) {
            level.scheduleTick(pos, this, SEARCH_TICKS);
            return;
        }
        Vec3 aim = target.getBoundingBox().getCenter().subtract(muzzle);
        Arrow arrow = new Arrow(level, muzzle.x, muzzle.y, muzzle.z, new ItemStack(Items.ARROW), null);
        arrow.setBaseDamage(ARROW_DAMAGE);
        // Aimed a little high, to make up for the drop over the distance.
        arrow.shoot(aim.x, aim.y + Math.sqrt(aim.x * aim.x + aim.z * aim.z) * 0.02, aim.z, ARROW_SPEED, 0.0F);
        level.addFreshEntity(arrow);
        level.playSound(null, pos, SoundEvents.CROSSBOW_SHOOT, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.scheduleTick(pos, this, RELOAD_TICKS);
    }

    /** The way the turret faces: out from a wall, up from a floor, down from a ceiling. */
    private static Direction aim(BlockState state) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> Direction.UP;
            case CEILING -> Direction.DOWN;
            case WALL -> state.getValue(FACING);
        };
    }

    /** In range, inside the view cone, and with nothing in the way. */
    private static boolean canShoot(ServerLevel level, Vec3 aimed, Vec3 muzzle, LivingEntity target) {
        Vec3 to = target.getBoundingBox().getCenter().subtract(muzzle);
        double distance = to.length();
        if (distance > RANGE || to.dot(aimed) < VIEW_COS * distance) {
            return false;
        }
        return level.clip(new ClipContext(muzzle, muzzle.add(to), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target))
                .getType() == HitResult.Type.MISS;
    }
}
