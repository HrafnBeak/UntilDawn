package org.kennyerf.untildawn.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.kennyerf.untildawn.registry.ModBlocks;
import org.kennyerf.untildawn.registry.ModEntities;
import org.kennyerf.untildawn.registry.ModItems;

/** A Sapper Flask: it blows up where it breaks, hurting everything near, but it only breaks Builders' planks and ladders. */
public class ThrownSapperFlask extends ThrownFlask {
    private static final float POWER = 3.0F;

    /** Leaves every block alone except Unstable Planks and Unstable Ladders. */
    private static final ExplosionDamageCalculator ONLY_UNSTABLE = new ExplosionDamageCalculator() {
        @Override
        public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
            return state.is(ModBlocks.UNSTABLE_PLANKS) || state.is(ModBlocks.UNSTABLE_LADDER);
        }
    };

    public ThrownSapperFlask(EntityType<? extends ThrownSapperFlask> type, Level level) {
        super(type, level);
    }

    public ThrownSapperFlask(ServerLevel level, LivingEntity owner, ItemStack stack) {
        super(ModEntities.SAPPER_FLASK, level, owner, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.SAPPER_FLASK;
    }

    @Override
    protected void shatter(ServerLevel level, BlockPos at, Vec3 where) {
        level.explode(this, null, ONLY_UNSTABLE, where.x, where.y, where.z, POWER, false, Level.ExplosionInteraction.BLOCK);
    }
}
