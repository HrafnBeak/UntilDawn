package org.kennyerf.untildawn.entity.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.entity.WaveMob;
import org.kennyerf.untildawn.squad.Squad;
import org.kennyerf.untildawn.squad.Squads;

import java.util.EnumSet;

/**
 * Each mob picks its own player to attack, but as part of its squad:
 * <ul>
 *     <li>it goes after players it can see, hear ({@code ai.hearingRange}), or that a squadmate has spotted
 *     recently, through walls if need be, since the squad can dig/build/blast its way there</li>
 *     <li>it leans towards the player its squad is going after, keeping the squad together</li>
 *     <li>it prefers players fewer wave mobs are already attacking, so separate squads spread out</li>
 *     <li>it reports every player it sees to its squad</li>
 * </ul>
 */
public class HuntPlayersGoal<T extends Mob & WaveMob> extends TargetGoal {
    private static final double CROWDING_PENALTY = 6.0;
    private static final double STICKINESS = 6.0;
    private static final double SQUAD_BONUS = 8.0;
    private static final double CROWD_RADIUS = 64.0;
    private static final int SCAN_TICKS = 20;

    private final T waveMob;
    private @Nullable Player candidate;
    private int nextScan;

    public HuntPlayersGoal(T mob) {
        super(mob, false);
        this.waveMob = mob;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    private double huntRange() {
        return waveMob.settings().huntRange.get();
    }

    @Override
    public boolean canUse() {
        if (mob.tickCount < nextScan) {
            return false;
        }
        scheduleScan();
        candidate = choose();
        return candidate != null && candidate != mob.getTarget();
    }

    @Override
    public void start() {
        mob.setTarget(candidate);
        targetMob = candidate;
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getTarget() instanceof Player player && isValid(player)
                && mob.distanceToSqr(player) <= huntRange() * huntRange() * 1.5;
    }

    @Override
    public void tick() {
        if (mob.tickCount < nextScan) {
            return;
        }
        scheduleScan();
        Player better = choose();
        if (better != null && better != mob.getTarget()) {
            mob.setTarget(better);
            targetMob = better;
        }
    }

    private void scheduleScan() {
        nextScan = mob.tickCount + SCAN_TICKS + mob.getRandom().nextInt(10);
    }

    private @Nullable Player choose() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        Squad squad = Squads.of(mob);
        Player squadTarget = squad != null ? squad.goalPlayer() : null;
        Player best = null;
        double bestScore = Double.MAX_VALUE;
        double huntRange = huntRange();
        double hearingRange = UntilDawnConfig.HEARING_RANGE.get();
        for (ServerPlayer player : level.players()) {
            if (!isValid(player)) {
                continue;
            }
            double distance = mob.distanceTo(player);
            if (distance > huntRange) {
                continue;
            }
            boolean sees = mob.getSensing().hasLineOfSight(player);
            if (sees && squad != null) {
                squad.reportSighting(player);
            }
            if (!sees && distance > hearingRange && (squad == null || !squad.knows(player))) {
                continue;
            }
            double score = distance + CROWDING_PENALTY * attackersOf(level, player);
            if (player == mob.getTarget()) {
                score -= STICKINESS;
            }
            if (player == squadTarget) {
                score -= SQUAD_BONUS;
            }
            if (score < bestScore) {
                bestScore = score;
                best = player;
            }
        }
        return best;
    }

    /** How many other wave mobs near the player are already going after it. */
    private int attackersOf(ServerLevel level, Player player) {
        return level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(CROWD_RADIUS),
                other -> other != mob && other instanceof WaveMob && other.getTarget() == player).size();
    }

    private static boolean isValid(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && player.canBeSeenAsEnemy();
    }
}
