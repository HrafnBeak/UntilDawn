package org.kennyerf.untildawn.client;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.pathing.entity.IEntityPathRenderer;
import baritone.api.pathing.entity.RenderedPath;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.BetterBlockPos;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.kennyerf.untildawn.debug.PathOverlayPayload;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the paths the server sends ({@link PathOverlayPayload}) with Baritone's path renderer, in the player's Baritone
 * colours: a path that reaches its goal like Baritone's current path, with a goal box at the end; one that only gets
 * as close as it can like Baritone's "best path so far". Loaded only when Baritone is installed.
 */
final class BaritonePathOverlay {
    private BaritonePathOverlay() {
    }

    static void init() {
        ClientPlayNetworking.registerGlobalReceiver(PathOverlayPayload.TYPE, (payload, context) -> show(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> renderer().setPaths(List.of()));
    }

    private static void show(PathOverlayPayload payload) {
        Settings settings = BaritoneAPI.getSettings();
        List<RenderedPath> paths = new ArrayList<>();
        for (PathOverlayPayload.Path path : payload.paths()) {
            if (path.positions().isEmpty()) {
                continue;
            }
            List<BetterBlockPos> positions = path.positions().stream().map(BetterBlockPos::new).toList();
            Color line = path.complete() ? settings.colorCurrentPath.value : settings.colorBestPathSoFar.value;
            Color goal = path.complete() ? settings.colorGoalBox.value : line;
            paths.add(new RenderedPath(positions, 0, path.breaks(), path.places(), new GoalBlock(positions.getLast()), line, goal));
        }
        renderer().setPaths(paths);
    }

    private static IEntityPathRenderer renderer() {
        return BaritoneAPI.getProvider().getEntityPathRenderer();
    }
}
