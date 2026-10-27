package org.kennyerf.monster.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the Bomber's explosion size and fuse follow the config. */
@Mixin(Creeper.class)
public interface CreeperAccessor {
    @Accessor("explosionRadius")
    void monster$setExplosionRadius(int radius);

    @Accessor("maxSwell")
    void monster$setMaxSwell(int ticks);

    @Accessor("explosionRadius")
    int monster$getExplosionRadius();

    @Accessor("maxSwell")
    int monster$getMaxSwell();
}
