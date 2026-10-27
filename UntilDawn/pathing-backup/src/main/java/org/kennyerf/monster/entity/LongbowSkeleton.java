package org.kennyerf.monster.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.level.Level;
import org.kennyerf.monster.config.MonsterConfig;

/**
 * Skeleton with much more range: by default starts shooting at 48 blocks with faster, flatter,
 * more accurate arrows (see {@code longbowman.*} in the config).
 */
public class LongbowSkeleton extends ArcherSkeleton {
    public LongbowSkeleton(EntityType<? extends LongbowSkeleton> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSkeleton.createAttributes().add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    public MonsterConfig.MobSettings settings() {
        return MonsterConfig.LONGBOWMAN;
    }

    @Override
    protected float accuracyFactor() {
        return 0.5F;
    }
}
