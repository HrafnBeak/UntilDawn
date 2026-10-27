package org.kennyerf.monster.debug;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.kennyerf.monster.entity.WaveMob;
import org.kennyerf.monster.entity.ai.MobPathController;
import org.kennyerf.monster.pathing.MobPath;
import org.kennyerf.monster.pathing.PathStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Draws wave mobs' planned paths with particles, visible only to the players who asked for it.
 * <p>
 * Colours: green walk, yellow up/down/fall, blue blocks to place, red blocks to break,
 * white the step being worked on, gold the goal (purple if the path is only partial).
 */
public final class PathDebug {
    private static final double NEARBY_RANGE = 48.0;
    private static final int WALK = 0x55FF55;
    private static final int VERTICAL = 0xFFFF55;
    private static final int PLACE = 0x5599FF;
    private static final int BREAK = 0xFF3333;
    private static final int CURRENT = 0xFFFFFF;
    private static final int GOAL = 0xFFD700;
    private static final int PARTIAL = 0xAA55FF;

    /** Either every wave mob near the player, or a fixed set of mobs. */
    private record Watch(boolean nearby, Set<UUID> mobs) {
    }

    private static final Map<UUID, Watch> WATCHERS = new HashMap<>();

    private PathDebug() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(PathDebug::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WATCHERS.clear());
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

    public static boolean stop(ServerPlayer player) {
        return WATCHERS.remove(player.getUUID()) != null;
    }

    private static void tick(MinecraftServer server) {
        if (WATCHERS.isEmpty() || server.getTickCount() % 4 != 0) {
            return;
        }
        WATCHERS.entrySet().removeIf(entry -> server.getPlayerList().getPlayer(entry.getKey()) == null);
        for (Map.Entry<UUID, Watch> entry : WATCHERS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ServerLevel level = (ServerLevel) player.level();
            for (Mob mob : watchedMobs(level, player, entry.getValue())) {
                draw(level, player, mob);
            }
        }
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

    private static void draw(ServerLevel level, ServerPlayer player, Mob mob) {
        MobPathController controller = ((WaveMob) mob).pathController();
        MobPath path = controller.path();
        if (path == null) {
            // No path: a puff above the mob's head shows it is waiting for one.
            particle(level, player, ParticleTypes.SMOKE, mob.getX(), mob.getY() + mob.getBbHeight() + 0.4, mob.getZ());
            return;
        }
        List<PathStep> steps = path.steps();
        int from = Math.max(0, controller.index() - 1);
        for (int i = from; i < steps.size(); i++) {
            PathStep step = steps.get(i);
            Vec3 node = Vec3.atBottomCenterOf(step.pos()).add(0.0, 0.1, 0.0);
            int colour = i == controller.index() ? CURRENT : switch (step.type()) {
                case ASCEND, DESCEND, FALL, DIG_DOWN, PILLAR -> VERTICAL;
                default -> WALK;
            };
            dust(level, player, colour, node, 1.0F);
            if (i + 1 < steps.size()) {
                Vec3 next = Vec3.atBottomCenterOf(steps.get(i + 1).pos()).add(0.0, 0.1, 0.0);
                for (int s = 1; s < 3; s++) {
                    dust(level, player, colour, node.lerp(next, s / 3.0), 0.6F);
                }
            }
            for (BlockPos block : step.breaks()) {
                outline(level, player, block, BREAK);
            }
            for (BlockPos spot : step.places()) {
                outline(level, player, spot, PLACE);
            }
        }
        Vec3 end = Vec3.atBottomCenterOf(path.last().pos()).add(0.0, 0.6, 0.0);
        dust(level, player, path.reachesGoal() ? GOAL : PARTIAL, end, 2.0F);
        BlockPos mining = controller.miningPos();
        if (mining != null) {
            particle(level, player, ParticleTypes.CRIT, mining.getX() + 0.5, mining.getY() + 0.5, mining.getZ() + 0.5);
        }
    }

    private static void outline(ServerLevel level, ServerPlayer player, BlockPos pos, int colour) {
        for (int corner = 0; corner < 8; corner++) {
            double x = pos.getX() + ((corner & 1) == 0 ? 0.1 : 0.9);
            double y = pos.getY() + ((corner & 2) == 0 ? 0.1 : 0.9);
            double z = pos.getZ() + ((corner & 4) == 0 ? 0.1 : 0.9);
            dust(level, player, colour, new Vec3(x, y, z), 0.7F);
        }
    }

    private static void dust(ServerLevel level, ServerPlayer player, int colour, Vec3 pos, float scale) {
        particle(level, player, new DustParticleOptions(colour, scale), pos.x, pos.y, pos.z);
    }

    private static void particle(ServerLevel level, ServerPlayer player, ParticleOptions particle, double x, double y, double z) {
        level.sendParticles(player, particle, true, false, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }
}
