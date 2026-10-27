package org.kennyerf.untildawn.entity.ai;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.UntilDawn;
import org.kennyerf.untildawn.baritone.BaritonePrediction;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.entity.WaveMob;
import org.kennyerf.untildawn.pathing.AccessCorridors;
import org.kennyerf.untildawn.pathing.BlockView;
import org.kennyerf.untildawn.pathing.MobPath;
import org.kennyerf.untildawn.pathing.MobPathfinder;
import org.kennyerf.untildawn.pathing.MoveType;
import org.kennyerf.untildawn.pathing.PathAbilities;
import org.kennyerf.untildawn.pathing.PathCosts;
import org.kennyerf.untildawn.pathing.PathGoal;
import org.kennyerf.untildawn.pathing.PathStep;
import org.kennyerf.untildawn.pathing.SquadSupport;
import org.kennyerf.untildawn.registry.ModBlocks;
import org.kennyerf.untildawn.squad.BreachIntent;
import org.kennyerf.untildawn.squad.Squad;
import org.kennyerf.untildawn.squad.Squads;
import org.kennyerf.untildawn.squad.WorkRequest;
import org.kennyerf.untildawn.world.BlockDamage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Follows a {@link MobPath} step by step, doing the work each step needs first: digging (Miner), blasting
 * (Bomber), or placing Unstable Planks and ladders (Builder). A Builder puts in the plank a ladder hangs on before the
 * ladder, and climbing down it hangs each rung before it climbs onto it (stepping off an edge onto a ladder, it drops
 * straight onto it). A mob whose route ends on a ladder holds on there.
 * <p>
 * Work the mob can't do itself, it asks its squad for and waits; specialists take such requests on as jobs,
 * walking to the spot and following the asker's planned stretch of path to do the work. Work a squadmate's route will do
 * anyway it doesn't ask for: it waits for it (a step further back if someone already stands there), and when a worker's
 * route changes, mobs whose own routes need help nobody has planned re-plan to follow it or use what it built. Before a squadmate's
 * breach goes off, mobs whose route crosses it (or who stand in the blast) hold back, then rush through.
 * The mob re-plans when its target moves, it gets knocked off course, progress stalls, help doesn't come, or a Builder
 * puts a plank in its way (a job whose way has been built into is called off, so the asker plans again). A Miner
 * or Builder kept from its next task (the next block to dig or place, or the move after it) first asks squadmates to
 * back right off, then plans another way.
 * <p>
 * Mobs that can parkour (Sprinters) sprint-jump gaps. They plan with Baritone when it can run (singleplayer and LAN),
 * which also jumps them around the ends of walls in the way (neos); elsewhere their own planner plans the gap jumps.
 * Mobs that climb walls (the wave spiders) plan with Baritone the same way, and press themselves to the wall to climb.
 */
public class MobPathController {
    private static final int REPATH_COOLDOWN = 20;
    /** How many steps ahead a mob looks for other mobs in its way. */
    private static final int CROWD_LOOKAHEAD = 3;
    /** Wave mobs closer than this (in blocks) push each other apart as they walk. */
    private static final double SPREAD_RADIUS = 0.9;
    /** Sideways speed added per nudge, in blocks per tick. */
    private static final double SPREAD_SPEED = 0.05;
    /** Within this many blocks of its target a mob stops spreading out. */
    private static final double SPREAD_TARGET_RANGE = 3.0;
    private static final int STEP_TIMEOUT = 80;
    private static final int MAX_WAIT_TICKS = 160;
    /** How long to wait for a squadmate to take on a request, and to finish it. */
    private static final int HELP_ANSWER_TICKS = 100;
    private static final int HELP_DONE_TICKS = 600;
    private static final int AVOID_TICKS = 600;
    private static final int JOB_TICKS = 1200;
    private static final int CLAIM_LOOKAHEAD = 8;
    /** Longest stretch of path one request covers. */
    private static final int REQUEST_STEPS = 16;
    private static final int BREACH_LOOKAHEAD = 8;
    private static final int RUSH_TICKS = 60;
    private static final double SPEED = 1.0;
    private static final double RUSH_SPEED = 1.3;
    private static final double REACH = 4.5;
    /** How close to the centre of the take-off block a Sprinter must be to jump. */
    private static final double TAKE_OFF_SLACK = 0.3;
    /** Horizontal drag per tick in the air, and the fastest a leap may carry a mob. */
    private static final double AIR_DRAG = 0.91;
    private static final double MAX_LEAP_SPEED = 0.7;
    /** How long a leap may wait on the ground for its jump to happen. */
    private static final int LEAP_TAKE_OFF_TICKS = 6;
    /**
     * A neo's curve, relative to the take-off block's centre: how far out to the side it swings (the body must clear
     * the wall's corner), how close to the wall's ends the swing starts and finishes, and how far to the side of the
     * landing block's centre it aims (it arrives moving sideways).
     */
    private static final double NEO_SWING = 1.0;
    private static final double NEO_CORNER = 0.15;
    private static final double NEO_LANDING = 0.1;
    /** How close the mob must come to a point of the curve for it to count as passed, and its speed leaving the ground. */
    private static final double WAYPOINT_REACHED = 0.3;
    private static final double TAKE_OFF_SPEED = 0.2;
    /** How long a mob keeps out of the way after being asked to (the worker asks again every few ticks), and how far it looks for room. */
    private static final int YIELD_TICKS = 30;
    private static final int YIELD_RADIUS = 3;
    /** Backing right off: for longer, further, and out of a wider area round the work. */
    private static final int BACK_OFF_TICKS = 60;
    private static final int BACK_OFF_RADIUS = 6;
    private static final int BACK_OFF_SPREAD = 1;
    /**
     * Debounce for a worker kept from its next task: after this long it asks squadmates to back right off, and after
     * twice this long it plans another way.
     */
    private static final int TASK_BLOCKED_TICKS = 30;
    /** Moving less than this in a tick, while it should be moving, counts as being held up. */
    private static final double STALLED_DISTANCE_SQR = 0.02 * 0.02;
    /** How hard a climbing mob is steered back to the middle of the ladder, and the fastest it is moved sideways. */
    private static final double CLIMB_PULL = 0.5;
    private static final double CLIMB_MAX_PULL = 0.1;
    /** Stepping onto a ladder below an edge, this close to the middle of it the mob stops walking and drops on. */
    private static final double OVER_LADDER = 0.2;
    private static final int CLEAR_INTERVAL = 5;
    /** How long after its last bit of work a mob still counts as working. */
    private static final int WORKING_TICKS = 15;
    /** A mob that has gone this long without getting anywhere, while it should be on the move, tries another way. */
    private static final int CONFUSED_TICKS = 100;
    private static final int PROGRESS_INTERVAL = 20;
    /** Moving less than this over a progress interval counts as standing still. */
    private static final double PROGRESS_DISTANCE_SQR = 0.25;
    private static final double STRIKE_RESET_DISTANCE_SQR = 16.0;

    private final Mob mob;
    private final PathAbilities abilities;

    private @Nullable MobPath path;
    private @Nullable BlockPos pathAnchor;
    private int index;
    private int stepTicks;
    private int waitTicks;
    private int repathCooldown;
    private int placeCooldown;
    private boolean pillarPlaced;
    /**
     * Where a jump is headed, from the moment the mob jumps until it lands: the landing, and for a neo the points of the
     * curve around the wall before it. Mid-air nothing else happens: the leap doesn't depend on the path, so a re-plan
     * (the target moved) can't pull the mob off course.
     */
    private @Nullable List<Vec3> leapWaypoints;
    private int leapWaypoint;
    private Vec3 leapForward = Vec3.ZERO;
    private int leapTicks;
    private boolean leapAirborne;
    private @Nullable CompletableFuture<@Nullable MobPath> baritonePlan;
    private @Nullable BlockPos miningPos;
    private String status = "idle";

    /** Cells a mob doing its job needs clear, and the way to the spot this mob is going to meanwhile (the next cell first). */
    private @Nullable LongSet yieldCells;
    private @Nullable List<BlockPos> yieldRoute;
    /** Where it waits once out of the cells: on a ladder it must keep climbing back up to it, or it slides down. */
    private @Nullable BlockPos yieldHoldAt;
    /** It walked aside off its path, so it plans afresh once it is done making way. */
    private boolean movedAside;
    private int yieldTicks;
    private int yieldRadius = YIELD_RADIUS;
    private int workTick = -WORKING_TICKS;

    /** The task (a step of a path) this worker has been kept from, for how long, and whether it asked squadmates to back off. */
    private @Nullable MobPath blockedPath;
    private int blockedIndex;
    private int blockedTicks;
    private boolean askedToBackOff;
    /** Set while following a step when the mob can't get on with it this tick. */
    private boolean blockedThisTick;
    private @Nullable Vec3 lastTickPos;

    /** Where the mob was at the last progress check, how long it has been going nowhere, and how often in a row it has. */
    private @Nullable Vec3 progressPos;
    private int confusedTicks;
    private int strikes;
    private @Nullable Vec3 strikePos;

    /** Help this mob asked for and is waiting on. */
    private @Nullable WorkRequest waitingOn;
    private int helpTicks;
    /** Help a squadmate asked for that this mob took on. */
    private @Nullable WorkRequest job;
    private boolean jobStarted;
    private int jobTicks;
    /** Cells whose work this mob gave up waiting for; it plans around them for a while. */
    private final LongSet avoid = new LongOpenHashSet();
    private long avoidUntil;
    private boolean holdingForBreach;
    private int rushTicks;
    /** A squadmate worker planned a route this mob's could follow; plan again as soon as the cooldown allows. */
    private boolean reconsider;
    /** Where its last route ended, while it has no other: on a ladder it holds on there. */
    private @Nullable BlockPos hangAt;
    /** Where a Builder waits for a Miner to dig at the spot it asked from, and whether it has looked for one yet. */
    private @Nullable BlockPos aside;
    private boolean asideLooked;

