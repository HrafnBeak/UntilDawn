package org.kennyerf.untildawn.squad;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.entity.WaveMob;
import org.kennyerf.untildawn.pathing.MobPath;
import org.kennyerf.untildawn.pathing.PathAbilities;
import org.kennyerf.untildawn.pathing.PathStep;
import org.kennyerf.untildawn.pathing.SquadSupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A group of wave mobs that share a goal and talk to each other. All communication is local to the squad:
 * sightings, requests for help, claims on work in progress, the work on workers' routes and Bombers' breach warnings
 * reach squadmates only. A worker whose route (and the work on it) changes tells the squad, so squadmates who would
 * otherwise need help of their own re-plan to follow it, or to use what it has built. Membership changes as mobs drift apart or meet other squads (see
 * {@link Squads}).
 */
public final class Squad {
    private static final int SIGHTING_TICKS = 200;
    private static final int CLAIM_TICKS = 120;
    private static final int REQUEST_TICKS = 1200;
    /** A Bomber answers a dig request a Miner hasn't taken by then (straight away if there is no Miner). */
    private static final int BOMBER_DIG_DELAY = 40;
    private static final int CROWD_LOOKAHEAD = 6;

    private record Claim(UUID owner, long expires) {
    }

    /** The kinds of worker; squads like to have one of each rather than two of one. */
    public enum Worker {
        MINER, BUILDER, BOMBER;

        public static @Nullable Worker of(Mob mob) {
            PathAbilities abilities = abilities(mob);
            if (abilities.explosive()) {
                return BOMBER;
            }
            if (abilities.canBreak()) {
                return MINER;
            }
            return abilities.canPlace() ? BUILDER : null;
        }
    }

    private static int nextId = 1;

    private final int id;
    private final ServerLevel level;
    private final List<Mob> members = new ArrayList<>();
    private final Map<UUID, Long> sightings = new HashMap<>();
    private final List<WorkRequest> requests = new ArrayList<>();
    private final List<BreachIntent> breaches = new ArrayList<>();
    private final Long2ObjectOpenHashMap<Claim> breakClaims = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Claim> placeClaims = new Long2ObjectOpenHashMap<>();
    /** Cell to the entity id standing in it or about to walk through it. */
    /** Shared by every squad: mobs avoid cells any mob stands on or is heading for, not just squadmates'. */
    private final Long2IntOpenHashMap crowd = Squads.CROWD;
    /** Blocks the workers' routes will break or place further on than their claims reach, to the worker's entity id. */
    private final Long2IntOpenHashMap plannedBreaks = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap plannedPlaces = new Long2IntOpenHashMap();
    private @Nullable UUID goalPlayer;
    private Vec3 centre = Vec3.ZERO;
    /** Which of {@link Squads#SIDES} the squad attacks the Core from, or -1 for any. */
    private int attackSide = -1;

    Squad(ServerLevel level) {
        this.id = nextId++;
        this.level = level;
    }

    public int id() {
        return id;
    }

    public ServerLevel level() {
        return level;
    }

    public List<Mob> members() {
        return Collections.unmodifiableList(members);
    }

    public Vec3 centre() {
        return centre;
    }

    public List<WorkRequest> requests() {
        return Collections.unmodifiableList(requests);
    }

    /** Which of {@link Squads#SIDES} the squad attacks the Core from, or -1 for any. */
    public int attackSide() {
        return attackSide;
    }

    void setAttackSide(int side) {
        attackSide = side;
    }

    /** How many of each kind of {@link Worker} the squad has, indexed by ordinal. */
    int[] workers() {
        int[] counts = new int[Worker.values().length];
        for (Mob member : members) {
            Worker worker = Worker.of(member);
            if (worker != null && member.isAlive()) {
                counts[worker.ordinal()]++;
            }
        }
        return counts;
    }

    void add(Mob mob) {
        members.add(mob);
    }

    void remove(Mob mob) {
        members.remove(mob);
        releaseAll(mob);
        for (WorkRequest request : requests) {
            request.waiting().remove(mob.getUUID());
            if (mob.getUUID().equals(request.responder())) {
                request.setResponder(null);
            }
        }
    }

    /** Takes over another squad's knowledge and open requests when merging. */
    void absorb(Squad other) {
        other.sightings.forEach((player, time) -> sightings.merge(player, time, Math::max));
        requests.addAll(other.requests);
        breaches.addAll(other.breaches);
    }

    private static PathAbilities abilities(Mob mob) {
        return mob instanceof WaveMob wave ? wave.pathAbilities() : PathAbilities.WALKER;
    }

