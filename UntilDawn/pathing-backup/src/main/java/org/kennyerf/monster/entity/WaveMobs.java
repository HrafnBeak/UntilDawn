package org.kennyerf.monster.entity;

import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.kennyerf.monster.config.MonsterConfig;
import org.kennyerf.monster.mixin.CreeperAccessor;

public final class WaveMobs {
    private WaveMobs() {
    }

    /** Gives a mob its fixed kit. The kit never drops, and the mob won't swap it for loot. */
    static void equip(Mob mob, ItemStack mainHand, ItemStack head) {
        mob.setItemSlot(EquipmentSlot.MAINHAND, mainHand);
        mob.setItemSlot(EquipmentSlot.HEAD, head);
        mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
        mob.setCanPickUpLoot(false);
    }

    /** Applies the current config (speed, health, damage, explosion, bow range) to a loaded mob. */
    @SuppressWarnings("unchecked")
    public static void applyConfig(Mob mob) {
        if (!(mob instanceof WaveMob wave)) {
            return;
        }
        MonsterConfig.MobSettings settings = wave.settings();
        AttributeSupplier defaults = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) mob.getType());

        scale(mob, defaults, Attributes.MOVEMENT_SPEED, settings.speed.get());

        // Keep the same fraction of health, so a fresh mob spawns at full (new) health.
        float oldMax = mob.getMaxHealth();
        scale(mob, defaults, Attributes.MAX_HEALTH, settings.health.get());
        float newMax = mob.getMaxHealth();
        if (newMax != oldMax && oldMax > 0) {
            mob.setHealth(mob.getHealth() * newMax / oldMax);
        }

        // Skeletons' damage is their arrows (see ArcherSkeleton); zombies use the attack damage attribute.
        if (settings.damage != null && !(mob instanceof ArcherSkeleton)) {
            scale(mob, defaults, Attributes.ATTACK_DAMAGE, settings.damage.get());
        }
        if (mob instanceof BomberCreeper bomber) {
            ((CreeperAccessor) bomber).monster$setExplosionRadius(MonsterConfig.BOMBER_EXPLOSION_RADIUS.get());
            ((CreeperAccessor) bomber).monster$setMaxSwell(MonsterConfig.BOMBER_FUSE.get());
        }
        if (mob instanceof ArcherSkeleton archer) {
            archer.reassessWeaponGoal();
        }
    }

    /** Re-applies the config to every loaded wave mob, after a setting changed. */
    public static int applyConfigToAll(MinecraftServer server) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), mob -> mob instanceof WaveMob)) {
                applyConfig(mob);
                count++;
            }
        }
        return count;
    }

    private static void scale(Mob mob, AttributeSupplier defaults, Holder<Attribute> attribute, double multiplier) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null && defaults.hasAttribute(attribute)) {
            instance.setBaseValue(defaults.getBaseValue(attribute) * multiplier);
        }
    }
}
