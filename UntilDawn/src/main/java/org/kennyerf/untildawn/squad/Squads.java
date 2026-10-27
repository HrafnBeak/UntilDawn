package org.kennyerf.untildawn.squad;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.entity.WaveMob;
import org.kennyerf.untildawn.game.WaveManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps track of every squad. Waves spawn mobs as ready-made squads; after that, membership follows proximity:
 * a mob that drifts too far from its squad goes solo, and squads (solo mobs included) that come close merge, up to
 * {@code squads.maxSize} and preferring merges that don't double up a kind of worker. A squad with two of a kind of
 * worker hands one to a nearby squad without, and Bombers with nothing to blast transfer to a nearby squad that is stuck
 * waiting for a wall to come down, so the workforce spreads out. Squads take different sides of the Core to attack from.
 * Squads are not saved; after a restart every mob starts solo and they regroup within a second.
 */
public final class Squads {
    /** Directions from the Core, as x and z steps, that squads attack it from: four sides and four corners, in turn round. */
    public static final int[][] SIDES = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

    private static final int CROWD_INTERVAL = 10;
    private static final int REGROUP_INTERVAL = 20;

    private static final List<Squad> SQUADS = new ArrayList<>();
    /** Cell to the id of the mob standing on it or heading for it, rebuilt every {@link #CROWD_INTERVAL} ticks. */
    static final Long2IntOpenHashMap CROWD = new Long2IntOpenHashMap();
    private static final Map<UUID, Squad> BY_MOB = new HashMap<>();

