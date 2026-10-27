package org.kennyerf.untildawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.game.CoreInterface;
import org.kennyerf.untildawn.game.WaveManager;

/**
 * Placing the Core starts the wave game; using it opens its screen (see {@link CoreInterface}). Its removal (broken by a player, or worn down by mobs)
 * is detected by {@link WaveManager}, which ends the game.
 */
public class CoreBlock extends Block {
    public CoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (level instanceof ServerLevel serverLevel) {
            WaveManager.onCorePlaced(serverLevel, pos, by);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            CoreInterface.open(serverPlayer, serverLevel, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
