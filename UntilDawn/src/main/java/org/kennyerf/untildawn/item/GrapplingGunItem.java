package org.kennyerf.untildawn.item;

import net.minecraft.core.particles.ParticleTypes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Holding use hooks whatever the player is looking at, up to {@link #RANGE} blocks away, and keeps pulling the player toward
 * it until the button is released. Crouching when the hook lands on a mob pulls the mob toward the player instead, and
 * crouching on a block does nothing. The hook sticks to a mob as it moves, and lets go if the mob dies or gets too far.
 */
public class GrapplingGunItem extends Item {
    public static final double RANGE = 20.0;
    private static final double SLACK = 6.0;
    private static final double PULL = 0.16;
    private static final double MAX_SPEED = 1.3;
    private static final int COOLDOWN_TICKS = 10;

    /** What each holding player has hooked; the entry is dropped when they let go. */
    private static final Map<UUID, Hook> HOOKS = new HashMap<>();

    /** {@code target} is the hooked mob, or null for a block at {@code point}; {@code pullsTarget} says whose way the pull goes. */
    private record Hook(Entity target, Vec3 point, boolean pullsTarget) {
        Vec3 spot() {
            return target == null ? point : target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        }
    }

    public GrapplingGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(RANGE));

        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 reach = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult mob = ProjectileUtil.getEntityHitResult(player, eye, reach,
                player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0),
                e -> !e.isSpectator() && e.isPickable(), eye.distanceToSqr(reach));

        Entity target = mob == null ? null : mob.getEntity();
        boolean hitBlock = target == null && block.getType() != HitResult.Type.MISS;
        if ((target == null && !hitBlock) || (hitBlock && player.isShiftKeyDown())) {
            return InteractionResult.FAIL;
        }

        if (level instanceof ServerLevel) {
            HOOKS.put(player.getUUID(), new Hook(target, reach, target != null && player.isShiftKeyDown()));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.8F, 1.4F);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
        if (!(level instanceof ServerLevel serverLevel) || !(user instanceof Player player)) {
            return;
        }
        Hook hook = HOOKS.get(player.getUUID());
        if (hook == null || (hook.target() != null && !hook.target().isAlive())) {
            player.stopUsingItem();
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 spot = hook.spot();
        if (eye.distanceTo(spot) > RANGE + SLACK) {
            player.stopUsingItem();
            return;
        }
        if (hook.pullsTarget()) {
            pull(hook.target(), eye.subtract(spot), 2.0);
        } else {
            pull(player, spot.subtract(eye), 1.5);
        }
        if (player.tickCount % 2 == 0) {
            trail(serverLevel, eye.add(player.getViewVector(1.0F).scale(0.8)).subtract(0.0, 0.2, 0.0), spot);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int ticksRemaining) {
        HOOKS.remove(user.getUUID());
        if (user instanceof Player player) {
            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        }
        return true;
    }

    /** Speeds {@code entity} up along {@code to}, and holds it still once it is within {@code stopAt} blocks of the end. */
    private static void pull(Entity entity, Vec3 to, double stopAt) {
        double distance = to.length();
        Vec3 velocity = entity.getDeltaMovement();
        if (distance < stopAt) {
            velocity = velocity.scale(0.5);
        } else {
            velocity = velocity.add(to.scale(PULL / distance));
            if (velocity.length() > MAX_SPEED) {
                velocity = velocity.normalize().scale(MAX_SPEED);
            }
        }
        entity.setDeltaMovement(velocity);
        entity.fallDistance = 0.0;
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        } else {
            entity.needsSync = true;
        }
    }

    private static void trail(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 line = to.subtract(from);
        int steps = Math.max((int) (line.length() * 2.0), 1);
        for (int i = 0; i <= steps; i++) {
            Vec3 at = from.add(line.scale(i / (double) steps));
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
