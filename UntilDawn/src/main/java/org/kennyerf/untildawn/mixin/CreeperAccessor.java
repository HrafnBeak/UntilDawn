package org.kennyerf.untildawn.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the Bomber's explosion size and fuse follow the config. */
@Mixin(Creeper.class)
public interface CreeperAccessor {
    @Accessor("explosionRadius")
    void untildawn$setExplosionRadius(int radius);

    @Accessor("maxSwell")
    void untildawn$setMaxSwell(int ticks);

    @Accessor("explosionRadius")
    int untildawn$getExplosionRadius();

    @Accessor("maxSwell")
    int untildawn$getMaxSwell();

    @Accessor("swell")
    int untildawn$getSwell();
}
