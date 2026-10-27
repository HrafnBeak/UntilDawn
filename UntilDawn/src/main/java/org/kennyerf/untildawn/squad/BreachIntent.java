package org.kennyerf.untildawn.squad;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A Bomber's announcement: "I'm about to blow this wall". Squadmates who planned to go through it, or who are
 * standing in the blast, hold back until it has gone off and then rush through.
 *
 * @param cells  the blocks the breach is meant to open
 * @param radius how far the blast reaches
 */
public record BreachIntent(UUID bomber, BlockPos centre, LongSet cells, double radius, long expires) {
    public boolean endangers(Vec3 pos, double margin) {
        return pos.distanceToSqr(Vec3.atCenterOf(centre)) < (radius + margin) * (radius + margin);
    }
}
