package org.kennyerf.monster.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.kennyerf.monster.Monster;

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
            Codec.INT.optionalFieldOf("pending_spawns", 0).forGetter(s -> s.pendingSpawns)
    ).apply(i, CoreState::new));

    // Null DataFixTypes is supported by Fabric API for modded saved data.
    public static final SavedDataType<CoreState> TYPE = new SavedDataType<>(Monster.id("core_state"), CoreState::new, CODEC, null);

    boolean active;
    ResourceKey<Level> dimension;
    BlockPos pos;
    int health;
    int night;
    boolean wasNight;
    int pendingSpawns;

    public CoreState() {
        this(false, Level.OVERWORLD, BlockPos.ZERO, WaveManager.DEFAULT_CORE_HEALTH, 0, false, 0);
    }

    private CoreState(boolean active, ResourceKey<Level> dimension, BlockPos pos, int health, int night, boolean wasNight, int pendingSpawns) {
        this.active = active;
        this.dimension = dimension;
        this.pos = pos;
        this.health = health;
        this.night = night;
        this.wasNight = wasNight;
        this.pendingSpawns = pendingSpawns;
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
}
