package org.kennyerf.untildawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.game.WaveManager;

/**
 * Placing the Core starts the wave game. Its removal (broken by a player, or worn down by mobs)
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
}
