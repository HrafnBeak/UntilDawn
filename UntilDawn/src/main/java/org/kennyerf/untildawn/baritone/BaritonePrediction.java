package org.kennyerf.untildawn.baritone;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.pathing.MobPath;
import org.kennyerf.untildawn.pathing.PathGoal;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Asks Baritone for paths: how something carrying a given inventory would get from one block to another
 * ({@code /untildawn path predict}), and Sprinters' routes. Baritone is optional: check {@link #isAvailable()} first.
 * Only {@link BaritonePlanner} touches Baritone's classes, so nothing else fails to load without it.
 */
public final class BaritonePrediction {
    /**
     * @param path    the predicted path in the mod's own form, for {@link org.kennyerf.untildawn.debug.PathDebug}; null if
     *                Baritone found none
     * @param summary what to tell the player
     */
    public record Prediction(@Nullable MobPath path, List<Component> summary) {
    }

    private BaritonePrediction() {
    }

    /** Baritone runs inside the client, so it can plan on the integrated server of singleplayer and LAN worlds only. */
    public static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded("baritone")
                && FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    /** Must be called on the server thread; completes there. */
    public static CompletableFuture<Prediction> predict(ServerLevel level, BlockPos from, BlockPos to, List<ItemStack> items) {
        if (!isAvailable()) {
            throw new IllegalStateException("Baritone is not available");
        }
        return BaritonePlanner.predict(level, from, to, items);
    }

    /**
     * Plans a Sprinter's route: walking and sprint-jumping gaps, no digging or building. Must be called on the server
     * thread; completes there, with null if Baritone found no path.
     */
    public static CompletableFuture<@Nullable MobPath> planSprinterPath(ServerLevel level, BlockPos from, PathGoal goal) {
        if (!isAvailable()) {
            throw new IllegalStateException("Baritone is not available");
        }
        return BaritonePlanner.planSprinterPath(level, from, goal);
    }

    /**
     * Plans a wave spider's route: walking and climbing walls, no digging or building. Must be called on the server
     * thread; completes there, with null if Baritone found no path.
     */
    public static CompletableFuture<@Nullable MobPath> planSpiderPath(ServerLevel level, BlockPos from, PathGoal goal) {
        if (!isAvailable()) {
            throw new IllegalStateException("Baritone is not available");
        }
        return BaritonePlanner.planSpiderPath(level, from, goal);
    }
}
