package org.kennyerf.untildawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.kennyerf.untildawn.registry.ModBlocks;

import java.util.ArrayList;
import java.util.List;

/**
 * A cobweb a Webber's shot leaves: it holds like a normal one, except that spiders (who spun it) go through it
 * untouched, and it vanishes after 2 to 4 seconds.
 */
public class DecayingCobwebBlock extends WebBlock {
    private static final int MIN_TICKS = 40;
    private static final int MAX_TICKS = 80;
    private static final int MAX_WEBS = 3;

    public DecayingCobwebBlock(Properties properties) {
        super(properties);
    }

    /**
     * One to three webs around a spot a shot landed: in the spot itself if it is open, and in open spots next to it.
     * Only air is replaced.
     */
    public static void spin(ServerLevel level, BlockPos centre) {
        RandomSource random = level.getRandom();
        List<BlockPos> spots = new ArrayList<>(7);
        for (Direction direction : Direction.values()) {
            spots.add(centre.relative(direction));
        }
        Util.shuffle(spots, random);
        spots.addFirst(centre);
        int webs = 1 + random.nextInt(MAX_WEBS);
        BlockState web = ModBlocks.DECAYING_COBWEB.defaultBlockState();
        for (BlockPos spot : spots) {
            if (webs <= 0) {
                break;
            }
            if (level.getBlockState(spot).isAir()) {
                level.setBlockAndUpdate(spot, web);
                webs--;
            }
        }
        level.playSound(null, centre, web.getSoundType().getPlaceSound(), SoundSource.HOSTILE, 1.0F, 0.8F);
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