    // ---------------------------------------------------------------- shared goal and sightings

    public void reportSighting(Player player) {
        sightings.put(player.getUUID(), level.getGameTime());
    }

    /** True if a squadmate has seen this player recently. */
    public boolean knows(Player player) {
        Long seen = sightings.get(player.getUUID());
        return seen != null && level.getGameTime() - seen <= SIGHTING_TICKS;
    }

    /** The player most of the squad is going after, if any: the squad's shared goal. */
    public @Nullable Player goalPlayer() {
        if (goalPlayer == null) {
            return null;
        }
        Player player = level.getPlayerByUUID(goalPlayer);
        return player != null && player.isAlive() && !player.isSpectator() && !player.isCreative() ? player : null;
    }

    // ---------------------------------------------------------------- what squadmates can do for one mob

    /** The planner's view of this squad, from the point of view of one member. */
    public SquadSupport support(Mob mob) {
        boolean dig = false, blast = false, build = false, followers = false;
        for (Mob member : members) {
            if (member == mob || !member.isAlive()) {
                continue;
            }
            PathAbilities abilities = abilities(member);
            dig |= abilities.canBreak() && !abilities.explosive();
            blast |= abilities.explosive();
            build |= abilities.canPlace();
            followers |= !abilities.canPlace() && !abilities.climbWalls();
        }
        boolean canDig = dig, canBlast = blast, canBuild = build, hasFollowers = followers;
        UUID self = mob.getUUID();
        int selfId = mob.getId();
        return new SquadSupport() {
            @Override
            public boolean canAssistDig() {
                return canDig;
            }

            @Override
            public boolean canAssistBlast() {
                return canBlast;
            }

            @Override
            public boolean canAssistBuild() {
                return canBuild;
            }

            @Override
            public boolean hasFollowers() {
                return hasFollowers;
            }

            @Override
            public boolean isBreakPending(long pos) {
                return claimedByOther(breakClaims, pos, self) || requested(WorkRequest.Kind.DIG, pos)
                        || plannedBreaks.getOrDefault(pos, selfId) != selfId;
            }

            @Override
            public boolean isPlacePending(long pos) {
                return claimedByOther(placeClaims, pos, self) || requested(WorkRequest.Kind.BUILD, pos)
                        || plannedPlaces.getOrDefault(pos, selfId) != selfId;
            }

            @Override
            public boolean isCrowded(long pos) {
                int occupant = crowd.getOrDefault(pos, selfId);
                return occupant != selfId;
            }
        };
    }