    public MobPathController(Mob mob, PathAbilities abilities) {
        this.mob = mob;
        this.abilities = abilities;
    }

    // ---------------------------------------------------------------- accessors

    public @Nullable MobPath path() {
        return path;
    }

    public int index() {
        return index;
    }

    public String status() {
        return status;
    }

    public @Nullable BlockPos miningPos() {
        return miningPos;
    }

    public PathAbilities abilities() {
        return abilities;
    }

    public boolean hasJob() {
        return job != null;
    }

    /** Keeping out of the way of a squadmate's work. */
    public boolean isYielding() {
        return yieldTicks > 0 && yieldCells != null;
    }

    public @Nullable WorkRequest job() {
        return job;
    }

    /**
     * On its feet on something solid: not hanging on a ladder, mid-jump or swimming. Until it is, it is in no state to go
     * off and help a squadmate; it gets itself somewhere safe first. A Miner or Builder on a ladder is free too: hanging on,
     * it can do what it can reach (see {@link #digInReach}, {@link #buildInReach}), and up a ladder is often just where the
     * work is wanted.
     */
    public boolean isFree() {
        boolean worker = abilities.canPlace() || abilities.canBreak() && !abilities.explosive();
        boolean footing = mob.onGround() && !onClimbable() || onClimbable() && worker;
        return footing && !mob.isInWater() && leapWaypoints == null;
    }

    /** Drop the current path so the next tick plans a fresh one. */
    public void forceRepath() {
        invalidate("forced re-plan");
        repathCooldown = 0;
    }

    /** Take on a squadmate's request: go to where they wait, then do the work along their path. */
    public void takeJob(WorkRequest request) {
        abandonHelp();
        job = request;
        jobStarted = false;
        jobTicks = 0;
        path = null;
        repathCooldown = 0;
        status = "answering a call for help";
    }

    // ---------------------------------------------------------------- ticking

    /** Moves towards the goal, or works on the current job (the goal is ignored while there is one). */
    public void tick(@Nullable PathGoal goal) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        if (repathCooldown > 0) {
            repathCooldown--;
        }
        if (placeCooldown > 0) {
            placeCooldown--;
        }
        if (rushTicks > 0) {
            rushTicks--;
        }
        if (!avoid.isEmpty() && level.getGameTime() > avoidUntil) {
            avoid.clear();
        }
        if (leapWaypoints != null && steerLeap()) {
            return;
        }
        if (isYielding() && yieldWay()) {
            return;
        }

