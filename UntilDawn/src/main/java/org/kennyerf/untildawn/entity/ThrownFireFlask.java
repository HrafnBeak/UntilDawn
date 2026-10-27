package org.kennyerf.untildawn.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.kennyerf.untildawn.registry.ModBlocks;
import org.kennyerf.untildawn.registry.ModEntities;
import org.kennyerf.untildawn.registry.ModItems;

/** A Fire Flask: where it breaks it leaves a Low Fire, on the floor below if it broke in the air beside a wall. */
public class ThrownFireFlask extends ThrownFlask {
    /** How far down a broken flask looks for a floor. */
    private static final int MAX_DROP = 4;

    public ThrownFireFlask(EntityType<? extends ThrownFireFlask> type, Level level) {
        super(type, level);
    }

    public ThrownFireFlask(ServerLevel level, LivingEntity owner, ItemStack stack) {
        super(ModEntities.FIRE_FLASK, level, owner, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.FIRE_FLASK;
    }

    @Override
    protected void shatter(ServerLevel level, BlockPos at, Vec3 where) {
        BlockPos spot = at;
        for (int drop = 0; drop < MAX_DROP && isOpen(level.getBlockState(spot.below())); drop++) {
            spot = spot.below();
        }
        if (!isOpen(level.getBlockState(spot))) {
            spot = at.above();
        }
        if (isOpen(level.getBlockState(spot))) {
            level.setBlockAndUpdate(spot, ModBlocks.LOW_FIRE.defaultBlockState());
        }
    }

    /** Whether fire can go in a cell: air, or something fire replaces (grass, snow layers), but not water. */
    private static boolean isOpen(BlockState state) {
        return (state.isAir() || state.canBeReplaced()) && state.getFluidState().isEmpty();
    }
}
