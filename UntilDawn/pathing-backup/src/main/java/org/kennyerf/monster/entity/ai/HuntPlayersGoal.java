package org.kennyerf.monster.entity.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.kennyerf.monster.config.MonsterConfig;
import org.kennyerf.monster.entity.WaveMob;
import org.kennyerf.monster.team.WaveTeam;

import java.util.EnumSet;

/**
 * Each mob picks its own player to attack, but as part of a team:
 * <ul>
 *     <li>it goes after players it can see, hear (within 12 blocks), or that a teammate has spotted recently,
 *     through walls if need be, since the pathfinder can dig/build/blast its way there</li>
 *     <li>it prefers players fewer teammates are already attacking, so the wave spreads out instead of
 *     the whole team chasing one player</li>
 *     <li>it reports every player it sees, alerting the rest of the team</li>
 * </ul>
 */
public class HuntPlayersGoal<T extends Mob & WaveMob> extends TargetGoal {
    private static final double CROWDING_PENALTY = 6.0;
    private static final double STICKINESS = 6.0;
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
        Player best = null;
        double bestScore = Double.MAX_VALUE;
        double huntRange = huntRange();
        double hearingRange = MonsterConfig.HEARING_RANGE.get();
        for (ServerPlayer player : level.players()) {
            if (!isValid(player)) {
                continue;
            }
            double distance = mob.distanceTo(player);
            if (distance > huntRange) {
                continue;
            }
            boolean sees = mob.getSensing().hasLineOfSight(player);
            if (sees) {
                WaveTeam.reportSighting(level, player);
            }
            if (!sees && distance > hearingRange && !WaveTeam.isKnown(level, player)) {
                continue;
            }
            double score = distance + CROWDING_PENALTY * WaveTeam.attackersOf(level, player, mob);
            if (player == mob.getTarget()) {
                score -= STICKINESS;
            }
            if (score < bestScore) {
                bestScore = score;
                best = player;
            }
        }
        return best;
    }

    private static boolean isValid(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && player.canBeSeenAsEnemy();
    }
}
