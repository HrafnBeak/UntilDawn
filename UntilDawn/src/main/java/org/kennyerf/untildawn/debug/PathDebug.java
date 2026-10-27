package org.kennyerf.untildawn.debug;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.entity.WaveMob;
import org.kennyerf.untildawn.entity.ai.MobPathController;
import org.kennyerf.untildawn.pathing.MobPath;
import org.kennyerf.untildawn.pathing.PathStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shows wave mobs' planned paths, and Baritone's predictions ({@code /untildawn path predict}), to the players who asked
 * for them. The server only sends the paths ({@link PathOverlayPayload}); the player's client draws them with Baritone's
 * path renderer, so a client without Baritone can't show them (see {@link #canDraw}).
 */
public final class PathDebug {
    private static final double NEARBY_RANGE = 48.0;
    private static final int SEND_INTERVAL = 5;

    /** Either every wave mob near the player, or a fixed set of mobs. */
    private record Watch(boolean nearby, Set<UUID> mobs) {
    }

    private static final Map<UUID, Watch> WATCHERS = new HashMap<>();
    private static final Map<UUID, MobPath> PREDICTIONS = new HashMap<>();
    /** What each player's client was last sent, so unchanged paths aren't sent again. */
    private static final Map<UUID, List<PathOverlayPayload.Path>> SENT = new HashMap<>();

    private PathDebug() {
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(PathOverlayPayload.TYPE, PathOverlayPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(PathDebug::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            WATCHERS.clear();
            PREDICTIONS.clear();
            SENT.clear();
        });
    }

    /** The player's client has Baritone (and this mod) to draw paths with. */
    public static boolean canDraw(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, PathOverlayPayload.TYPE);
    }

    public static void watchNearby(ServerPlayer player) {
        WATCHERS.put(player.getUUID(), new Watch(true, Set.of()));
    }

    public static void watch(ServerPlayer player, List<Mob> mobs) {
        Watch existing = WATCHERS.get(player.getUUID());
        Set<UUID> ids = existing != null && !existing.nearby() ? new HashSet<>(existing.mobs()) : new HashSet<>();
        mobs.forEach(mob -> ids.add(mob.getUUID()));
        WATCHERS.put(player.getUUID(), new Watch(false, ids));
    }

    /** Replaces the player's last prediction; null just clears it. */
    public static void showPrediction(ServerPlayer player, @Nullable MobPath path) {
        if (path == null) {
            PREDICTIONS.remove(player.getUUID());
        } else {
            PREDICTIONS.put(player.getUUID(), path);
        }
    }

    public static boolean stop(ServerPlayer player) {
        boolean watching = WATCHERS.remove(player.getUUID()) != null;
        boolean predicting = PREDICTIONS.remove(player.getUUID()) != null;
        send(player, List.of());
        return watching || predicting;
    }

    private static void tick(MinecraftServer server) {
        if ((WATCHERS.isEmpty() && PREDICTIONS.isEmpty()) || server.getTickCount() % SEND_INTERVAL != 0) {
            return;
        }
        WATCHERS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        PREDICTIONS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        Set<UUID> viewers = new HashSet<>(WATCHERS.keySet());
        viewers.addAll(PREDICTIONS.keySet());
        for (UUID id : viewers) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            List<PathOverlayPayload.Path> paths = new ArrayList<>();
            Watch watch = WATCHERS.get(id);
            if (watch != null) {
                for (Mob mob : watchedMobs((ServerLevel) player.level(), player, watch)) {
                    MobPathController controller = ((WaveMob) mob).pathController();
                    if (controller.path() != null) {
                        paths.add(overlay(controller.path(), controller.index()));
                    }
                }
            }
            MobPath prediction = PREDICTIONS.get(id);
            if (prediction != null) {
                paths.add(overlay(prediction, 0));
            }
            send(player, paths);
        }
    }

    private static void send(ServerPlayer player, List<PathOverlayPayload.Path> paths) {
        if (!canDraw(player) || paths.equals(SENT.getOrDefault(player.getUUID(), List.of()))) {
            return;
        }
        ServerPlayNetworking.send(player, new PathOverlayPayload(paths));
        if (paths.isEmpty()) {
            SENT.remove(player.getUUID());
        } else {
            SENT.put(player.getUUID(), paths);
        }
    }

    /** The rest of the path from {@code current}, the step being worked on, with the step before it to draw from. */
    private static PathOverlayPayload.Path overlay(MobPath path, int current) {
        List<PathStep> steps = path.steps();
        List<BlockPos> positions = new ArrayList<>();
        List<BlockPos> breaks = new ArrayList<>();
        List<BlockPos> places = new ArrayList<>();
        for (int i = Math.max(0, current - 1); i < steps.size(); i++) {
            PathStep step = steps.get(i);
            positions.add(step.pos());
            breaks.addAll(step.breaks());
            places.addAll(step.places());
        }
        return new PathOverlayPayload.Path(positions, breaks, places, path.reachesGoal());
    }

    private static List<Mob> watchedMobs(ServerLevel level, ServerPlayer player, Watch watch) {
        if (watch.nearby()) {
            return level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(NEARBY_RANGE), mob -> mob instanceof WaveMob);
        }
        List<Mob> mobs = new ArrayList<>();
        for (UUID id : watch.mobs()) {
            if (level.getEntity(id) instanceof Mob mob && mob instanceof WaveMob && mob.isAlive()) {
                mobs.add(mob);
            }
        }
        return mobs;
    }
}
