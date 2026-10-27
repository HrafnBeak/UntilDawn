package org.kennyerf.monster.game;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;
import org.kennyerf.monster.config.MonsterConfig;
import org.kennyerf.monster.entity.WaveMob;
import org.kennyerf.monster.entity.WaveMobs;
import org.kennyerf.monster.pathing.PathBudget;
import org.kennyerf.monster.team.WaveTeam;
import org.kennyerf.monster.registry.ModBlocks;
import org.kennyerf.monster.registry.ModEntities;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WaveManager {
    /** Health a saved game without a stored value starts from; the live maximum is {@code core.maxHealth}. */
    public static final int DEFAULT_CORE_HEALTH = 100;
    /** Horizontal reach of the Core area in every direction (5x5 footprint). */
    public static final int AREA_RADIUS = 2;
    /** Blocks above the Core's Y level that are part of the area (Core level + 3 = 4 tall). */
    public static final int AREA_HEIGHT = 3;
    public static final String TEAM_NAME = "monster_wave";
    /** Entity tag for mobs spawned by a wave night; they are removed when day comes. */
    public static final String WAVE_TAG = "monster.wave";

    private static final int NIGHT_START = 13000;
    private static final int NIGHT_END = 23000;
    private static final int DAY_LENGTH = 24000;

    private static final ServerBossEvent BOSS_BAR = new ServerBossEvent(
            UUID.fromString("5f1c3b7e-6d2a-4c1e-9a7b-8e0d2c4f6a10"),
            Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);

    private WaveManager() {
    }

    public static void init() {
        BOSS_BAR.setVisible(false);
        ServerTickEvents.START_SERVER_TICK.register(server -> PathBudget.resetForTick());
        ServerTickEvents.END_SERVER_TICK.register(WaveManager::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            BOSS_BAR.removeAllPlayers();
            BOSS_BAR.setVisible(false);
            WaveTeam.clear();
        });

        ServerEntityEvents.ALLOW_LOAD.register((entity, level, spawnReason, isLoadedFromDisk) -> {
            CoreState state = CoreState.get(level.getServer());
            if (!state.active) {
                // Leftover wave mobs from a finished game are dropped as their chunks load.
                return !isTaggedWaveMob(entity);
            }
            if (entity instanceof WaveMob) {
                // Wave mobs saved in a chunk that unloaded overnight should not survive into the day.
                return !(isLoadedFromDisk && isTaggedWaveMob(entity) && !isNight(level));
            }
            // While the Core is active, the mod's own waves replace vanilla mob spawning.
            return !(entity instanceof Mob && spawnReason != null && switch (spawnReason) {
                case NATURAL, CHUNK_GENERATION, PATROL, REINFORCEMENT -> true;
                default -> false;
            });
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof WaveMob && entity instanceof Mob mob) {
                Scoreboard scoreboard = level.getScoreboard();
                scoreboard.addPlayerToTeam(entity.getScoreboardName(), getOrCreateTeam(scoreboard));
                WaveMobs.applyConfig(mob);
            }
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            Entity.RemovalReason reason = entity.getRemovalReason();
            if (entity instanceof WaveMob && reason != null && reason.shouldDestroy()) {
                Scoreboard scoreboard = level.getScoreboard();
                PlayerTeam team = scoreboard.getPlayersTeam(entity.getScoreboardName());
                if (team != null && team.getName().equals(TEAM_NAME)) {
                    scoreboard.removePlayerFromTeam(entity.getScoreboardName(), team);
                }
            }
        });

        // Friendly fire off: wave mobs never hurt each other (stray arrows, Bomber explosions, ...).
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof WaveMob && source.getEntity() instanceof WaveMob));

        // Sleeping would skip wave nights entirely.
        EntitySleepEvents.ALLOW_SLEEPING.register((player, sleepingPos) ->
                !MonsterConfig.ALLOW_SLEEPING.get() && player.level() instanceof ServerLevel level && CoreState.get(level.getServer()).active
                        ? new Player.BedSleepingProblem(Component.translatable("message.monster.no_sleep"))
                        : null);
    }

    // ---------------------------------------------------------------- queries used by mob AI

    /** The Core position if a game is running in this level. */
    public static @Nullable BlockPos corePos(ServerLevel level) {
        CoreState state = CoreState.get(level.getServer());
        return state.active && state.dimension.equals(level.dimension()) ? state.pos : null;
    }

    public static AABB coreArea(BlockPos core) {
        return new AABB(
                core.getX() - AREA_RADIUS, core.getY(), core.getZ() - AREA_RADIUS,
                core.getX() + AREA_RADIUS + 1, core.getY() + AREA_HEIGHT + 1, core.getZ() + AREA_RADIUS + 1);
    }

    public static boolean isInCoreArea(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos core = corePos(level);
        return core != null && coreArea(core).intersects(entity.getBoundingBox());
    }

    public static boolean isNight(ServerLevel level) {
        long dayTime = level.getOverworldClockTime() % DAY_LENGTH;
        return dayTime >= NIGHT_START && dayTime < NIGHT_END;
    }

    private static boolean isTaggedWaveMob(Entity entity) {
        return entity.entityTags().contains(WAVE_TAG);
    }

    // ---------------------------------------------------------------- Core lifecycle

    public static void onCorePlaced(ServerLevel level, BlockPos pos, @Nullable LivingEntity placer) {
        CoreState state = CoreState.get(level.getServer());
        if (state.active) {
            level.destroyBlock(pos, true);
            if (placer instanceof ServerPlayer player) {
                BlockPos existing = state.pos;
                player.sendSystemMessage(Component.translatable("message.monster.core_exists",
                        existing.getX(), existing.getY(), existing.getZ()).withStyle(ChatFormatting.RED));
            }
            return;
        }

        state.active = true;
        state.dimension = level.dimension();
        state.pos = pos.immutable();
        state.health = MonsterConfig.CORE_MAX_HEALTH.get();
        state.night = 0;
        // Only the *next* night counts; placing the Core mid-night does not start a wave.
        state.wasNight = isNight(level);
        state.pendingSpawns = 0;
        state.setDirty();

        clearArea(level, pos);
        updateSpawnPoints(level.getServer(), state);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.monster.core_placed").withStyle(ChatFormatting.GOLD), false);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private static void endGame(MinecraftServer server, CoreState state, ServerLevel level) {
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.monster.core_destroyed", Math.max(0, state.wasNight ? state.night - 1 : state.night))
                        .withStyle(ChatFormatting.DARK_RED), false);
        despawnWaveMobs(level);

        ServerPlayer.RespawnConfig coreSpawn = coreRespawnConfig(state);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (coreSpawn.isSamePosition(player.getRespawnConfig())) {
                player.setRespawnPosition(null, false);
            }
        }

        state.active = false;
        state.pendingSpawns = 0;
        state.setDirty();
        WaveTeam.clear();
        BOSS_BAR.removeAllPlayers();
        BOSS_BAR.setVisible(false);
    }

    private static void damageCore(ServerLevel level, CoreState state, int amount) {
        state.health = Math.max(0, state.health - amount);
        state.setDirty();
        BlockPos pos = state.pos;
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                Math.min(amount, 10), 0.3, 0.2, 0.3, 0.1);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 0.6F, 1.4F);
        if (state.health <= 0) {
            level.destroyBlock(pos, false);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    1, 0.0, 0.0, 0.0, 0.0);
            level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4.0F, 0.8F);
        }
    }

    /** Called when a Bomber blows itself up inside the Core area. */
    public static void onBomberDetonatedAtCore(ServerLevel level) {
        CoreState state = CoreState.get(level.getServer());
        if (state.active && state.dimension.equals(level.dimension())) {
            damageCore(level, state, MonsterConfig.CORE_BOMBER_DAMAGE.get());
        }
    }

    // ---------------------------------------------------------------- ticking

    private static void tick(MinecraftServer server) {
        CoreState state = CoreState.get(server);
        if (!state.active) {
            if (BOSS_BAR.isVisible()) {
                BOSS_BAR.removeAllPlayers();
                BOSS_BAR.setVisible(false);
            }
            return;
        }

        ServerLevel level = server.getLevel(state.dimension);
        if (level == null || !level.isLoaded(state.pos)) {
            // The Core's chunk is not loaded: the game is paused until someone comes back.
            updateBossBar(server, state, level != null && isNight(level));
            return;
        }

        if (!level.getBlockState(state.pos).is(ModBlocks.CORE) || state.health <= 0) {
            endGame(server, state, level);
            return;
        }

        boolean night = isNight(level);
        if (night != state.wasNight) {
            state.wasNight = night;
            if (night) {
                startNight(server, level, state);
            } else {
                endNight(server, level, state);
            }
            state.setDirty();
        }

        int tick = server.getTickCount();
        if (night && state.pendingSpawns > 0 && tick % squadInterval(state.night) == 0) {
            int spawned = spawnSquad(level, state);
            if (spawned > 0) {
                state.pendingSpawns = Math.max(0, state.pendingSpawns - spawned);
                state.setDirty();
            }
        }

        if (tick % 10 == 0) {
            drawAreaOutline(level, state.pos);
        }

        if (tick % 20 == 0) {
            clearArea(level, state.pos);
            int intruders = level.getEntitiesOfClass(Mob.class, coreArea(state.pos),
                    mob -> mob instanceof WaveMob && mob.isAlive()).size();
            int damage = intruders * MonsterConfig.CORE_DAMAGE_PER_MOB.get();
            if (damage > 0) {
                damageCore(level, state, damage);
            }
            if (state.health > MonsterConfig.CORE_MAX_HEALTH.get()) {
                // core.maxHealth was lowered mid-game.
                state.health = MonsterConfig.CORE_MAX_HEALTH.get();
                state.setDirty();
            }
            updateSpawnPoints(server, state);
            updateBossBar(server, state, night);
        }
    }

    private static void startNight(MinecraftServer server, ServerLevel level, CoreState state) {
        state.night++;
        state.pendingSpawns = waveSize(state.night);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.monster.night_start", state.night, state.pendingSpawns)
                        .withStyle(ChatFormatting.RED), false);
        for (ServerPlayer player : level.players()) {
            level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.5F, 1.2F);
        }
    }

    private static void endNight(MinecraftServer server, ServerLevel level, CoreState state) {
        state.pendingSpawns = 0;
        despawnWaveMobs(level);
        WaveTeam.clear();
        if (state.night > 0) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.monster.night_survived", state.night).withStyle(ChatFormatting.GREEN), false);
        }
    }

    private static void despawnWaveMobs(ServerLevel level) {
        List<Mob> mobs = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(Mob.class), WaveManager::isTaggedWaveMob, mobs);
        for (Mob mob : mobs) {
            level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 1.0, mob.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
            mob.discard();
        }
    }

    // ---------------------------------------------------------------- waves

    /** Nights progressively bring more enemies. */
    static int waveSize(int night) {
        int size = MonsterConfig.WAVE_BASE_SIZE.get() + (night - 1) * MonsterConfig.WAVE_GROWTH.get();
        return Math.min(size, MonsterConfig.WAVE_MAX_SIZE.get());
    }

    /** Ticks between squads. */
    private static int squadInterval(int night) {
        return Math.max(40, 120 - night * 6);
    }

    /** Every squad is built around a specialist that opens the way for the others. */
    private static EntityType<? extends Mob> pickSpecialist(int night, RandomSource random) {
        if (night < 2) {
            return ModEntities.MINER;
        }
        return switch (random.nextInt(3)) {
            case 0 -> ModEntities.MINER;
            case 1 -> ModEntities.BUILDER;
            default -> ModEntities.BOMBER;
        };
    }

    private static EntityType<? extends Mob> pickMobType(int night, RandomSource random) {
        int archer = 3;
        int miner = 3;
        int builder = night >= 2 ? 2 : 0;
        int bomber = night >= 2 ? 2 : 0;
        int longbow = night >= 3 ? 1 + night / 5 : 0;
        int roll = random.nextInt(archer + miner + builder + bomber + longbow);
        if ((roll -= archer) < 0) return ModEntities.ARCHER;
        if ((roll -= miner) < 0) return ModEntities.MINER;
        if ((roll -= builder) < 0) return ModEntities.BUILDER;
        if ((roll -= bomber) < 0) return ModEntities.BOMBER;
        return ModEntities.LONGBOWMAN;
    }

    /** Spawns a squad of 2-5 mobs together at one spot. Returns how many were spawned. */
    private static int spawnSquad(ServerLevel level, CoreState state) {
        RandomSource random = level.getRandom();
        BlockPos rally = null;
        for (int attempt = 0; attempt < 16 && rally == null; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int minDistance = MonsterConfig.SPAWN_DISTANCE_MIN.get();
            int maxDistance = Math.max(minDistance, MonsterConfig.SPAWN_DISTANCE_MAX.get());
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            rally = surfaceSpawnPos(level,
                    Mth.floor(state.pos.getX() + Math.cos(angle) * distance),
                    Mth.floor(state.pos.getZ() + Math.sin(angle) * distance));
        }
        if (rally == null) {
            return 0;
        }

        // Squads grow with the nights: 2 at first, up to waves.maxSquadSize.
        int maxSquad = MonsterConfig.MAX_SQUAD_SIZE.get();
        int squad = Math.min(maxSquad, 2 + random.nextInt(1 + Math.min(Math.max(0, maxSquad - 2), state.night / 2)));
        int size = Math.min(state.pendingSpawns, squad);
        int spawned = 0;
        for (int i = 0; i < size; i++) {
            EntityType<? extends Mob> type = i == 0 ? pickSpecialist(state.night, random) : pickMobType(state.night, random);
            BlockPos pos = i == 0 ? rally : surfaceSpawnPos(level, rally.getX() + random.nextInt(5) - 2, rally.getZ() + random.nextInt(5) - 2);
            if (pos != null && spawnMember(level, type, pos)) {
                spawned++;
            }
        }
        return spawned;
    }

    private static @Nullable BlockPos surfaceSpawnPos(ServerLevel level, int x, int z) {
        if (!level.isLoaded(new BlockPos(x, 0, z))) {
            return null;
        }
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        BlockState ground = level.getBlockState(pos.below());
        return ground.getFluidState().isEmpty() && !ground.getCollisionShape(level, pos.below()).isEmpty() ? pos : null;
    }

    private static boolean spawnMember(ServerLevel level, EntityType<? extends Mob> type, BlockPos pos) {
        Mob mob = type.create(level, EntitySpawnReason.EVENT);
        if (mob == null) {
            return false;
        }
        mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(mob)) {
            return false;
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        mob.addTag(WAVE_TAG);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        return true;
    }

    // ---------------------------------------------------------------- Core area upkeep

    /** Destroys every block at the Core's level and up to 3 above it, 2 blocks out in every direction. */
    private static void clearArea(ServerLevel level, BlockPos core) {
        for (BlockPos pos : BlockPos.betweenClosed(
                core.offset(-AREA_RADIUS, 0, -AREA_RADIUS), core.offset(AREA_RADIUS, AREA_HEIGHT, AREA_RADIUS))) {
            if (pos.equals(core)) {
                continue;
            }
            BlockState blockState = level.getBlockState(pos);
            if (blockState.isAir() || blockState.getDestroySpeed(level, pos) < 0) {
                continue;
            }
            if (blockState.getBlock() instanceof LiquidBlock) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            } else {
                level.destroyBlock(pos, true);
            }
        }
    }

    private static void drawAreaOutline(ServerLevel level, BlockPos core) {
        AABB box = coreArea(core);
        double step = 0.5;
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        for (double y : ys) {
            for (double z : zs) {
                for (double x = box.minX; x <= box.maxX; x += step) {
                    edgeParticle(level, x, y, z);
                }
            }
            for (double x : xs) {
                for (double z = box.minZ; z <= box.maxZ; z += step) {
                    edgeParticle(level, x, y, z);
                }
            }
        }
        for (double x : xs) {
            for (double z : zs) {
                for (double y = box.minY; y <= box.maxY; y += step) {
                    edgeParticle(level, x, y, z);
                }
            }
        }
    }

    private static void edgeParticle(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private static ServerPlayer.RespawnConfig coreRespawnConfig(CoreState state) {
        BlockPos spawn = state.pos.south();
        return new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(state.dimension, spawn, 0.0F, 0.0F), true);
    }

    /** Keeps every player's spawn point inside the Core area while the game runs. */
    private static void updateSpawnPoints(MinecraftServer server, CoreState state) {
        ServerPlayer.RespawnConfig coreSpawn = coreRespawnConfig(state);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!coreSpawn.isSamePosition(player.getRespawnConfig())) {
                player.setRespawnPosition(coreSpawn, false);
            }
        }
    }

    private static void updateBossBar(MinecraftServer server, CoreState state, boolean night) {
        if (night) {
            BOSS_BAR.setName(Component.translatable("bossbar.monster.night", state.night, state.health, MonsterConfig.CORE_MAX_HEALTH.get()));
            BOSS_BAR.setColor(BossEvent.BossBarColor.RED);
        } else {
            BOSS_BAR.setName(Component.translatable("bossbar.monster.day", state.night + 1, state.health, MonsterConfig.CORE_MAX_HEALTH.get()));
            BOSS_BAR.setColor(BossEvent.BossBarColor.YELLOW);
        }
        BOSS_BAR.setProgress(Mth.clamp(state.health / (float) MonsterConfig.CORE_MAX_HEALTH.get(), 0.0F, 1.0F));
        BOSS_BAR.setVisible(true);

        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        for (ServerPlayer player : List.copyOf(BOSS_BAR.getPlayers())) {
            if (!online.contains(player)) {
                BOSS_BAR.removePlayer(player);
            }
        }
        for (ServerPlayer player : online) {
            if (!BOSS_BAR.getPlayers().contains(player)) {
                BOSS_BAR.addPlayer(player);
            }
        }
    }

    private static PlayerTeam getOrCreateTeam(Scoreboard scoreboard) {
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.addPlayerTeam(TEAM_NAME);
            team.setDisplayName(Component.translatable("team.monster.wave"));
        }
        team.setAllowFriendlyFire(false);
        team.setSeeFriendlyInvisibles(true);
        return team;
    }
}
