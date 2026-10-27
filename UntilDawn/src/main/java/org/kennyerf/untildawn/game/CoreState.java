package org.kennyerf.untildawn.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.kennyerf.untildawn.UntilDawn;

/**
 * World-wide state of the wave game, saved with the overworld.
 */
public class CoreState extends SavedData {
    public static final Codec<CoreState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("active", false).forGetter(s -> s.active),
            ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("dimension", Level.OVERWORLD).forGetter(s -> s.dimension),
            BlockPos.CODEC.optionalFieldOf("pos", BlockPos.ZERO).forGetter(s -> s.pos),
            Codec.INT.optionalFieldOf("health", WaveManager.DEFAULT_CORE_HEALTH).forGetter(s -> s.health),
            Codec.INT.optionalFieldOf("night", 0).forGetter(s -> s.night),
            Codec.BOOL.optionalFieldOf("was_night", false).forGetter(s -> s.wasNight),
            Codec.INT.optionalFieldOf("pending_spawns", 0).forGetter(s -> s.pendingSpawns),
            Codec.INT.optionalFieldOf("wave_size", 0).forGetter(s -> s.waveSize),
            Codec.INT.optionalFieldOf("wave_alive", 0).forGetter(s -> s.waveAlive),
            Codec.INT.optionalFieldOf("next_night", 0).forGetter(s -> s.nextNight)
    ).apply(i, CoreState::new));

    // Null DataFixTypes is supported by Fabric API for modded saved data.
    public static final SavedDataType<CoreState> TYPE = new SavedDataType<>(UntilDawn.id("core_state"), CoreState::new, CODEC, null);

    boolean active;
    ResourceKey<Level> dimension;
    BlockPos pos;
    int health;
    int night;
    boolean wasNight;
    int pendingSpawns;
    /** Mobs in tonight's wave, and how many of those spawned are still alive. */
    int waveSize;
    int waveAlive;
    /** The number the next night will have, set with {@code /untildawn night}; 0 = the one after the last. */
    int nextNight;
    /** Set from the Core screen: the daylight is sped up until night comes. Not saved. */
    boolean skippingToNight;

    public CoreState() {
        this(false, Level.OVERWORLD, BlockPos.ZERO, WaveManager.DEFAULT_CORE_HEALTH, 0, false, 0, 0, 0, 0);
    }

    private CoreState(boolean active, ResourceKey<Level> dimension, BlockPos pos, int health, int night, boolean wasNight,
                      int pendingSpawns, int waveSize, int waveAlive, int nextNight) {
        this.active = active;
        this.dimension = dimension;
        this.pos = pos;
        this.health = health;
        this.night = night;
        this.wasNight = wasNight;
        this.pendingSpawns = pendingSpawns;
        this.waveSize = waveSize;
        this.waveAlive = waveAlive;
        this.nextNight = nextNight;
    }

    public static CoreState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isActive() {
        return active;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos pos() {
        return pos;
    }

    /** The number the coming (or, by day, the next) night's wave will have. */
    public int upcomingNight() {
        return nextNight > 0 ? nextNight : night + 1;
    }

    /** Makes the next night that starts night {@code number}; the nights after it count on from there. */
    public void setNextNight(int number) {
        nextNight = number;
        setDirty();
    }
}