    private boolean requested(WorkRequest.Kind kind, long pos) {
        for (WorkRequest request : requests) {
            if (!request.isClosed() && request.kind() == kind && request.cells().contains(pos)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- requests for help

    /** Asks the squad for help with some work, or joins an existing request for the same work. */
    public WorkRequest request(WorkRequest.Kind kind, List<PathStep> segment, Mob asker) {
        LongSet cells = WorkRequest.cellsOf(kind, segment);
        for (WorkRequest request : requests) {
            if (!request.isClosed() && request.kind() == kind && request.overlaps(cells)) {
                request.waiting().add(asker.getUUID());
                return request;
            }
        }
        WorkRequest request = new WorkRequest(kind, segment, asker.getUUID(), level.getGameTime());
        requests.add(request);
        return request;
    }

    /** The asker no longer needs the work; the request closes once nobody does. */
    public void withdraw(WorkRequest request, Mob asker) {
        request.waiting().remove(asker.getUUID());
        if (request.waiting().isEmpty()) {
            request.close();
        }
    }

    /** The work can't be done as asked (the way it covers has been built into since): the askers plan again. */
    public void cancel(WorkRequest request) {
        request.close();
    }

    /** A responder stops working on a request; if the work isn't done, another squadmate can take it on. */
    public void dropJob(WorkRequest request, Mob responder) {
        if (responder.getUUID().equals(request.responder())) {
            request.setResponder(null);
        }
    }

    /** A specialist looks for an unanswered request it can do, nearest first, and takes it on. */
    public @Nullable WorkRequest offerJob(Mob mob) {
        PathAbilities abilities = abilities(mob);
        if (!abilities.canBreak() && !abilities.canPlace()) {
            return null;
        }
        double talkRange = UntilDawnConfig.SQUAD_TALK_RANGE.get();
        long now = level.getGameTime();
        WorkRequest best = null;
        double bestDistance = talkRange * talkRange;
        for (WorkRequest request : requests) {
            if (request.isClosed() || request.responder() != null || request.waiting().contains(mob.getUUID())) {
                continue;
            }
            boolean able = switch (request.kind()) {
                case BUILD -> abilities.canPlace();
                case DIG -> abilities.canBreak() && (!abilities.explosive()
                        || (now - request.created() >= BOMBER_DIG_DELAY || !hasMiner()) && request.isBlastable(level));
            };
            if (!able) {
                continue;
            }
            double distance = mob.distanceToSqr(Vec3.atCenterOf(request.site()));
            if (distance <= bestDistance) {
                best = request;
                bestDistance = distance;
            }
        }
        if (best != null) {
            best.setResponder(mob.getUUID());
        }
        return best;
    }

    private boolean hasMiner() {
        for (Mob member : members) {
            PathAbilities abilities = abilities(member);
            if (member.isAlive() && abilities.canBreak() && !abilities.explosive()) {
                return true;
            }
        }
        return false;
    }

    /** An open dig request nobody has answered for a while: a job for a passing Bomber. */
    boolean needsBlasting() {
        long now = level.getGameTime();
        for (WorkRequest request : requests) {
            if (!request.isClosed() && request.kind() == WorkRequest.Kind.DIG && request.responder() == null
                    && now - request.created() >= BOMBER_DIG_DELAY && request.isBlastable(level)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- breach warnings

    public void announceBreach(Mob bomber, BlockPos centre, LongSet cells, double radius) {
        breaches.removeIf(breach -> breach.bomber().equals(bomber.getUUID()));
        breaches.add(new BreachIntent(bomber.getUUID(), centre.immutable(), new LongOpenHashSet(cells), radius, level.getGameTime() + 200));
    }

    /** Breaches about to happen: the Bomber is still alive and the warning hasn't run out. */
    public List<BreachIntent> activeBreaches() {
        long now = level.getGameTime();
        breaches.removeIf(breach -> breach.expires() < now
                || !(level.getEntity(breach.bomber()) instanceof Mob bomber) || !bomber.isAlive());
        return Collections.unmodifiableList(breaches);
    }

    /** A squadmate's route passes through this cell soon, so nothing should be built in it. Squadmates making way don't count. */
    public boolean isOnRouteOfOther(BlockPos pos, Mob mob) {
        for (Mob member : members) {
            if (member == mob || !member.isAlive() || !(member instanceof WaveMob wave) || wave.pathController().isYielding()) {
                continue;
            }
            MobPath path = wave.pathController().path();
            if (path == null) {
                continue;
            }
            int from = wave.pathController().index();
            for (int i = from; i < Math.min(path.steps().size(), from + CROWD_LOOKAHEAD); i++) {
                BlockPos step = path.steps().get(i).pos();
                if (step.equals(pos) || step.above().equals(pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- workers' routes

    /** A squadmate worker's route will break this block, or build in this cell, on its way. */
    public boolean isPlannedByOther(BlockPos pos, Mob mob) {
        int id = mob.getId();
        long key = pos.asLong();
        return plannedBreaks.getOrDefault(key, id) != id || plannedPlaces.getOrDefault(key, id) != id;
    }

    /**
     * A worker has planned a new route, or finished or dropped one with work on it: its work is noted for the squad, and
     * squadmates whose own routes need work done that nobody has taken on or planned re-plan, to follow the worker's route
     * (or use what it built) where they can rather than ask for help of their own.
     */
    public void routeChanged(Mob worker) {
        updateRoutes();
        for (Mob member : members) {
            if (member != worker && member.isAlive() && member instanceof WaveMob wave) {
                wave.pathController().reconsiderIfUnaided(this);
            }
        }
    }

    private void updateRoutes() {
        plannedBreaks.clear();
        plannedPlaces.clear();
        for (Mob mob : members) {
            if (!(mob instanceof WaveMob wave) || !mob.isAlive()) {
                continue;
            }
            PathAbilities abilities = wave.pathAbilities();
            MobPath path = wave.pathController().path();
            if (path == null || !(abilities.canBreak() || abilities.canPlace())) {
                continue;
            }
            for (int i = wave.pathController().index(); i < path.steps().size(); i++) {
                PathStep step = path.steps().get(i);
                if (abilities.canBreak()) {
                    step.breaks().forEach(pos -> plannedBreaks.putIfAbsent(pos.asLong(), mob.getId()));
                }
                if (abilities.canPlace()) {
                    step.places().forEach(pos -> plannedPlaces.putIfAbsent(pos.asLong(), mob.getId()));
                }
            }
        }
    }

    // ---------------------------------------------------------------- claims on work in progress

    public boolean claimBreak(BlockPos pos, Mob mob) {
        return claim(breakClaims, pos, mob);
    }

    public boolean claimPlace(BlockPos pos, Mob mob) {
        return claim(placeClaims, pos, mob);
    }

    public boolean isBreakClaimedByOther(BlockPos pos, Mob mob) {
        return claimedByOther(breakClaims, pos.asLong(), mob.getUUID());
    }

    public boolean isPlaceClaimedByOther(BlockPos pos, Mob mob) {
        return claimedByOther(placeClaims, pos.asLong(), mob.getUUID());
    }

    public void release(BlockPos pos) {
        breakClaims.remove(pos.asLong());
        placeClaims.remove(pos.asLong());
    }

    public void releaseAll(Mob mob) {
        breakClaims.values().removeIf(claim -> claim.owner().equals(mob.getUUID()));
        placeClaims.values().removeIf(claim -> claim.owner().equals(mob.getUUID()));
    }

    private boolean claim(Long2ObjectOpenHashMap<Claim> claims, BlockPos pos, Mob mob) {
        long key = pos.asLong();
        if (claimedByOther(claims, key, mob.getUUID())) {
            return false;
        }
        claims.put(key, new Claim(mob.getUUID(), level.getGameTime() + CLAIM_TICKS));
        return true;
    }

    private boolean claimedByOther(Long2ObjectOpenHashMap<Claim> claims, long key, UUID self) {
        Claim claim = claims.get(key);
        if (claim == null || claim.owner().equals(self)) {
            return false;
        }
        if (claim.expires() < level.getGameTime() || !(level.getEntity(claim.owner()) instanceof Mob owner) || !owner.isAlive()) {
            claims.remove(key);
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- upkeep (from Squads)

    /** Drops dead members and stale knowledge, closes finished requests, and re-picks the shared goal. */
    void prune() {
        members.removeIf(mob -> mob.isRemoved() || !mob.isAlive());
        long now = level.getGameTime();
        sightings.values().removeIf(time -> now - time > SIGHTING_TICKS);
        for (WorkRequest request : requests) {
            UUID responder = request.responder();
            if (responder != null && !(level.getEntity(responder) instanceof Mob mob && mob.isAlive() && members.contains(mob))) {
                request.setResponder(null);
            }
            request.waiting().removeIf(asker -> !(level.getEntity(asker) instanceof Mob mob) || !mob.isAlive());
            if (request.waiting().isEmpty() || now - request.created() > REQUEST_TICKS || request.isDone(level)) {
                request.close();
            }
        }
        requests.removeIf(WorkRequest::isClosed);
        activeBreaches();

        double x = 0, y = 0, z = 0;
        Map<UUID, Integer> targets = new HashMap<>();
        for (Mob mob : members) {
            x += mob.getX();
            y += mob.getY();
            z += mob.getZ();
            if (mob.getTarget() instanceof Player player) {
                targets.merge(player.getUUID(), 1, Integer::sum);
            }
        }
        if (!members.isEmpty()) {
            centre = new Vec3(x / members.size(), y / members.size(), z / members.size());
        }
        goalPlayer = targets.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    /** Records where everyone is and where they are about to step, and the work left on the workers' routes. */
    void updateCrowd() {
        updateRoutes();
    }

    /** Marks the cells this squad's mobs stand on in the crowd map shared by all squads. */
    void recordStanding(Long2IntOpenHashMap crowd) {
        for (Mob mob : members) {
            crowd.put(mob.blockPosition().asLong(), mob.getId());
        }
    }

    /** Marks the cells this squad's mobs are about to step on, unless someone already stands or is heading there. */
    void recordHeading(Long2IntOpenHashMap crowd) {
        for (Mob mob : members) {
            if (mob instanceof WaveMob wave) {
                MobPath path = wave.pathController().path();
                if (path != null) {
                    int from = wave.pathController().index();
                    for (int i = from; i < Math.min(path.steps().size(), from + CROWD_LOOKAHEAD); i++) {
                        crowd.putIfAbsent(path.steps().get(i).pos().asLong(), mob.getId());
                    }
                }
            }
        }
    }

    boolean isBusy(Mob mob) {
        return mob instanceof WaveMob wave && wave.pathController().hasJob();
    }

    @Override
    public String toString() {
        return "squad #" + id + " (" + members.size() + ")";
    }
}
