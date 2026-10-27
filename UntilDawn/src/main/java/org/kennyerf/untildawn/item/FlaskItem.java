package org.kennyerf.untildawn.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A flask thrown like a snowball; the entity it throws decides what happens when it breaks. */
public class FlaskItem extends Item {
    private final Projectile.ProjectileFactory<? extends Projectile> thrown;

    public FlaskItem(Properties properties, Projectile.ProjectileFactory<? extends Projectile> thrown) {
        super(properties);
        this.thrown = thrown;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.NEUTRAL,
                0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (level instanceof ServerLevel serverLevel) {
            Projectile.spawnProjectileFromRotation(thrown, serverLevel, stack, player, 0.0F, 0.5F, 1.0F);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