        if (job != null) {
            if (!jobStillWanted(level)) {
                finishJob("done helping");
                return;
            }
            if (!jobStarted) {
                goal = PathGoal.near(job.site(), 1, "a squadmate who needs help");
                // Arrived by the walk's own measure: anything stricter could leave it at the goal of a walk that is over,
                // neither planning nor starting.
                BlockPos at = feet();
                if (goal.isGoal(at.getX(), at.getY(), at.getZ()) && (mob.onGround() || onClimbable())) {
                    startJob();
                }
            }
        }
        if (job != null && (job.kind() == WorkRequest.Kind.DIG ? digInReach(level) : buildInReach(level))) {
            return;
        }
        if (path != null && index >= path.steps().size()) {
            if (job != null && jobStarted) {
                finishJob("finished helping");
                return;
            }
            // Reached the end. Drop the path so nothing indexes past it; a new one is planned once the
            // re-plan cooldown allows (or never, if the objective was reached and the goal stops).
            hangAt = path.last().pos();
            dropPath();
            status = "reached end of path";
        }
        if (path == null && hangAt != null) {
            if (onClimbable() && feet().distManhattan(hangAt) <= 1) {
                // Its route ended on a ladder (beside a target up high, say): it hangs on there rather than sliding off.
                hold(hangAt);
            } else {
                hangAt = null;
            }
        }
        if (goal != null && !(job != null && jobStarted) && shouldRepath(goal)) {
            repath(level, goal);
        }
        blockedThisTick = false;
        if (path != null && index < path.steps().size()) {
            follow(level);
        }
        if (path != null && !blockedThisTick && (mob.tickCount & 1) == 0) {
            spreadOut(level);
        }
        watchTask(level);
        watchProgress(goal);
        lastTickPos = mob.position();
    }

    /**
     * Nudges a walking mob off any wave mob it is standing almost on top of, so a crowd doesn't fold into one cell. Only
     * sideways on flat ground and onto a safe floor (never off a stair or ledge), and not near its target, where mobs
     * are meant to close in.
     */
    private void spreadOut(ServerLevel level) {
        if (!mob.onGround() || onClimbable() || job != null || isYielding()
                || mob.getTarget() != null && mob.distanceToSqr(mob.getTarget()) < SPREAD_TARGET_RANGE * SPREAD_TARGET_RANGE) {
            return;
        }
        double awayX = 0.0;
        double awayZ = 0.0;
        for (Mob other : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(SPREAD_RADIUS, 0.0, SPREAD_RADIUS),
                o -> o != mob && o instanceof WaveMob && o.onGround() && Math.abs(o.getY() - mob.getY()) < 0.6)) {
            double dx = mob.getX() - other.getX();
            double dz = mob.getZ() - other.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance >= SPREAD_RADIUS) {
                continue;
            }
            if (distance < 1.0E-3) {
                // Exactly on top: the lower id goes one way, the other the opposite, so the pair always separates.
                dx = mob.getId() < other.getId() ? 1.0 : -1.0;
                dz = 0.0;
                distance = 1.0;
            }
            double weight = (SPREAD_RADIUS - Math.min(distance, SPREAD_RADIUS)) / SPREAD_RADIUS;
            awayX += dx / distance * weight;
            awayZ += dz / distance * weight;
        }
        double length = Math.sqrt(awayX * awayX + awayZ * awayZ);
        if (length < 1.0E-3) {
            return;
        }
        double nudgeX = awayX / length * SPREAD_SPEED * Math.min(1.0, length);
        double nudgeZ = awayZ / length * SPREAD_SPEED * Math.min(1.0, length);
        BlockPos ahead = BlockPos.containing(mob.getX() + nudgeX * 6.0, mob.getY(), mob.getZ() + nudgeZ * 6.0);
        if (!BlockView.isOpen(level, ahead.below()) && !BlockView.isHarmful(level.getBlockState(ahead))
                && BlockView.isOpen(level, ahead) && BlockView.isOpen(level, ahead.above())) {
            mob.setDeltaMovement(mob.getDeltaMovement().add(nudgeX, 0.0, nudgeZ));
        }
    }

    public void stop() {
        progressPos = null;
        confusedTicks = 0;
        miningPos = null;
        hangAt = null;
        dropPath();
        status = "idle";
        abandonHelp();
        if (job != null) {
            finishJob("idle");
        }
        Squad squad = Squads.of(mob);
        if (squad != null) {
            squad.releaseAll(mob);
        }
    }

    private boolean shouldRepath(PathGoal goal) {
        if (repathCooldown > 0) {
            return false;
        }
        if (!mob.onGround() && !onClimbable() && !mob.isInWater() && !abilities.climbWalls()) {
            // Mid-jump the cell it is in has no floor: a plan from there (or a squadmate asked to work from there) is
            // nonsense. Spiders are exempt, as they plan from walls.
            return false;
        }
        if (path == null || reconsider) {
            return true;
        }
        // The target has wandered away from where this path leads.
        return pathAnchor != null && pathAnchor.distSqr(goal.anchor()) > 9.0 || headsForOthers();
    }

    /**
     * Whether the next few plain steps lead through cells other mobs stand on or are heading for. The plan was made when
     * they were elsewhere, so a fresh one (which prices those cells) lets this mob take a clear line instead.
     */
    private boolean headsForOthers() {
        Squad squad = Squads.of(mob);
        if (squad == null || path == null || job != null || waitingOn != null) {
            return false;
        }
        SquadSupport support = squad.support(mob);
        List<PathStep> steps = path.steps();
        for (int k = index; k < Math.min(steps.size(), index + CROWD_LOOKAHEAD); k++) {
            PathStep step = steps.get(k);
            if (!step.breaks().isEmpty() || !step.places().isEmpty()) {
                return false;
            }
            if (support.isCrowded(step.pos().asLong())) {
                return true;
            }
        }
        return false;
    }

    private void repath(ServerLevel level, PathGoal goal) {
        reconsider = false;
        // Baritone doesn't know the cells this mob has given up on, so after a failure the mob's own planner plans instead.
        if (abilities.prefersBaritone() && BaritonePrediction.isAvailable() && avoid.isEmpty()) {
            planWithBaritone(level, goal);
        } else {
            planOwn(level, goal);
        }
    }

    private void planOwn(ServerLevel level, PathGoal goal) {
        Squad squad = Squads.of(mob);
        SquadSupport support = squad != null ? squad.support(mob) : SquadSupport.NONE;
        MobPath result = MobPathfinder.find(level, abilities, support, avoid, feet(), goal);
        if (result == null) {
            if (path == null) {
                status = "no route (or path budget spent)";
            }
            // Maybe the cells it was avoiding were the only way: forget them and look again.
            avoid.clear();
            repathCooldown = 10;
            return;
        }
        adopt(level, result, goal, squad);
    }

    /**
     * Baritone can plan jumps over gaps, and spiders' climbs. It searches off the server thread, so the route arrives a
     * tick or more later; if it can't get all the way, the mob's own planner (which can count on the squad's help) gets
     * a go.
     */
    private void planWithBaritone(ServerLevel level, PathGoal goal) {
        if (baritonePlan != null) {
            return;
        }
        if (path == null) {
            status = "planning with Baritone";
        }
        repathCooldown = REPATH_COOLDOWN;
        baritonePlan = abilities.climbWalls()
                ? BaritonePrediction.planSpiderPath(level, feet(), goal)
                : BaritonePrediction.planSprinterPath(level, feet(), goal);
        baritonePlan.whenComplete((result, error) -> {
            baritonePlan = null;
            if (mob.isRemoved() || !mob.isAlive() || job != null) {
                return;
            }
            if (leapWaypoints != null) {
                // Planned from where the jump started; plan again once it lands.
                repathCooldown = 0;
                return;
            }
            if (error != null) {
                UntilDawn.LOGGER.warn("Baritone failed to plan for {}", mob, error);
            }
            if (result != null && result.reachesGoal()) {
                adopt(level, result, goal, Squads.of(mob));
            } else {
                planOwn(level, goal);
            }
        });
    }

    private void adopt(ServerLevel level, MobPath result, PathGoal goal, @Nullable Squad squad) {
        miningPos = null;
        if (squad != null) {
            squad.releaseAll(mob);
        }
        boolean hadWork = hasWork(path);
        path = result;
        hangAt = null;
        pathAnchor = goal.anchor();
        index = 1;
        resetStep();
        repathCooldown = REPATH_COOLDOWN;
        claimUpcoming(level);
        if (squad != null && (hadWork || hasWork(result))) {
            squad.routeChanged(mob);
        }
    }

    /** Lets go of the path; a worker's squadmates hear of it, as work they were counting on may now not be done. */
    private void dropPath() {
        boolean hadWork = hasWork(path);
        path = null;
        Squad squad = Squads.of(mob);
        if (squad != null && hadWork) {
            squad.routeChanged(mob);
        }
    }

    /** A route this worker does work on, which its squad counts on: see {@link Squad#routeChanged}. */
    private boolean hasWork(@Nullable MobPath route) {
        return route != null && (abilities.canPlace() && route.placeCount() > 0 || abilities.canBreak() && route.breakCount() > 0);
    }

    /**
     * A squadmate worker has planned a new route. If this mob's route needs work that nobody has taken on or planned (it
     * would have to ask for it), it plans again: the worker's route is cheap to follow, so it will likely go that way.
     */
    public void reconsiderIfUnaided(Squad squad) {
        if (path == null || job != null || waitingOn != null || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        SquadSupport support = squad.support(mob);
        List<PathStep> steps = path.steps();
        for (int k = index; k < steps.size(); k++) {
            PathStep step = steps.get(k);
            for (BlockPos block : step.breaks()) {
                if (!BlockView.isOpen(level, block) && !breaksItself(level.getBlockState(block)) && !support.isBreakPending(block.asLong())) {
                    reconsider = true;
                    return;
                }
            }
            for (BlockPos spot : step.places()) {
                if (!abilities.canPlace() && level.getBlockState(spot).canBeReplaced() && !support.isPlacePending(spot.asLong())) {
                    reconsider = true;
                    return;
                }
            }
        }
    }

    private void invalidate(String reason) {
        miningPos = null;
        path = null;
        status = reason;
        repathCooldown = Math.min(repathCooldown, 5);
        if (job != null) {
            // Walk back to the start of the job and try again.
            jobStarted = false;
        }
    }

    private void resetStep() {
        stepTicks = 0;
        waitTicks = 0;
        pillarPlaced = false;
        if (leapWaypoints == null) {
            mob.setSprinting(false);
        }
    }

    // ---------------------------------------------------------------- jobs (helping squadmates)

    private boolean jobStillWanted(ServerLevel level) {
        Squad squad = Squads.of(mob);
        return job != null && !job.isClosed() && mob.getUUID().equals(job.responder()) && ++jobTicks <= JOB_TICKS
                && squad != null && squad.requests().contains(job) && (jobTicks % 10 != 0 || !job.isDone(level));
    }

    private void startJob() {
        path = new MobPath(job.segment(), true, 0.0, 0, 0, "work for a squadmate");
        hangAt = null;
        pathAnchor = null;
        index = 1;
        resetStep();
        jobStarted = true;
        status = job.kind() == WorkRequest.Kind.DIG ? "digging for a squadmate" : "building for a squadmate";
    }

    private void finishJob(String reason) {
        Squad squad = Squads.of(mob);
        if (job != null && squad != null) {
            // If the work isn't done, someone else can pick it up.
            squad.dropJob(job, mob);
        }
        job = null;
        jobStarted = false;
        path = null;
        status = reason;
        repathCooldown = 0;
    }

    /**
     * A Miner on a dig job digs any block of it within reach of where it is, up or across as well as straight ahead, so it
     * needn't squeeze into the spot the asker waits in, or climb all the way to it.
     */
    private boolean digInReach(ServerLevel level) {
        if (!abilities.canBreak() || abilities.explosive() || !(mob.onGround() || onClimbable())) {
            return false;
        }
        Vec3 eyes = mob.getEyePosition();
        BlockPos target = null;
        double nearest = REACH * REACH;
        for (long cell : job.cells()) {
            BlockPos pos = BlockPos.of(cell);
            double distance = eyes.distanceToSqr(Vec3.atCenterOf(pos));
            if (BlockView.isOpen(level, pos) || distance > REACH * REACH) {
                continue;
            }
            if (pos.equals(miningPos)) {
                // Keep on at the block it has started on.
                target = pos;
                break;
            }
            if (distance < nearest) {
                target = pos;
                nearest = distance;
            }
        }
        if (target == null) {
            return false;
        }
        stepTicks++;
        mine(level, feet(), target);
        status = "digging for a squadmate";
        return true;
    }

    /**
     * A Builder on a build job places the job's blocks from where it is while the next one is within reach, in the order
     * the asker's path needs them; out of reach (or where it can't place one) it walks the job as usual.
     */
    private boolean buildInReach(ServerLevel level) {
        if (!abilities.canPlace() || !(mob.onGround() || onClimbable())) {
            return false;
        }
        for (PathStep step : job.segment()) {
            for (BlockPos spot : step.places()) {
                if (!level.getBlockState(spot).canBeReplaced()) {
                    continue;
                }
                if (!inReach(spot)) {
                    return false;
                }
                if (placeCooldown <= 0 && !(step.ladders().contains(spot) ? placeLadder(level, spot) : placePlank(level, spot))) {
                    return false;
                }
                hold(feet());
                workTick = mob.tickCount;
                status = "building for a squadmate";
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- following

    private void follow(ServerLevel level) {
        List<PathStep> steps = path.steps();
        BlockPos feet = feet();

        // Shortcuts: if the mob is already standing on a later node, jump ahead.
        for (int k = Math.min(index + 3, steps.size() - 1); k > index; k--) {
            if (feet.equals(steps.get(k).pos()) && (mob.onGround() || onClimbable())) {
                index = k + 1;
                resetStep();
                if (index >= steps.size()) {
                    return;
                }
                break;
            }
        }

        PathStep step = steps.get(index);
        BlockPos previous = steps.get(index - 1).pos();
        if (holdForBreach(level, steps)) {
            return;
        }
        stepTicks++;
        if (stepTicks > STEP_TIMEOUT && miningPos == null && waitTicks == 0 && waitingOn == null) {
            tryAnotherWay("stuck, trying another way");
            return;
        }
        if (feet.distManhattan(step.pos()) > 4 && feet.distManhattan(previous) > 4 || droppedBelow(feet, previous, step)) {
            invalidate("knocked off path, re-planning");
            return;
        }
        if (builtInto(level, step)) {
            cantDo(LongSet.of(), "the way has been built into, re-planning");
            return;
        }
        if (stepTicks % 10 == 1) {
            claimUpcoming(level);
        }
        Squad squad = Squads.of(mob);
        if ((job != null || hasPendingWork(level, step)) && stepTicks % CLEAR_INTERVAL == 1) {
            clearTheWay(level, steps, step);
        }

        // 1. Clear the way.
        for (BlockPos block : step.breaks()) {
            if (BlockView.isOpen(level, block)) {
                continue;
            }
            boolean own = breaksItself(level.getBlockState(block));
            if (squad != null && squad.isBreakClaimedByOther(block, mob)) {
                // Kept from its own work it counts as blocked (see watchTask); waiting on a squadmate's digging it doesn't,
                // or it would tell the very mob doing the digging to back off.
                blockedThisTick = own;
                waitForDigging(level, steps, squad, block, "waiting for a squadmate to dig through");
                return;
            }
            if (!own) {
                // A Builder asks even where a squadmate's route plans the digging: up a ladder, the Miner is likely stuck
                // below it, and it can dig from there on request.
                if (squad != null && squad.isPlannedByOther(block, mob) && !abilities.canPlace()) {
                    waitForDigging(level, steps, squad, block, "waiting for a squadmate's tunnel");
                } else {
                    askForHelp(level, WorkRequest.Kind.DIG, steps, previous);
                }
                return;
            }
            if (abilities.explosive()) {
                breach(level, previous, block, steps);
            } else {
                mine(level, previous, block);
            }
            return;
        }

        // The way is open: a Builder that waited aside takes the space back.
        aside = null;
        asideLooked = false;

        // 2. Build what the step stands on (pillars place mid-jump instead).
        if (step.type() != MoveType.PILLAR) {
            for (BlockPos spot : step.places()) {
                if (level.getBlockState(spot).canBeReplaced()) {
                    buildOrAsk(level, step, spot, steps, previous, squad);
                    return;
                }
            }
        }
        waitTicks = 0;

        // 3. Move.
        Vec3 target = Vec3.atBottomCenterOf(step.pos());
        double speed = rushTicks > 0 ? RUSH_SPEED : SPEED;
        boolean onLadder = false;
        switch (step.type()) {
            case PILLAR -> {
                status = "towering up";
                BlockPos under = step.pos().below();
                if (!pillarPlaced) {
                    if (placeCooldown <= 0 && mob.getBoundingBox().minY >= under.getY() + 1.0 && placePlank(level, under)) {
                        pillarPlaced = true;
                    } else if (mob.onGround()) {
                        mob.getJumpControl().jump();
                    }
                }
            }
            case CLIMB -> {
                status = "climbing";
                climbColumn(step.pos(), true);
            }
            case CLIMB_DOWN -> {
                status = "climbing down";
                climbColumn(step.pos(), false);
            }
            case DESCEND -> {
                status = "moving (descend)";
                if (BlockView.isClimbable(level.getBlockState(step.pos()))) {
                    status = "climbing down";
                    stepOntoLadder(step.pos());
                    onLadder = true;
                }
            }
            case PARKOUR -> {
                status = "jumping a gap";
                leap(level, previous, step);
            }
            case NEO -> {
                status = "jumping around a wall";
                leap(level, previous, step);
            }
            case WALL_CLIMB -> {
                status = "climbing a wall";
                pressToWall(level, previous, step);
            }
            case WALL_DOWN -> {
                status = "climbing down a wall";
                pressToWall(level, previous, step);
            }
            default -> {
                status = "moving (" + step.type().name().toLowerCase(Locale.ROOT) + ")";
                if (offLadderTooLow(step, previous)) {
                    // Walking off sideways now, it would let go of the ladder and slide down it, and off it.
                    status = "climbing to the top of the ladder";
                    climbOffLadder(previous, step.pos());
                    onLadder = true;
                } else if (step.type() == MoveType.ASCEND && (mob.onGround() || onClimbable()) && mob.horizontalCollision) {
                    mob.getJumpControl().jump();
                }
            }
        }
        boolean leaping = step.type() == MoveType.PARKOUR || step.type() == MoveType.NEO;
        if (!leaping && !onLadder && step.type() != MoveType.CLIMB && step.type() != MoveType.CLIMB_DOWN && !step.type().isWallClimb()) {
            mob.getMoveControl().setWantedPosition(target.x, target.y, target.z, speed);
        }

        if (arrived(step, feet)) {
            AccessCorridors.reserve(level, step);
            index++;
            resetStep();
            helpTicks = 0;
        } else if (!leaping && lastTickPos != null && mob.position().distanceToSqr(lastTickPos) < STALLED_DISTANCE_SQR) {
            // Should be moving but isn't: usually squadmates in the way.
            blockedThisTick = true;
        }
    }

    /**
     * A Builder has put a plank where the step's body goes since the route was planned (building another squadmate's way,
     * say): walking on, the mob would just push against it.
     */
    private static boolean builtInto(ServerLevel level, PathStep step) {
        for (int h = 0; h <= 1; h++) {
            BlockPos cell = step.pos().above(h);
            if (!step.breaks().contains(cell) && level.getBlockState(cell).is(ModBlocks.UNSTABLE_PLANKS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Standing two or more blocks below both ends of the step: it dropped off the path (down a stair, say), and walking on
     * towards the step from down here would take it over the edge of whatever it now stands on.
     */
    private boolean droppedBelow(BlockPos feet, BlockPos previous, PathStep step) {
        return mob.onGround() && feet.getY() <= Math.min(previous.getY(), step.pos().getY()) - 2;
    }

    /**
     * Hanging on a ladder, about to step off it sideways onto something no lower than where it hangs, but not yet up level
     * with it. A mob moving off sideways stops pressing on anything and slides down; it must climb up level first.
     */
    private boolean offLadderTooLow(PathStep step, BlockPos previous) {
        return onClimbable() && !mob.onGround() && step.pos().getY() >= previous.getY()
                && (step.pos().getX() != previous.getX() || step.pos().getZ() != previous.getZ())
                && mob.getY() < step.pos().getY() - 0.05;
    }

    /**
     * Climbs the ladder in {@code column} while pressing towards {@code to}: pressing into the block it steps onto is what
     * lifts a mob past the top rung. Across that way it is held to the middle, so it can't drift off the ladder's side.
     */
    private void climbOffLadder(BlockPos column, BlockPos to) {
        Vec3 centre = Vec3.atBottomCenterOf(column);
        int dx = Integer.signum(to.getX() - column.getX());
        int dz = Integer.signum(to.getZ() - column.getZ());
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), SPEED);
        mob.setDeltaMovement(
                dx != 0 ? dx * CLIMB_MAX_PULL : Mth.clamp((centre.x - mob.getX()) * CLIMB_PULL, -CLIMB_MAX_PULL, CLIMB_MAX_PULL),
                mob.getDeltaMovement().y,
                dz != 0 ? dz * CLIMB_MAX_PULL : Mth.clamp((centre.z - mob.getZ()) * CLIMB_PULL, -CLIMB_MAX_PULL, CLIMB_MAX_PULL));
        mob.getJumpControl().jump();
    }

    /**
     * Climbs a ladder (or scaffolding) straight up or down, keeping to the middle of the block: walking towards the next
     * block would carry the mob past the middle and off the side, so instead it stops walking and is steered straight in.
     */
    private void climbColumn(BlockPos column, boolean up) {
        Vec3 centre = Vec3.atBottomCenterOf(column);
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), SPEED);
        mob.setDeltaMovement(
                Mth.clamp((centre.x - mob.getX()) * CLIMB_PULL, -CLIMB_MAX_PULL, CLIMB_MAX_PULL),
                mob.getDeltaMovement().y,
                Mth.clamp((centre.z - mob.getZ()) * CLIMB_PULL, -CLIMB_MAX_PULL, CLIMB_MAX_PULL));
        if (up) {
            mob.getJumpControl().jump();
        }
    }

    /**
     * Steps off an edge onto the top of a ladder a block down. Walking on, it would be carried out past the ladder as it
     * dropped, so once over the ladder it stops walking and is held to the middle of it while it drops on.
     */
    private void stepOntoLadder(BlockPos column) {
        Vec3 centre = Vec3.atBottomCenterOf(column);
        double dx = centre.x - mob.getX();
        double dz = centre.z - mob.getZ();
        if (onClimbable() || dx * dx + dz * dz < OVER_LADDER * OVER_LADDER) {
            climbColumn(column, false);
        } else {
            mob.getMoveControl().setWantedPosition(centre.x, mob.getY(), centre.z, SPEED);
        }
    }

    /**
     * A spider's climb: it walks into the wall it climbs, and the pushing is what holds it there (rising while it
     * pushes, sliding down slowly when the step goes down). Over an edge it first walks out until it drops off, then
     * turns back into the side of the block it stood on.
     */
    private void pressToWall(ServerLevel level, BlockPos previous, PathStep step) {
        BlockPos column = step.pos();
        Vec3 centre = Vec3.atBottomCenterOf(column);
        boolean overEdge = column.getX() != previous.getX() || column.getZ() != previous.getZ();
        if (overEdge && mob.getY() > column.getY() + 0.99) {
            // Still on the edge: out past it far enough for the whole body to clear it.
            Vec3 out = new Vec3(column.getX() - previous.getX(), 0.0, column.getZ() - previous.getZ());
            Vec3 past = centre.add(out.scale(mob.getBbWidth() / 2.0 - 0.3));
            mob.getMoveControl().setWantedPosition(past.x, mob.getY(), past.z, SPEED);
            return;
        }
        // The wall to cling to: beside the feet on the way up, beside the cell below on the way down.
        int clingY = step.type() == MoveType.WALL_CLIMB ? previous.getY() : column.getY();
        Direction wall = null;
        if (overEdge) {
            wall = Direction.getApproximateNearest(previous.getX() - column.getX(), 0, previous.getZ() - column.getZ());
        } else {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos side = new BlockPos(column.getX(), clingY, column.getZ()).relative(direction);
                if (!BlockView.isOpen(level, side)) {
                    wall = direction;
                    break;
                }
            }
        }
        Vec3 target = wall == null ? centre : centre.add(wall.getStepX() * 0.5, 0.0, wall.getStepZ() * 0.5);
        mob.getMoveControl().setWantedPosition(target.x, mob.getY(), target.z, SPEED);
    }

    /** Places a plank (on a climb, a ladder), or asks a squadmate to. */
    private void buildOrAsk(ServerLevel level, PathStep step, BlockPos spot, List<PathStep> steps, BlockPos previous, @Nullable Squad squad) {
        if (squad != null && squad.isPlaceClaimedByOther(spot, mob)) {
            blockedThisTick = true;
            waitAt(waitingSpot(level, steps), "waiting for a squadmate to build");
        } else if (!abilities.canPlace() && squad != null && squad.isPlannedByOther(spot, mob)) {
            waitAt(waitingSpot(level, steps), "waiting for a squadmate's way up");
        } else if (!abilities.canPlace()) {
            askForHelp(level, WorkRequest.Kind.BUILD, steps, previous);
        } else if (placeCooldown > 0) {
            workTick = mob.tickCount;
            waitAt(previous, "building");
        } else if (step.ladders().contains(spot)) {
            // A ladder isn't in anyone's way, so nobody has to move for it. With nothing to hang it on (the world isn't as
            // planned), a plank beside it first.
            if (!placeLadder(level, spot) && !propUpLadder(level, spot, steps, squad)) {
                cantDo(LongSet.of(spot.asLong()), "can't hang a ladder there, re-planning");
            } else {
                hold(previous);
            }
        } else if (squad != null && squad.isOnRouteOfOther(spot, mob)) {
            workTick = mob.tickCount;
            blockedThisTick = true;
            waitAt(previous, "waiting for a squadmate to pass");
        } else if (mob.getBoundingBox().intersects(new AABB(spot))) {
            // Standing in the spot itself (squeezed out of where it should stand): step back, then build.
            workTick = mob.tickCount;
            waitAt(previous, "stepping back to build");
        } else if (onlySquadmatesIn(level, spot)) {
            // They are asked to move (see clearTheWay); don't give up the spot meanwhile.
            workTick = mob.tickCount;
            blockedThisTick = true;
            waitAt(previous, "waiting for mobs to move out of the way");
        } else if (!placePlank(level, spot)) {
            cantDo(LongSet.of(spot.asLong()), "can't build there, re-planning");
        }
    }

    private boolean arrived(PathStep step, BlockPos feet) {
        if (!feet.equals(step.pos())) {
            return false;
        }
        return switch (step.type()) {
            case PILLAR -> pillarPlaced && mob.onGround();
            case CLIMB, CLIMB_DOWN -> onClimbable() || mob.onGround();
            // Pressed to the wall it is off the middle of the block, and it may be in mid-air.
            case WALL_CLIMB, WALL_DOWN -> true;
            case ASCEND, DESCEND, FALL, DIG_DOWN, PARKOUR, NEO -> mob.onGround() || mob.isInWater() || onClimbable();
            default -> {
                double dx = mob.getX() - (step.pos().getX() + 0.5);
                double dz = mob.getZ() - (step.pos().getZ() + 0.5);
                yield dx * dx + dz * dz < 0.45 * 0.45;
            }
        };
    }

    /** Lines up on the take-off block {@code from}, then jumps for the step's block; {@link #steerLeap} does the rest. */
    private void leap(ServerLevel level, BlockPos from, PathStep step) {
        Vec3 takeOff = Vec3.atBottomCenterOf(from);
        Vec3 landing = Vec3.atBottomCenterOf(step.pos());
        mob.getMoveControl().setWantedPosition(takeOff.x, takeOff.y, takeOff.z, SPEED);
        double dx = mob.getX() - takeOff.x;
        double dz = mob.getZ() - takeOff.z;
        if (!mob.onGround() || dx * dx + dz * dz >= TAKE_OFF_SLACK * TAKE_OFF_SLACK) {
            return;
        }
        Vec3 forward = landing.subtract(takeOff).multiply(1.0, 0.0, 1.0).normalize();
        List<Vec3> waypoints = new ArrayList<>();
        if (step.type() == MoveType.NEO) {
            Vec3 side = neoSide(level, from, step.pos(), forward);
            if (side == null) {
                invalidate("the way around the wall is blocked, re-planning");
                return;
            }
            double length = landing.subtract(takeOff).horizontalDistance();
            waypoints.add(takeOff.add(forward.scale(NEO_CORNER)).add(side.scale(NEO_SWING)));
            waypoints.add(takeOff.add(forward.scale(length - NEO_CORNER)).add(side.scale(NEO_SWING)));
            landing = landing.add(side.scale(NEO_LANDING));
        }
        waypoints.add(landing);
        mob.getLookControl().setLookAt(landing.x, landing.y + mob.getEyeHeight(), landing.z);
        // Leave the ground heading for the first point, not with whatever the run-up left over (a neo must go sideways
        // first or it hits the wall); not sprinting yet, as a sprint-jump adds a push straight ahead.
        mob.setSprinting(false);
        Vec3 start = waypoints.getFirst().subtract(mob.position()).multiply(1.0, 0.0, 1.0);
        if (start.lengthSqr() > 1.0E-4) {
            start = start.normalize().scale(TAKE_OFF_SPEED);
            mob.setDeltaMovement(start.x, mob.getDeltaMovement().y, start.z);
        }
        mob.getJumpControl().jump();
        leapWaypoints = waypoints;
        leapWaypoint = 0;
        leapForward = forward;
        leapTicks = 0;
        leapAirborne = false;
    }

    /** Which side of the wall a neo goes around: the side whose column, from beside take-off to beside landing, is open. */
    private static @Nullable Vec3 neoSide(ServerLevel level, BlockPos from, BlockPos to, Vec3 forward) {
        int fx = (int) Math.round(forward.x);
        int fz = (int) Math.round(forward.z);
        int length = Math.abs(to.getX() - from.getX()) + Math.abs(to.getZ() - from.getZ());
        for (int[] side : new int[][]{{-fz, fx}, {fz, -fx}}) {
            boolean open = true;
            for (int i = 0; i <= length && open; i++) {
                for (int h = 0; h <= 2 && open; h++) {
                    open = BlockView.isOpen(level, from.offset(fx * i + side[0], h, fz * i + side[1]));
                }
            }
            if (open) {
                return new Vec3(side[0], 0.0, side[1]);
            }
        }
        return null;
    }

    /**
     * Mid-leap, every tick: head for the next point of the jump at the speed that covers what is left of it in the time
     * left in the air (a mob's own air control can't steer a long jump, let alone a curve), correcting for whatever
     * slowed it. Returns false once the leap is over: landed, or the jump never happened.
     */
    private boolean steerLeap() {
        leapTicks++;
        if (mob.onGround() || mob.isInWater() || onClimbable()) {
            if (leapAirborne || leapTicks > LEAP_TAKE_OFF_TICKS) {
                leapWaypoints = null;
                mob.setSprinting(false);
                return false;
            }
            if (leapTicks % 3 == 0) {
                // The jump didn't happen (bumped, or the jump control was busy): try again.
                mob.getJumpControl().jump();
            }
            return true;
        }
        leapAirborne = true;
        mob.setSprinting(true);
        List<Vec3> waypoints = leapWaypoints;
        Vec3 position = mob.position();
        while (leapWaypoint < waypoints.size() - 1 && passed(position, waypoints.get(leapWaypoint))) {
            leapWaypoint++;
        }
        Vec3 landing = waypoints.getLast();
        Vec3 next = waypoints.get(leapWaypoint);
        mob.getMoveControl().setWantedPosition(next.x, next.y, next.z, SPEED);
        Vec3 toNext = next.subtract(position).multiply(1.0, 0.0, 1.0);
        double rest = toNext.length();
        for (int i = leapWaypoint + 1; i < waypoints.size(); i++) {
            rest += waypoints.get(i).subtract(waypoints.get(i - 1)).horizontalDistance();
        }
        double vy = mob.getDeltaMovement().y;
        boolean missed = vy < 0 && mob.getY() < landing.y;
        if (toNext.lengthSqr() > 1.0E-4 && !missed) {
            // Air drag shrinks the speed every tick: v * (1 + d + d^2 + ...) over the time left in the air.
            int ticks = airTicks(vy, landing.y - mob.getY());
            double speed = rest * (1.0 - AIR_DRAG) / (1.0 - Math.pow(AIR_DRAG, ticks));
            Vec3 velocity = toNext.normalize().scale(Math.min(speed, MAX_LEAP_SPEED));
            mob.setDeltaMovement(velocity.x, vy, velocity.z);
        }
        return true;
    }

    /** Near a point of the curve, or level with it along the jump and close to its line. */
    private boolean passed(Vec3 position, Vec3 waypoint) {
        Vec3 offset = position.subtract(waypoint).multiply(1.0, 0.0, 1.0);
        double along = offset.dot(leapForward);
        double across = offset.subtract(leapForward.scale(along)).length();
        return offset.length() < WAYPOINT_REACHED || (along >= -0.05 && across < WAYPOINT_REACHED);
    }

    /** Ticks until a body rising at {@code vy} comes back down to {@code dy} above (or below) where it is now. */
    private static int airTicks(double vy, double dy) {
        double y = 0.0;
        for (int tick = 1; tick <= 60; tick++) {
            y += vy;
            vy = (vy - 0.08) * 0.98;
            if (vy < 0 && y <= dy) {
                return tick;
            }
        }
        return 60;
    }

    /**
     * Where to wait for a squadmate's work on the next step: the step before it, or the one before that if another mob
     * already stands there (often the worker itself). Crowding onto one cell of a narrow stair pushes mobs off the edge.
     */
    private BlockPos waitingSpot(ServerLevel level, List<PathStep> steps) {
        BlockPos previous = steps.get(index - 1).pos();
        if (index < 2 || level.getEntitiesOfClass(Mob.class, new AABB(previous), other -> other != mob && other.isAlive()).isEmpty()) {
            return previous;
        }
        return steps.get(index - 2).pos();
    }

    private void waitAt(BlockPos spot, String reason) {
        status = reason;
        if (++waitTicks > MAX_WAIT_TICKS) {
            invalidate("waited too long, re-planning");
            return;
        }
        hold(spot);
    }

    private void hold(BlockPos spot) {
        if (onClimbable() && mob.getBlockX() == spot.getX() && mob.getBlockZ() == spot.getZ()) {
            // Hang on in the middle of the ladder rather than walking about on it. Holding still, as a player does by
            // crouching, so a Builder climbing down hangs its next rung before it goes on instead of sliding past it.
            boolean rise = mob.getY() < spot.getY();
            climbColumn(spot, rise);
            if (!rise) {
                Vec3 motion = mob.getDeltaMovement();
                mob.setDeltaMovement(motion.x, 0.0, motion.z);
            }
            return;
        }
        Vec3 hold = Vec3.atBottomCenterOf(spot);
        mob.getMoveControl().setWantedPosition(hold.x, hold.y, hold.z, SPEED);
    }

    // ---------------------------------------------------------------- asking for help

    /** Asks the squad to do work this mob can't, and waits for it; gives up and plans around it if help doesn't come. */
    private void askForHelp(ServerLevel level, WorkRequest.Kind kind, List<PathStep> steps, BlockPos previous) {
        Squad squad = Squads.of(mob);
        if (waitingOn != null && waitingOn.isClosed() && waitingOn.kind() == kind && !waitingOn.isDone(level)) {
            // Called off: the way it asked for can't be built as planned.
            giveUp(waitingOn.cells(), "the help fell through, re-planning");
            return;
        }
        if (waitingOn != null && (waitingOn.isClosed() || waitingOn.kind() != kind)) {
            waitingOn = null;
            aside = null;
            asideLooked = false;
        }
        if (waitingOn == null) {
            SquadSupport support = squad != null ? squad.support(mob) : SquadSupport.NONE;
            boolean helpExists = kind == WorkRequest.Kind.DIG
                    ? support.canAssistDig() || support.canAssistBlast() : support.canAssistBuild();
            if (squad == null || !helpExists) {
                giveUp(WorkRequest.cellsOf(kind, List.of(steps.get(index))), "nobody can help here, re-planning");
                return;
            }
            waitingOn = squad.request(kind, requestSegment(kind, steps, level), mob);
            helpTicks = 0;
        }
        helpTicks++;
        boolean answered = waitingOn.responder() != null;
        status = kind == WorkRequest.Kind.DIG
                ? (answered ? "waiting for a squadmate to dig" : "calling for a miner")
                : (answered ? "waiting for a squadmate to build" : "calling for a builder");
        if (kind != WorkRequest.Kind.DIG || !abilities.canPlace() || !stepAside(level, previous, steps, squad, waitingOn.cells())) {
            hold(previous);
        }
        stepTicks = 0;
        if ((!answered && helpTicks > HELP_ANSWER_TICKS) || helpTicks > HELP_DONE_TICKS) {
            giveUp(waitingOn.cells(), "no help came, re-planning");
        }
    }

    /**
     * A Builder waiting for a Miner to dig (one it called, or one whose route plans the digging) steps off its spot onto a
     * floor beside it, leaving the two cells of air there for the Miner; once the way is open it takes the space back. It builds nothing to wait on: with no floor
     * beside it, it waits where it is (Miners dig what they can reach, so they needn't take its place).
     */
    private boolean stepAside(ServerLevel level, BlockPos stand, List<PathStep> steps, @Nullable Squad squad, LongSet work) {
        if (!asideLooked) {
            asideLooked = true;
            if (mob.onGround() || onClimbable()) {
                aside = findAside(level, stand, steps, squad, work);
            }
        }
        if (aside == null) {
            return false;
        }
        status = "waiting aside for a miner";
        mob.getMoveControl().setWantedPosition(aside.getX() + 0.5, aside.getY(), aside.getZ() + 0.5, SPEED);
        return true;
    }

    /**
     * A cell beside {@code stand} with a floor (not a ladder) and two cells of air, clear of the routes nearby and of the
     * work asked for; the side nearest the way the route goes first.
     */
    private @Nullable BlockPos findAside(ServerLevel level, BlockPos stand, List<PathStep> steps, @Nullable Squad squad, LongSet work) {
        LongSet route = new LongOpenHashSet();
        for (int k = index - 1; k < Math.min(steps.size(), index + 4); k++) {
            addBody(route, steps.get(k).pos());
        }
        BlockPos towards = steps.get(Math.min(index, steps.size() - 1)).pos();
        List<Direction> sides = new ArrayList<>(4);
        Direction.Plane.HORIZONTAL.forEach(sides::add);
        sides.sort(Comparator.comparingDouble(direction -> stand.relative(direction).distSqr(towards)));
        for (Direction direction : sides) {
            BlockPos cell = stand.relative(direction);
            BlockPos floor = cell.below();
            BlockPos head = cell.above();
            BlockState under = level.getBlockState(floor);
            if (BlockView.isOpen(level, floor) || BlockView.isClimbable(under) || !under.getFluidState().isEmpty()
                    || !BlockView.isOpen(level, cell) || BlockView.isClimbable(level.getBlockState(cell)) || !BlockView.isOpen(level, head)
                    || !level.getFluidState(cell).isEmpty() || route.contains(cell.asLong()) || route.contains(head.asLong())
                    || work.contains(cell.asLong()) || work.contains(head.asLong())
                    || squad != null && squad.isOnRouteOfOther(cell, mob)
                    || !level.getEntitiesOfClass(Mob.class, new AABB(cell), other -> other != mob && other.isAlive()).isEmpty()) {
                continue;
            }
            return cell;
        }
        return null;
    }

    /**
     * Waits for a squadmate to dig what this step needs (it has claimed the block, or plans to): a Builder steps aside if
     * it can, so the Miner coming up behind it has the spot to dig from (see {@link #stepAside}).
     */
    private void waitForDigging(ServerLevel level, List<PathStep> steps, @Nullable Squad squad, BlockPos block, String reason) {
        if (abilities.canPlace() && stepAside(level, steps.get(index - 1).pos(), steps, squad, LongSet.of(block.asLong()))) {
            if (++waitTicks > MAX_WAIT_TICKS) {
                invalidate("waited too long, re-planning");
            }
            return;
        }
        waitAt(waitingSpot(level, steps), reason);
    }

    /** The stretch of path from where the mob waits to the last step within reach that needs the same kind of help. */
    private List<PathStep> requestSegment(WorkRequest.Kind kind, List<PathStep> steps, ServerLevel level) {
        int last = index;
        for (int k = index; k < Math.min(steps.size(), index + REQUEST_STEPS); k++) {
            if (needsHelp(kind, steps.get(k), level)) {
                last = k;
            }
        }
        return new ArrayList<>(steps.subList(index - 1, last + 1));
    }

    private boolean needsHelp(WorkRequest.Kind kind, PathStep step, ServerLevel level) {
        if (kind == WorkRequest.Kind.DIG) {
            return step.breaks().stream().anyMatch(pos -> !BlockView.isOpen(level, pos) && !breaksItself(level.getBlockState(pos)));
        }
        return !abilities.canPlace() && step.places().stream().anyMatch(pos -> level.getBlockState(pos).canBeReplaced());
    }

    private void giveUp(LongSet cells, String reason) {
        avoid.addAll(cells);
        avoidUntil = mob.level().getGameTime() + AVOID_TICKS;
        abandonHelp();
        invalidate(reason);
    }

    private void abandonHelp() {
        Squad squad = Squads.of(mob);
        if (waitingOn != null && squad != null) {
            squad.withdraw(waitingOn, mob);
        }
        waitingOn = null;
        aside = null;
        asideLooked = false;
        helpTicks = 0;
    }

    // ---------------------------------------------------------------- breaches

    /**
     * Holds back while a squadmate's breach is about to go off, if this mob's route goes through the wall or
     * it is standing in the blast; afterwards it re-plans through the opening and rushes in.
     */
    private boolean holdForBreach(ServerLevel level, List<PathStep> steps) {
        Squad squad = Squads.of(mob);
        if (squad != null) {
            for (BreachIntent breach : squad.activeBreaches()) {
                if (breach.bomber().equals(mob.getUUID()) || !(routeUses(breach, steps) || breach.endangers(mob.position(), 1.5))) {
                    continue;
                }
                holdingForBreach = true;
                status = "holding for a Bomber's breach";
                stepTicks = 0;
                Vec3 centre = Vec3.atCenterOf(breach.centre());
                Vec3 away = mob.position().subtract(centre).multiply(1.0, 0.0, 1.0);
                if (breach.endangers(mob.position(), 2.5) && away.lengthSqr() > 1.0E-4) {
                    Vec3 safe = centre.add(away.normalize().scale(breach.radius() + 3.0));
                    mob.getMoveControl().setWantedPosition(safe.x, mob.getY(), safe.z, RUSH_SPEED);
                } else {
                    mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), SPEED);
                }
                return true;
            }
        }
        if (holdingForBreach) {
            holdingForBreach = false;
            rushTicks = RUSH_TICKS;
            invalidate("breach open, rushing through");
            repathCooldown = 0;
            return true;
        }
        return false;
    }

    private boolean routeUses(BreachIntent breach, List<PathStep> steps) {
        for (int k = index; k < Math.min(steps.size(), index + BREACH_LOOKAHEAD); k++) {
            PathStep step = steps.get(k);
            for (BlockPos block : step.breaks()) {
                if (breach.cells().contains(block.asLong())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Bomber: warn the squad, walk up to the wall and light the fuse. */
    private void breach(ServerLevel level, BlockPos standAt, BlockPos block, List<PathStep> steps) {
        Squad squad = Squads.of(mob);
        if (squad != null) {
            LongSet cells = new LongOpenHashSet();
            for (int k = index; k < Math.min(steps.size(), index + 3); k++) {
                steps.get(k).breaks().forEach(pos -> cells.add(pos.asLong()));
            }
            squad.announceBreach(mob, block, cells, UntilDawnConfig.BOMBER_EXPLOSION_RADIUS.get());
        }
        hold(standAt);
        if (mob.distanceToSqr(Vec3.atCenterOf(block)) < 2.5 * 2.5 && mob instanceof Creeper creeper && !creeper.isIgnited()) {
            creeper.ignite();
        }
        status = "breaching a wall";
    }

    // ---------------------------------------------------------------- work

    private void mine(ServerLevel level, BlockPos standAt, BlockPos block) {
        Squad squad = Squads.of(mob);
        workTick = mob.tickCount;
        if (!block.equals(miningPos)) {
            miningPos = block;
            if (squad != null) {
                squad.claimBreak(block, mob);
            }
        }
        status = "digging";
        hold(standAt);
        mob.getLookControl().setLookAt(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);

        if (stepTicks % 6 == 0) {
            mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            level.playSound(null, block, level.getBlockState(block).getSoundType().getHitSound(), mob.getSoundSource(), 0.5F, 0.8F);
        }
        BlockState state = level.getBlockState(block);
        double speed = abilities.canBreak() ? UntilDawnConfig.MINER_DIG_SPEED.get()
                // A Builder taking down its own planks or ladders: builder.breakTicks per block, however hard.
                : (double) PathCosts.mineTicks(state.getDestroySpeed(level, block)) / UntilDawnConfig.BUILDER_BREAK_TICKS.get();
        boolean broken = BlockDamage.damage(level, block, speed, mob);
        if (broken || BlockView.isOpen(level, block)) {
            if (squad != null) {
                squad.release(block);
            }
            miningPos = null;
            stepTicks = 0;
        }
    }

    private boolean placePlank(ServerLevel level, BlockPos spot) {
        if (!level.getBlockState(spot).canBeReplaced()
                || !level.getEntitiesOfClass(LivingEntity.class, new AABB(spot)).isEmpty()
                || !inReach(spot)) {
            return false;
        }
        BlockState block = ModBlocks.UNSTABLE_PLANKS.defaultBlockState();
        level.setBlockAndUpdate(spot, block);
        level.playSound(null, spot, block.getSoundType().getPlaceSound(), mob.getSoundSource(), 1.0F, 0.8F);
        afterPlacing(level, spot);
        return true;
    }

    /** Hangs a ladder in the spot, on a wall beside it that can hold one (the wall the ladder below is on, if it can). */
    private boolean placeLadder(ServerLevel level, BlockPos spot) {
        if (!level.getBlockState(spot).canBeReplaced() || !inReach(spot)) {
            return false;
        }
        BlockState below = level.getBlockState(spot.below());
        List<Direction> facings = new ArrayList<>(5);
        if (BlockView.isLadder(below)) {
            facings.add(below.getValue(LadderBlock.FACING));
        }
        Direction.Plane.HORIZONTAL.forEach(facings::add);
        for (Direction facing : facings) {
            BlockState ladder = ModBlocks.UNSTABLE_LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
            if (ladder.canSurvive(level, spot)) {
                level.setBlockAndUpdate(spot, ladder);
                level.playSound(null, spot, ladder.getSoundType().getPlaceSound(), mob.getSoundSource(), 1.0F, 0.8F);
                afterPlacing(level, spot);
                return true;
            }
        }
        return false;
    }

    /**
     * Puts a plank beside a ladder's cell for it to hang on, out of the way of this mob's next few steps and its squadmates'
     * routes; the ladder goes on it next. Prefers carrying on a pillar of planks below. Returns false if there is nowhere.
     */
    private boolean propUpLadder(ServerLevel level, BlockPos spot, List<PathStep> steps, @Nullable Squad squad) {
        LongSet route = new LongOpenHashSet();
        for (int k = index; k < Math.min(steps.size(), index + 4); k++) {
            addBody(route, steps.get(k).pos());
        }
        List<BlockPos> sides = new ArrayList<>(4);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = spot.relative(direction);
            if (route.contains(side.asLong()) || !level.getBlockState(side).canBeReplaced() || !level.getFluidState(side).isEmpty()
                    || AccessCorridors.isReserved(level, side.getX(), side.getY(), side.getZ())
                    || squad != null && squad.isOnRouteOfOther(side, mob)
                    || !level.getEntitiesOfClass(LivingEntity.class, new AABB(side)).isEmpty()) {
                continue;
            }
            if (level.getBlockState(side.below()).is(ModBlocks.UNSTABLE_PLANKS)) {
                sides.addFirst(side);
            } else {
                sides.add(side);
            }
        }
        for (BlockPos side : sides) {
            if (placePlank(level, side)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Work on the step can't be done. On its own route the mob plans around the cells; on a job the asker's planned way is
     * no good, so the job is called off and the asker plans again.
     */
    private void cantDo(LongSet cells, String reason) {
        Squad squad = Squads.of(mob);
        if (job != null && squad != null) {
            squad.cancel(job);
            finishJob(reason);
        } else {
            giveUp(cells, reason);
        }
    }

    private void afterPlacing(ServerLevel level, BlockPos spot) {
        mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        Squad squad = Squads.of(mob);
        if (squad != null) {
            squad.release(spot);
        }
        placeCooldown = UntilDawnConfig.BUILDER_PLACE_DELAY.get();
        status = "building";
    }

    /** Whether this mob breaks the block itself: a Miner (or Bomber) anything, a Builder only what Builders build. */
    private boolean breaksItself(BlockState state) {
        return abilities.canBreak() || abilities.canPlace() && BlockView.isUnstable(state);
    }

    private boolean inReach(BlockPos spot) {
        return mob.distanceToSqr(Vec3.atCenterOf(spot)) <= REACH * REACH;
    }

    /** Tell squadmates about the next few blocks this mob will dig or build, so they wait and follow. */
    private void claimUpcoming(ServerLevel level) {
        Squad squad = Squads.of(mob);
        if (path == null || squad == null) {
            return;
        }
        List<PathStep> steps = path.steps();
        for (int k = index; k < Math.min(index + CLAIM_LOOKAHEAD, steps.size()); k++) {
            PathStep step = steps.get(k);
            for (BlockPos block : step.breaks()) {
                if (!BlockView.isOpen(level, block) && breaksItself(level.getBlockState(block))) {
                    squad.claimBreak(block, mob);
                }
            }
            if (abilities.canPlace()) {
                for (BlockPos spot : step.places()) {
                    if (level.getBlockState(spot).canBeReplaced()) {
                        squad.claimPlace(spot, mob);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- getting unstuck

    /**
     * Debounce for a Miner or Builder kept from its next task (see {@link #blockedThisTick}): after
     * {@link #TASK_BLOCKED_TICKS} it asks squadmates to back right off; still kept from it as long again, it gives up
     * the task and plans another way.
     */
    private void watchTask(ServerLevel level) {
        boolean worker = abilities.canPlace() || abilities.canBreak() && !abilities.explosive();
        if (!worker || !blockedThisTick || path == null || index >= path.steps().size()) {
            blockedPath = null;
            return;
        }
        if (blockedPath != path || blockedIndex != index) {
            blockedPath = path;
            blockedIndex = index;
            blockedTicks = 0;
            askedToBackOff = false;
        }
        blockedTicks++;
        if (blockedTicks >= TASK_BLOCKED_TICKS * 2) {
            blockedPath = null;
            tryAnotherWay("still can't get on with the work, re-planning");
        } else if (blockedTicks >= TASK_BLOCKED_TICKS && !askedToBackOff) {
            askedToBackOff = true;
            backOff(level, path.steps());
        }
    }

    /**
     * Debounce for a mob that should be going somewhere but isn't: once it has barely moved for {@link #CONFUSED_TICKS}
     * (not counting waits for work, breaches or squadmates) it plans again, avoiding where it got stuck.
     */
    private void watchProgress(@Nullable PathGoal goal) {
        boolean waiting = goal == null || waitingOn != null || miningPos != null || holdingForBreach || waitTicks > 0
                || placeCooldown > 0 || leapWaypoints != null || isYielding() || mob.isUsingItem() || baritonePlan != null;
        if (waiting) {
            confusedTicks = 0;
            progressPos = null;
            return;
        }
        if (progressPos == null) {
            progressPos = mob.position();
            return;
        }
        if (mob.tickCount % PROGRESS_INTERVAL != 0) {
            return;
        }
        if (mob.position().distanceToSqr(progressPos) >= PROGRESS_DISTANCE_SQR) {
            confusedTicks = 0;
            progressPos = mob.position();
            return;
        }
        confusedTicks += PROGRESS_INTERVAL;
        if (confusedTicks >= CONFUSED_TICKS) {
            tryAnotherWay("confused, trying another way");
        }
    }

    /**
     * Gives up the step the mob is stuck on: its cells are avoided for a while, so the next plan goes round them. Stuck again
     * in the same place, it avoids more of the route and hops, to shake loose.
     */
    private void tryAnotherWay(String reason) {
        confusedTicks = 0;
        progressPos = null;
        Vec3 here = mob.position();
        if (strikePos == null || here.distanceToSqr(strikePos) > STRIKE_RESET_DISTANCE_SQR) {
            strikes = 0;
        }
        strikePos = here;
        strikes++;
        if (path != null) {
            List<PathStep> steps = path.steps();
            for (int k = index; k < Math.min(steps.size(), index + (strikes >= 2 ? 3 : 1)); k++) {
                PathStep step = steps.get(k);
                avoid.add(step.pos().asLong());
                step.breaks().forEach(pos -> avoid.add(pos.asLong()));
                step.places().forEach(pos -> avoid.add(pos.asLong()));
            }
            avoidUntil = mob.level().getGameTime() + AVOID_TICKS;
        }
        if (strikes >= 2 && mob.onGround()) {
            mob.getJumpControl().jump();
        }
        abandonHelp();
        invalidate(reason);
        repathCooldown = 0;
    }

    // ---------------------------------------------------------------- making way

    /** Whether this mob is busy digging or building, so squadmates should keep clear of it. */
    private boolean isWorking() {
        return (job != null && jobStarted) || miningPos != null || mob.tickCount - workTick < WORKING_TICKS;
    }

    /** Mobs working come before mobs walking to a job, which come before everyone else. */
    private int rank() {
        return isWorking() ? 2 : job != null ? 1 : 0;
    }

    private boolean hasPendingWork(ServerLevel level, PathStep step) {
        for (BlockPos block : step.breaks()) {
            if (!BlockView.isOpen(level, block)) {
                return true;
            }
        }
        if (step.type() != MoveType.PILLAR) {
            for (BlockPos spot : step.places()) {
                if (level.getBlockState(spot).canBeReplaced()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void addBody(LongSet cells, BlockPos feet) {
        cells.add(feet.asLong());
        cells.add(feet.above().asLong());
    }

    /**
     * A mob doing its job (or on its way to it) asks squadmates standing where it needs to be (where it stands to work
     * too: sharing a cell, they push each other about), or about to walk there, to move aside.
     */
    private void clearTheWay(ServerLevel level, List<PathStep> steps, PathStep step) {
        LongSet cells = new LongOpenHashSet();
        addBody(cells, feet());
        addBody(cells, step.pos());
        for (BlockPos spot : step.places()) {
            cells.add(spot.asLong());
        }
        if (job != null) {
            for (int k = index + 1; k <= Math.min(index + 2, steps.size() - 1); k++) {
                addBody(cells, steps.get(k).pos());
            }
        }
        AABB area = null;
        for (long cell : cells) {
            AABB box = new AABB(BlockPos.of(cell));
            area = area == null ? box : area.minmax(box);
        }
        for (Mob other : level.getEntitiesOfClass(Mob.class, area.inflate(YIELD_RADIUS), m -> m != mob && m.isAlive() && m instanceof WaveMob)) {
            ((WaveMob) other).pathController().makeWay(cells, this, false);
        }
    }

    /**
     * Asking nicely ({@link #clearTheWay}) wasn't enough: squadmates in or near the work, where this mob stands, or the
     * next few steps are asked to get well clear and to find another way afterwards.
     */
    private void backOff(ServerLevel level, List<PathStep> steps) {
        LongSet core = new LongOpenHashSet();
        addBody(core, steps.get(index - 1).pos());
        addBody(core, feet());
        for (int k = index; k <= Math.min(index + 2, steps.size() - 1); k++) {
            PathStep step = steps.get(k);
            addBody(core, step.pos());
            step.breaks().forEach(pos -> core.add(pos.asLong()));
            step.places().forEach(pos -> core.add(pos.asLong()));
        }
        LongSet cells = new LongOpenHashSet();
        AABB area = null;
        for (long cell : core) {
            BlockPos pos = BlockPos.of(cell);
            for (BlockPos near : BlockPos.betweenClosed(pos.offset(-BACK_OFF_SPREAD, 0, -BACK_OFF_SPREAD), pos.offset(BACK_OFF_SPREAD, 0, BACK_OFF_SPREAD))) {
                cells.add(near.asLong());
            }
            AABB box = new AABB(pos);
            area = area == null ? box : area.minmax(box);
        }
        status = "asking squadmates to back off";
        for (Mob other : level.getEntitiesOfClass(Mob.class, area.inflate(BACK_OFF_RADIUS), m -> m != mob && m.isAlive() && m instanceof WaveMob)) {
            ((WaveMob) other).pathController().makeWay(cells, this, true);
        }
    }

    /** Whoever is standing in the spot is a squadmate that can be asked to move. */
    private boolean onlySquadmatesIn(ServerLevel level, BlockPos spot) {
        List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class, new AABB(spot), e -> e != mob);
        return !inside.isEmpty() && inside.stream().allMatch(e -> e instanceof WaveMob);
    }

    /**
     * Asked by a mob working on {@code cells}: if this mob is in them or about to walk into them, it moves aside and waits.
     * Asked to back right off ({@code far}), it looks further for room, waits longer and plans a new route afterwards.
     */
    public void makeWay(LongSet cells, MobPathController source, boolean far) {
        int mine = rank();
        int theirs = source.rank();
        if (mine > theirs || mine == theirs && mine > 0 && mob.getId() < source.mob.getId()) {
            return;
        }
        if (!standsIn(cells) && !routeCrosses(cells)) {
            return;
        }
        yieldCells = cells;
        yieldRoute = null;
        yieldHoldAt = null;
        yieldTicks = far ? BACK_OFF_TICKS : YIELD_TICKS;
        yieldRadius = far ? BACK_OFF_RADIUS : YIELD_RADIUS;
        if (far) {
            invalidate("backing off for a squadmate's work");
        }
    }

    private boolean standsIn(LongSet cells) {
        AABB body = mob.getBoundingBox();
        for (long cell : cells) {
            if (new AABB(BlockPos.of(cell)).intersects(body)) {
                return true;
            }
        }
        return false;
    }

    private boolean routeCrosses(LongSet cells) {
        if (path == null) {
            return false;
        }
        List<PathStep> steps = path.steps();
        for (int k = index; k < Math.min(steps.size(), index + 3); k++) {
            if (cells.contains(steps.get(k).pos().asLong())) {
                return true;
            }
        }
        return false;
    }

    /** Steps out of the cells, or holds still once out until the work is done. Returns false if it should carry on instead. */
    private boolean yieldWay() {
        if (--yieldTicks <= 0) {
            yieldCells = null;
            yieldRoute = null;
            if (movedAside) {
                movedAside = false;
                invalidate("made way, re-planning");
            }
            return false;
        }
        if (!mob.onGround() && !mob.isInWater() && !onClimbable()) {
            return false;
        }
        if (standsIn(yieldCells)) {
            if (yieldRoute == null) {
                yieldRoute = findYieldRoute();
            }
            if (yieldRoute == null) {
                return false;
            }
            while (yieldRoute.size() > 1 && feet().equals(yieldRoute.getFirst())) {
                yieldRoute.removeFirst();
            }
            status = "making way for a squadmate's work";
            movedAside = true;
            BlockPos next = yieldRoute.getFirst();
            if (onClimbable() && next.getX() == mob.getBlockX() && next.getZ() == mob.getBlockZ()) {
                // Along the ladder, up or down.
                climbColumn(next, next.getY() > mob.getBlockY());
                return true;
            }
            Vec3 to = Vec3.atBottomCenterOf(next);
            mob.getMoveControl().setWantedPosition(to.x, to.y, to.z, RUSH_SPEED);
            if (mob.horizontalCollision) {
                mob.getJumpControl().jump();
            }
            return true;
        }
        yieldRoute = null;
        status = "waiting for a squadmate's work";
        mob.getNavigation().stop();
        if (yieldHoldAt == null) {
            yieldHoldAt = feet();
        }
        if (onClimbable()) {
            hold(yieldHoldAt);
        } else {
            mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), SPEED);
        }
        return true;
    }

    /**
     * The way to the nearest spot with a floor that is out of the cells, walking (a block up or down at a time, ladders
     * included) rather than in a straight line, which could take the mob off the side of a stair or a ladder high up.
     * Null if there is none within reach.
     */
    private @Nullable List<BlockPos> findYieldRoute() {
        Level level = mob.level();
        BlockPos start = feet();
        Long2ObjectOpenHashMap<BlockPos> cameFrom = new Long2ObjectOpenHashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        cameFrom.put(start.asLong(), start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (pos != start && !yieldCells.contains(pos.asLong()) && !yieldCells.contains(pos.above().asLong())
                    && !BlockView.isOpen(level, pos.below()) && level.getFluidState(pos.below()).isEmpty()) {
                List<BlockPos> route = new ArrayList<>();
                for (BlockPos at = pos; at != start; at = cameFrom.get(at.asLong())) {
                    route.addFirst(at);
                }
                return route;
            }
            // Up or down a ladder it hangs on; off it sideways only where there is a floor (stepping off a ladder in
            // mid-air, the mob lets go and falls).
            if (BlockView.isClimbable(level.getBlockState(pos))) {
                for (BlockPos next : new BlockPos[]{pos.above(), pos.below()}) {
                    if (Math.abs(next.getY() - start.getY()) <= yieldRadius && !cameFrom.containsKey(next.asLong())
                            && BlockView.isClimbable(level.getBlockState(next)) && canStandIn(level, next)) {
                        cameFrom.put(next.asLong(), pos);
                        queue.add(next);
                    }
                }
            }
            if (!onFloor(level, pos)) {
                continue;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = pos.relative(direction).above(dy);
                    if (Math.abs(next.getX() - start.getX()) > yieldRadius || Math.abs(next.getZ() - start.getZ()) > yieldRadius
                            || Math.abs(next.getY() - start.getY()) > 1 || cameFrom.containsKey(next.asLong())
                            || !canStandIn(level, next) || !onFloor(level, next)
                            || !BlockView.isOpen(level, (dy > 0 ? pos : next).above(2))) {
                        continue;
                    }
                    cameFrom.put(next.asLong(), pos);
                    queue.add(next);
                }
            }
        }
        return null;
    }

    /** Something solid to stand on under this cell: not air, and not a ladder. */
    private static boolean onFloor(Level level, BlockPos pos) {
        return !BlockView.isOpen(level, pos.below());
    }

    /** Room for the body, out of fluids, and something holding it up: a floor, or a ladder. */
    private static boolean canStandIn(Level level, BlockPos pos) {
        return BlockView.isOpen(level, pos) && BlockView.isOpen(level, pos.above()) && level.getFluidState(pos).isEmpty()
                && (!BlockView.isOpen(level, pos.below()) || BlockView.isClimbable(level.getBlockState(pos)));
    }

    private boolean onClimbable() {
        return mob.onClimbable();
    }

    /** The block the mob's feet are in, stepping up out of partial blocks like farmland or slabs. */
    private BlockPos feet() {
        BlockPos pos = mob.blockPosition();
        return BlockView.isOpen(mob.level(), pos) ? pos : pos.above();
    }
}
