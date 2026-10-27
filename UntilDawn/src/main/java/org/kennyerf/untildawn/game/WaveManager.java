package org.kennyerf.untildawn.game;

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
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.config.SquadCompositions;
import org.kennyerf.untildawn.entity.WaveMob;
import org.kennyerf.untildawn.entity.WaveMobs;
import org.kennyerf.untildawn.pathing.PathBudget;
import org.kennyerf.untildawn.registry.ModBlocks;
import org.kennyerf.untildawn.registry.ModEntities;
import org.kennyerf.untildawn.registry.ModItems;
import org.kennyerf.untildawn.squad.Squads;

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
    public static final String TEAM_NAME = "untildawn_wave";
    /** Entity tag for mobs spawned by a wave night; they are removed when day comes. */
    public static final String WAVE_TAG = "untildawn.wave";

    private static final int NIGHT_START = 13000;
    private static final int NIGHT_END = 23000;
    private static final int DAY_LENGTH = 24000;
    /** Extra ticks the clock gains every server tick while the Core screen is skipping to night. */
    private static final int SKIP_TICKS_PER_TICK = 120;

    private static final ServerBossEvent BOSS_BAR = new ServerBossEvent(
            UUID.fromString("5f1c3b7e-6d2a-4c1e-9a7b-8e0d2c4f6a10"),
            Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);

    private WaveManager() {
    }

    public static void init() {
        BOSS_BAR.setVisible(false);
        ServerTickEvents.START_SERVER_TICK.register(server -> PathBudget.resetForTick());
        ServerTickEvents.END_SERVER_TICK.register(WaveManager::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> hideBossBar());

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
                CoreState state = CoreState.get(level.getServer());
                if (isTaggedWaveMob(entity) && state.waveAlive > 0) {
                    // Killed, or blew itself up.
                    state.waveAlive--;
                    state.setDirty();
                }
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

        // Scrap comes only from mobs a wave sent, never from spawn eggs or vanilla mobs.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.level() instanceof ServerLevel level && isTaggedWaveMob(entity)
                    && level.getGameRules().get(GameRules.MOB_DROPS)
                    && level.getRandom().nextDouble() * 100.0 < UntilDawnConfig.SCRAP_CHANCE.get()) {
                entity.spawnAtLocation(level, ModItems.SCRAP);
            }
        });

        // Sleeping would skip wave nights entirely.
        EntitySleepEvents.ALLOW_SLEEPING.register((player, sleepingPos) ->
                !UntilDawnConfig.ALLOW_SLEEPING.get() && player.level() instanceof ServerLevel level && CoreState.get(level.getServer()).active
                        ? new Player.BedSleepingProblem(Component.translatable("message.untildawn.no_sleep"))
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

    /** Clock ticks until the next night starts (0 if it is night); the clock runs faster while the night is being skipped to. */
    public static long ticksUntilNight(ServerLevel level) {
        long dayTime = level.getOverworldClockTime() % DAY_LENGTH;
        if (dayTime >= NIGHT_START && dayTime < NIGHT_END) {
            return 0;
        }
        return dayTime < NIGHT_START ? NIGHT_START - dayTime : DAY_LENGTH - dayTime + NIGHT_START;
    }

    /** Whether the daylight is being sped up towards night (see the Core screen). */
    public static boolean isSkippingToNight(ServerLevel level) {
        return CoreState.get(level.getServer()).skippingToNight;
    }

    /** How many clock ticks pass per server tick while the night is being skipped to. */
    public static int skipRate() {
        return SKIP_TICKS_PER_TICK + 1;
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
                player.sendSystemMessage(Component.translatable("message.untildawn.core_exists",
                        existing.getX(), existing.getY(), existing.getZ()).withStyle(ChatFormatting.RED));
            }
            return;
        }

        state.active = true;
        state.dimension = level.dimension();
        state.pos = pos.immutable();
        state.health = UntilDawnConfig.CORE_MAX_HEALTH.get();
        state.night = 0;
        state.nextNight = 0;
        // Only the *next* night counts; placing the Core mid-night does not start a wave.
        state.wasNight = isNight(level);
        state.pendingSpawns = 0;
        state.setDirty();

        clearArea(level, pos);
        updateSpawnPoints(level.getServer(), state);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.untildawn.core_placed").withStyle(ChatFormatting.GOLD), false);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private static void endGame(MinecraftServer server, CoreState state, ServerLevel level) {
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.untildawn.core_destroyed", Math.max(0, state.wasNight ? state.night - 1 : state.night))
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
        state.waveSize = 0;
        state.waveAlive = 0;
        state.setDirty();
        hideBossBar();
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
        if (corePos(level) != null) {
            damageCore(level, CoreState.get(level.getServer()), UntilDawnConfig.CORE_BOMBER_DAMAGE.get());
        }
    }

    // ---------------------------------------------------------------- ticking

    private static void tick(MinecraftServer server) {
        CoreState state = CoreState.get(server);
        if (!state.active) {
            if (BOSS_BAR.isVisible()) {
                hideBossBar();
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

        if (state.skippingToNight) {
            if (isNight(level)) {
                state.skippingToNight = false;
            } else {
                server.registryAccess().get(WorldClocks.OVERWORLD)
                        .ifPresent(clock -> server.clockManager().addTicks(clock, SKIP_TICKS_PER_TICK));
            }
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
            if (night) {
                checkWaveRemaining(server, level, state);
            }
            clearArea(level, state.pos);
            int intruders = level.getEntitiesOfClass(Mob.class, coreArea(state.pos),
                    mob -> mob instanceof WaveMob && mob.isAlive()).size();
            int damage = intruders * UntilDawnConfig.CORE_DAMAGE_PER_MOB.get();
            if (damage > 0) {
                damageCore(level, state, damage);
            }
            if (state.health > UntilDawnConfig.CORE_MAX_HEALTH.get()) {
                // core.maxHealth was lowered mid-game.
                state.health = UntilDawnConfig.CORE_MAX_HEALTH.get();
                state.setDirty();
            }
            updateSpawnPoints(server, state);
            updateBossBar(server, state, night);
            CoreInterface.refreshViewers(level, state);
        }
    }

    private static void startNight(MinecraftServer server, ServerLevel level, CoreState state) {
        state.night = state.upcomingNight();
        state.nextNight = 0;
        state.pendingSpawns = waveSize(state.night);
        state.waveSize = state.pendingSpawns;
        state.waveAlive = 0;
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.untildawn.night_start", state.night, state.pendingSpawns)
                        .withStyle(ChatFormatting.RED), false);
        for (ServerPlayer player : level.players()) {
            level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.5F, 1.2F);
        }
    }

    private static void endNight(MinecraftServer server, ServerLevel level, CoreState state) {
        state.pendingSpawns = 0;
        state.waveSize = 0;
        state.waveAlive = 0;
        despawnWaveMobs(level);
        if (state.night > 0) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.untildawn.night_survived", state.night).withStyle(ChatFormatting.GREEN), false);
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
    public static int waveSize(int night) {
        int size = UntilDawnConfig.WAVE_BASE_SIZE.get() + (night - 1) * UntilDawnConfig.WAVE_GROWTH.get();
        return Math.min(size, UntilDawnConfig.WAVE_MAX_SIZE.get());
    }

    /** Ticks between squads. */
    private static int squadInterval(int night) {
        return Math.max(40, 120 - night * 6);
    }

    /**
     * Spawns one of the configured squad compositions together at one spot, leaving out mob types that haven't
     * appeared yet on this night, and forms them into a squad (several, if there are more than {@code squads.maxSize}).
     * Returns how many were spawned.
     */
    private static int spawnSquad(ServerLevel level, CoreState state) {
        RandomSource random = level.getRandom();
        int minDistance = UntilDawnConfig.SPAWN_DISTANCE_MIN.get();
        int maxDistance = Math.max(minDistance, UntilDawnConfig.SPAWN_DISTANCE_MAX.get());
        BlockPos rally = null;
        for (int attempt = 0; attempt < 16 && rally == null; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            rally = surfaceSpawnPos(level,
                    Mth.floor(state.pos.getX() + Math.cos(angle) * distance),
                    Mth.floor(state.pos.getZ() + Math.sin(angle) * distance));
        }
        if (rally == null) {
            return 0;
        }

        List<EntityType<? extends Mob>> composition = pickComposition(state.night, random);
        int size = Math.min(state.pendingSpawns, composition.size());
        List<Mob> squad = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            BlockPos pos = i == 0 ? rally : surfaceSpawnPos(level, rally.getX() + random.nextInt(5) - 2, rally.getZ() + random.nextInt(5) - 2);
            Mob mob = pos != null ? spawnMember(level, composition.get(i), pos) : null;
            if (mob != null) {
                squad.add(mob);
            }
        }
        if (!squad.isEmpty()) {
            Squads.formSpread(level, squad, UntilDawnConfig.SQUAD_MAX_SIZE.get());
            state.waveAlive += squad.size();
        }
        return squad.size();
    }

    /** A random configured squad, without the mob types that don't appear yet; a lone Miner if nothing is left. */
    public static List<EntityType<? extends Mob>> pickComposition(int night, RandomSource random) {
        List<List<EntityType<? extends Mob>>> options = new ArrayList<>();
        for (List<String> names : SquadCompositions.current()) {
            List<EntityType<? extends Mob>> squad = new ArrayList<>();
            for (String name : names) {
                if (night >= settingsOf(name).firstNight.get()) {
                    squad.add(typeOf(name));
                }
            }
            if (!squad.isEmpty()) {
                options.add(squad);
            }
        }
        return options.isEmpty() ? List.of(ModEntities.MINER) : options.get(random.nextInt(options.size()));
    }

    private static EntityType<? extends Mob> typeOf(String name) {
        return switch (name) {
            case "miner" -> ModEntities.MINER;
            case "builder" -> ModEntities.BUILDER;
            case "bomber" -> ModEntities.BOMBER;
            case "longbowman" -> ModEntities.LONGBOWMAN;
            case "sprinter" -> ModEntities.SPRINTER;
            case "swarmer" -> ModEntities.SWARMER;
            case "warrior" -> ModEntities.WARRIOR;
            case "assassin" -> ModEntities.ASSASSIN;
            case "webber" -> ModEntities.WEBBER;
            case "sneaking_spider" -> ModEntities.SNEAKING_SPIDER;
            default -> ModEntities.ARCHER;
        };
    }

    public static UntilDawnConfig.MobSettings settingsOf(String name) {
        return switch (name) {
            case "miner" -> UntilDawnConfig.MINER;
            case "builder" -> UntilDawnConfig.BUILDER;
            case "bomber" -> UntilDawnConfig.BOMBER;
            case "longbowman" -> UntilDawnConfig.LONGBOWMAN;
            case "sprinter" -> UntilDawnConfig.SPRINTER;
            case "swarmer" -> UntilDawnConfig.SWARMER;
            case "warrior" -> UntilDawnConfig.WARRIOR;
            case "assassin" -> UntilDawnConfig.ASSASSIN;
            case "webber" -> UntilDawnConfig.WEBBER;
            case "sneaking_spider" -> UntilDawnConfig.SNEAKING_SPIDER;
            default -> UntilDawnConfig.ARCHER;
        };
    }

    /** The share of tonight's wave still to beat, alive or not yet spawned, from 0 to 1. */
    public static double remainingFraction(int waveSize, int pending, int alive) {
        return waveSize <= 0 ? 1.0 : Math.min(1.0, (pending + alive) / (double) waveSize);
    }

    /**
     * End of a wave: below {@code waves.glowPercent}, or with {@code waves.glowCount} mobs or fewer left, the last mobs
     * glow so players can find them; below {@code waves.endPercent} the rest despawn and the night skips to morning.
     */
    private static void checkWaveRemaining(MinecraftServer server, ServerLevel level, CoreState state) {
        if (state.waveSize <= 0) {
            return;
        }
        double remaining = remainingFraction(state.waveSize, state.pendingSpawns, state.waveAlive) * 100.0;
        if (remaining < UntilDawnConfig.END_PERCENT.get()) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.untildawn.wave_cleared").withStyle(ChatFormatting.GOLD), false);
            state.pendingSpawns = 0;
            despawnWaveMobs(level);
            // The next tick sees the night end as usual.
            skipToDay(server);
            state.setDirty();
        } else if (remaining < UntilDawnConfig.GLOW_PERCENT.get()
                || state.pendingSpawns + state.waveAlive <= UntilDawnConfig.GLOW_COUNT.get()) {
            glowWaveMobs(level);
        }
    }

    /** Moves the overworld clock forward to the next morning, like {@code /time set day}. */
    public static void skipToDay(MinecraftServer server) {
        server.registryAccess().get(WorldClocks.OVERWORLD)
                .ifPresent(clock -> server.clockManager().moveToTimeMarker(clock, ClockTimeMarkers.DAY));
    }

    /** Makes every living wave mob in the level glow, so players can find the last few. */
    public static void glowWaveMobs(ServerLevel level) {
        for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), mob -> isTaggedWaveMob(mob) && mob.isAlive())) {
            if (!mob.hasEffect(MobEffects.GLOWING)) {
                mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
            }
        }
    }

    private static @Nullable BlockPos surfaceSpawnPos(ServerLevel level, int x, int z) {
        if (!level.isLoaded(new BlockPos(x, 0, z))) {
            return null;
        }
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        BlockState ground = level.getBlockState(pos.below());
        return ground.getFluidState().isEmpty() && !ground.getCollisionShape(level, pos.below()).isEmpty() ? pos : null;
    }

    private static @Nullable Mob spawnMember(ServerLevel level, EntityType<? extends Mob> type, BlockPos pos) {
        Mob mob = type.create(level, EntitySpawnReason.EVENT);
        if (mob == null) {
            return null;
        }
        mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(mob)) {
            return null;
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        mob.addTag(WAVE_TAG);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        return mob;
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

    private static void hideBossBar() {
        BOSS_BAR.removeAllPlayers();
        BOSS_BAR.setVisible(false);
    }

    private static void updateBossBar(MinecraftServer server, CoreState state, boolean night) {
        if (night) {
            BOSS_BAR.setName(Component.translatable("bossbar.untildawn.night", state.night, state.health, UntilDawnConfig.CORE_MAX_HEALTH.get()));
            BOSS_BAR.setColor(BossEvent.BossBarColor.RED);
        } else {
            BOSS_BAR.setName(Component.translatable("bossbar.untildawn.day", state.upcomingNight(), state.health, UntilDawnConfig.CORE_MAX_HEALTH.get()));
            BOSS_BAR.setColor(BossEvent.BossBarColor.YELLOW);
        }
        BOSS_BAR.setProgress(Mth.clamp(state.health / (float) UntilDawnConfig.CORE_MAX_HEALTH.get(), 0.0F, 1.0F));
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
            team.setDisplayName(Component.translatable("team.untildawn.wave"));
        }
        team.setAllowFriendlyFire(false);
        team.setSeeFriendlyInvisibles(true);
        return team;
    }
}
