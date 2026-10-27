package org.kennyerf.monster.team;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.kennyerf.monster.entity.WaveMob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The wave's shared knowledge. Every mob still decides for itself, but through this blackboard they:
 * <ul>
 *     <li>share sightings, so one mob spotting a player alerts the rest of the team</li>
 *     <li>claim the blocks they are about to break or place, so specialists don't duplicate work
 *     and teammates plan routes through the tunnel/bridge being made and follow behind</li>
 * </ul>
 */
public final class WaveTeam {
    private static final int CLAIM_TICKS = 120;
    private static final int SIGHTING_TICKS = 200;

    private record Claim(int entityId, long expires) {
    }

    private static final class LevelTeam {
        final Long2ObjectOpenHashMap<Claim> breakClaims = new Long2ObjectOpenHashMap<>();
        final Long2ObjectOpenHashMap<Claim> placeClaims = new Long2ObjectOpenHashMap<>();
        final Map<UUID, Long> sightings = new HashMap<>();
    }

    private static final Map<ResourceKey<Level>, LevelTeam> TEAMS = new HashMap<>();

    private WaveTeam() {
    }

    private static LevelTeam team(ServerLevel level) {
        return TEAMS.computeIfAbsent(level.dimension(), k -> new LevelTeam());
    }

    public static void clear() {
        TEAMS.clear();
    }

    // ---------------------------------------------------------------- work claims

    /** Claims a block to break. Returns false if a teammate already has it. */
    public static boolean claimBreak(ServerLevel level, BlockPos pos, Entity mob) {
        return claim(level, team(level).breakClaims, pos, mob);
    }

    public static boolean claimPlace(ServerLevel level, BlockPos pos, Entity mob) {
        return claim(level, team(level).placeClaims, pos, mob);
    }

    public static boolean isBreakClaimedByOther(ServerLevel level, BlockPos pos, Entity mob) {
        return claimedByOther(level, team(level).breakClaims, pos.asLong(), mob);
    }

    public static boolean isPlaceClaimedByOther(ServerLevel level, BlockPos pos, Entity mob) {
        return claimedByOther(level, team(level).placeClaims, pos.asLong(), mob);
    }

    public static boolean isBreakClaimedByOther(ServerLevel level, long pos, Entity mob) {
        return claimedByOther(level, team(level).breakClaims, pos, mob);
    }

    public static boolean isPlaceClaimedByOther(ServerLevel level, long pos, Entity mob) {
        return claimedByOther(level, team(level).placeClaims, pos, mob);
    }

    public static void releaseAll(ServerLevel level, Entity mob) {
        LevelTeam team = team(level);
        team.breakClaims.values().removeIf(c -> c.entityId == mob.getId());
        team.placeClaims.values().removeIf(c -> c.entityId == mob.getId());
    }

    public static void release(ServerLevel level, BlockPos pos) {
        LevelTeam team = team(level);
        team.breakClaims.remove(pos.asLong());
        team.placeClaims.remove(pos.asLong());
    }

    private static boolean claim(ServerLevel level, Long2ObjectOpenHashMap<Claim> claims, BlockPos pos, Entity mob) {
        long key = pos.asLong();
        if (claimedByOther(level, claims, key, mob)) {
            return false;
        }
        claims.put(key, new Claim(mob.getId(), level.getGameTime() + CLAIM_TICKS));
        return true;
    }

    private static boolean claimedByOther(ServerLevel level, Long2ObjectOpenHashMap<Claim> claims, long key, Entity mob) {
        Claim claim = claims.get(key);
        if (claim == null || claim.entityId == mob.getId()) {
            return false;
        }
        if (claim.expires < level.getGameTime() || !(level.getEntity(claim.entityId) instanceof Mob owner) || !owner.isAlive()) {
            claims.remove(key);
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- shared sightings

    public static void reportSighting(ServerLevel level, Player player) {
        team(level).sightings.put(player.getUUID(), level.getGameTime());
    }

    /** True if any teammate has seen this player recently. */
    public static boolean isKnown(ServerLevel level, Player player) {
        Long seen = team(level).sightings.get(player.getUUID());
        return seen != null && level.getGameTime() - seen <= SIGHTING_TICKS;
    }

    /** How many other wave mobs near the player are already going after it. */
    public static int attackersOf(ServerLevel level, Player player, Mob except) {
        return level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64.0),
                mob -> mob != except && mob instanceof WaveMob && mob.getTarget() == player).size();
    }
}