    private Squads() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Squads::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SQUADS.clear();
            CROWD.clear();
            BY_MOB.clear();
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof WaveMob wave && wave.joinsSquads() && entity instanceof Mob mob && !BY_MOB.containsKey(mob.getUUID())) {
                join(mob, newSquad(level));
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof Mob mob) {
                leave(mob);
            }
        });
    }

    public static @Nullable Squad of(Mob mob) {
        return BY_MOB.get(mob.getUUID());
    }

    public static List<Squad> all() {
        return Collections.unmodifiableList(SQUADS);
    }

    /** Puts these mobs together in a fresh squad, taking them out of whatever squad they were in. Loners are left out. */
    public static Squad form(ServerLevel level, List<? extends Mob> mobs) {
        Squad squad = newSquad(level);
        for (Mob mob : mobs) {
            if (mob instanceof WaveMob wave && !wave.joinsSquads()) {
                continue;
            }
            leave(mob);
            join(mob, squad);
        }
        return squad;
    }

    /**
     * Forms these mobs into as few squads of at most {@code maxSize} as will do, dealing each kind of worker out across
     * them rather than putting two together.
     */
    public static List<Squad> formSpread(ServerLevel level, List<? extends Mob> mobs, int maxSize) {
        int count = Math.max(1, (mobs.size() + maxSize - 1) / maxSize);
        List<List<Mob>> parts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            parts.add(new ArrayList<>());
        }
        // Workers first, so they are dealt out before the fighters even the squads up.
        List<Mob> order = new ArrayList<>(mobs);
        order.sort(Comparator.comparing((Mob mob) -> Squad.Worker.of(mob) == null));
        for (Mob mob : order) {
            Squad.Worker worker = Squad.Worker.of(mob);
            List<Mob> best = null;
            long bestSame = 0;
            for (List<Mob> part : parts) {
                if (part.size() >= maxSize) {
                    continue;
                }
                long same = worker == null ? 0 : part.stream().filter(member -> Squad.Worker.of(member) == worker).count();
                if (best == null || same < bestSame || same == bestSame && part.size() < best.size()) {
                    best = part;
                    bestSame = same;
                }
            }
            best.add(mob);
        }
        return parts.stream().map(part -> form(level, part)).toList();
    }

    private static Squad newSquad(ServerLevel level) {
        Squad squad = new Squad(level);
        SQUADS.add(squad);
        return squad;
    }

    private static void join(Mob mob, Squad squad) {
        squad.add(mob);
        BY_MOB.put(mob.getUUID(), squad);
    }

    private static void leave(Mob mob) {
        Squad squad = BY_MOB.remove(mob.getUUID());
        if (squad != null) {
            squad.remove(mob);
        }
    }

    private static void move(Mob mob, Squad to) {
        leave(mob);
        join(mob, to);
    }

    // ---------------------------------------------------------------- ticking

    private static void tick(MinecraftServer server) {
        if (SQUADS.isEmpty()) {
            return;
        }
        int tick = server.getTickCount();
        if (tick % REGROUP_INTERVAL == 0) {
            regroup();
        }
        if (tick % CROWD_INTERVAL == 0) {
            SQUADS.forEach(Squad::updateCrowd);
            // Standing mobs claim their cell before anyone's planned steps, across all squads.
            CROWD.clear();
            SQUADS.forEach(squad -> squad.recordStanding(CROWD));
            SQUADS.forEach(squad -> squad.recordHeading(CROWD));
        }
    }

    static void regroup() {
        for (Squad squad : SQUADS) {
            squad.prune();
        }
        SQUADS.removeIf(squad -> squad.members().isEmpty());

        // Stragglers go solo.
        double leave = UntilDawnConfig.SQUAD_LEAVE_RADIUS.get();
        for (Squad squad : List.copyOf(SQUADS)) {
            if (squad.members().size() < 2) {
                continue;
            }
            for (Mob mob : List.copyOf(squad.members())) {
                if (mob.position().distanceToSqr(squad.centre()) > leave * leave && !squad.isBusy(mob)) {
                    Squad solo = newSquad(squad.level());
                    move(mob, solo);
                    solo.setAttackSide(squad.attackSide());
                    solo.prune();
                }
            }
            squad.prune();
        }

        // Squads that meet merge, the smaller into the bigger, as long as that doesn't double up a kind of worker (two
        // squads with a Builder each can both build); then a mob still on its own joins whoever it meets.
        double join = UntilDawnConfig.SQUAD_JOIN_RADIUS.get();
        int maxSize = UntilDawnConfig.SQUAD_MAX_SIZE.get();
        for (boolean diverseOnly : new boolean[]{true, false}) {
            List<Squad> bySize = new ArrayList<>(SQUADS);
            bySize.sort(Comparator.comparingInt((Squad s) -> s.members().size()).reversed());
            for (int i = 0; i < bySize.size(); i++) {
                Squad big = bySize.get(i);
                for (int j = i + 1; j < bySize.size(); j++) {
                    Squad small = bySize.get(j);
                    if (big.members().isEmpty() || small.members().isEmpty() || big.level() != small.level()
                            || big.members().size() + small.members().size() > maxSize
                            || big.centre().distanceToSqr(small.centre()) > join * join
                            || (diverseOnly ? doublesUpWorkers(big, small) : small.members().size() > 1)) {
                        continue;
                    }
                    big.absorb(small);
                    for (Mob mob : List.copyOf(small.members())) {
                        move(mob, big);
                    }
                    big.prune();
                }
            }
            SQUADS.removeIf(squad -> squad.members().isEmpty());
        }

        shareWorkers(maxSize);
        lendBombers(maxSize);
        assignSides();
    }

    /** Both squads have a worker of the same kind. */
    private static boolean doublesUpWorkers(Squad a, Squad b) {
        int[] first = a.workers();
        int[] second = b.workers();
        for (int i = 0; i < first.length; i++) {
            if (first[i] > 0 && second[i] > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * A squad with two or more of a kind of worker hands an idle one to the nearest squad that has none, if it is close
     * enough to that squad to stay in it.
     */
    private static void shareWorkers(int maxSize) {
        double leave = UntilDawnConfig.SQUAD_LEAVE_RADIUS.get();
        for (Squad from : List.copyOf(SQUADS)) {
            int[] have = from.workers();
            for (Squad.Worker worker : Squad.Worker.values()) {
                if (have[worker.ordinal()] < 2) {
                    continue;
                }
                Squad to = null;
                Mob mob = null;
                double best = leave * leave;
                for (Squad other : SQUADS) {
                    if (other == from || other.level() != from.level() || other.members().size() >= maxSize
                            || other.workers()[worker.ordinal()] > 0) {
                        continue;
                    }
                    for (Mob member : from.members()) {
                        double distance = member.position().distanceToSqr(other.centre());
                        if (Squad.Worker.of(member) == worker && !from.isBusy(member) && distance <= best) {
                            to = other;
                            mob = member;
                            best = distance;
                        }
                    }
                }
                if (mob != null) {
                    move(mob, to);
                    to.prune();
                    from.prune();
                    break;
                }
            }
        }
    }

    /**
     * Spreads squads round the Core so they come at it from different sides instead of bunching up with each other. Each
     * keeps its side unless an older squad has taken it, and otherwise takes the free side closest to it. A mob on its own
     * keeps to its old squad's side (or the one nearest it) without taking it from anyone.
     */
    private static void assignSides() {
        Map<ServerLevel, int[]> taken = new HashMap<>();
        List<Squad> byAge = new ArrayList<>(SQUADS);
        byAge.sort(Comparator.comparingInt(Squad::id));
        for (Squad squad : byAge) {
            BlockPos core = WaveManager.corePos(squad.level());
            if (core == null) {
                squad.setAttackSide(-1);
                continue;
            }
            Vec3 offset = squad.centre().subtract(Vec3.atCenterOf(core));
            int nearest = Math.floorMod((int) Math.round(Math.atan2(offset.z, offset.x) / (Math.PI / 4)), SIDES.length);
            int preferred = squad.attackSide() >= 0 ? squad.attackSide() : nearest;
            if (squad.members().size() < 2) {
                squad.setAttackSide(preferred);
                continue;
            }
            int[] counts = taken.computeIfAbsent(squad.level(), level -> new int[SIDES.length]);
            int best = preferred;
            int bestScore = Integer.MAX_VALUE;
            for (int side = 0; side < SIDES.length; side++) {
                int turn = Math.abs(side - preferred);
                // Fewer squads on a side always wins; among equals, the least far round.
                int score = counts[side] * SIDES.length + Math.min(turn, SIDES.length - turn);
                if (score < bestScore) {
                    best = side;
                    bestScore = score;
                }
            }
            counts[best]++;
            squad.setAttackSide(best);
        }
    }

    /** Bombers put supporting others first: an idle one joins a nearby squad that is stuck at a wall. */
    private static void lendBombers(int maxSize) {
        double talk = UntilDawnConfig.SQUAD_TALK_RANGE.get();
        for (Squad needy : List.copyOf(SQUADS)) {
            if (!needy.needsBlasting() || needy.members().size() >= maxSize) {
                continue;
            }
            for (Squad other : List.copyOf(SQUADS)) {
                if (other == needy || other.level() != needy.level()) {
                    continue;
                }
                Mob bomber = idleBomber(other, needy, talk);
                if (bomber != null) {
                    move(bomber, needy);
                    break;
                }
            }
        }
    }

    private static @Nullable Mob idleBomber(Squad from, Squad to, double talk) {
        if (from.needsBlasting()) {
            return null;
        }
        for (Mob mob : from.members()) {
            if (mob instanceof WaveMob wave && wave.pathAbilities().explosive() && !from.isBusy(mob)
                    && mob.position().distanceToSqr(to.centre()) <= talk * talk) {
                return mob;
            }
        }
        return null;
    }
}
