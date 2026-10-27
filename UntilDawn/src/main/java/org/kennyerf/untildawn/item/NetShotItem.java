package org.kennyerf.untildawn.item;

import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.entity.NetShot;

/** Ammo for any crossbow that takes arrows (it is in the arrows tag); it fires a {@link NetShot}. */
public class NetShotItem extends ArrowItem {
    /** What the crossbows that don't take Net Shots (the Repeater and the Chain) load: arrows, but not these. */
    public static final Predicate<ItemStack> ARROWS_WITHOUT_NETS = stack -> ProjectileWeaponItem.ARROW_ONLY.test(stack) && !(stack.getItem() instanceof NetShotItem);

    public NetShotItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity owner, @Nullable ItemStack weapon) {
        return new NetShot(level, owner, ammo.copyWithCount(1), weapon);
    }

    @Override
    public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
        NetShot shot = new NetShot(level, position.x(), position.y(), position.z(), stack.copyWithCount(1), null);
        shot.pickup = AbstractArrow.Pickup.ALLOWED;
        return shot;
    }
}
