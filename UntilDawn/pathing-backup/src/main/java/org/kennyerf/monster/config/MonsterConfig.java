package org.kennyerf.monster.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.kennyerf.monster.Monster;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side settings, stored in {@code config/monster.json5} and editable in game with {@code /monster config}.
 * <p>
 * The file is JSON with comments, one object per group: {@code {"core": {"maxHealth": 100}}}. Flat keys
 * ({@code "core.maxHealth": 100}) are read as well.
 */
public final class MonsterConfig {
    public static final String FILE_NAME = Monster.MOD_ID + ".json5";

    private static final Map<String, Setting<?>> SETTINGS = new LinkedHashMap<>();

    // ---------------------------------------------------------------- Core
    public static final Setting<Integer> CORE_MAX_HEALTH = add(Setting.ofInt("core.maxHealth",
            "Core health; a running game keeps its current health, capped at the new maximum", 100, 1, 100_000));
    public static final Setting<Integer> CORE_DAMAGE_PER_MOB = add(Setting.ofInt("core.damagePerMob",
            "Core damage per second for each mob inside the Core area", 1, 0, 1000));
    public static final Setting<Integer> CORE_BOMBER_DAMAGE = add(Setting.ofInt("core.bomberDamage",
            "Extra Core damage when a Bomber detonates inside the Core area", 10, 0, 100_000));
    public static final Setting<Boolean> ALLOW_SLEEPING = add(Setting.ofBool("core.allowSleeping",
            "Allow sleeping while a Core is active (sleeping skips wave nights)", false));

    // ---------------------------------------------------------------- waves
    public static final Setting<Integer> WAVE_BASE_SIZE = add(Setting.ofInt("waves.baseSize",
            "Mobs on night 1", 4, 1, 1000));
    public static final Setting<Integer> WAVE_GROWTH = add(Setting.ofInt("waves.growthPerNight",
            "Extra mobs each following night", 3, 0, 1000));
    public static final Setting<Integer> WAVE_MAX_SIZE = add(Setting.ofInt("waves.maxSize",
            "Upper limit on mobs per night", 80, 1, 2000));
    public static final Setting<Integer> MAX_SQUAD_SIZE = add(Setting.ofInt("waves.maxSquadSize",
            "Largest squad spawned together (squads start at 2 and grow with the nights)", 5, 1, 50));
    public static final Setting<Integer> SPAWN_DISTANCE_MIN = add(Setting.ofInt("waves.spawnDistanceMin",
            "Closest a squad spawns to the Core, in blocks", 24, 4, 200));
    public static final Setting<Integer> SPAWN_DISTANCE_MAX = add(Setting.ofInt("waves.spawnDistanceMax",
            "Furthest a squad spawns from the Core, in blocks (treated as spawnDistanceMin if lower)", 40, 4, 200));

    // ---------------------------------------------------------------- AI and pathing
    public static final Setting<Double> HEARING_RANGE = add(Setting.ofDouble("ai.hearingRange",
            "Mobs notice players this close even without line of sight", 12.0, 0.0, 128.0));
    public static final Setting<Integer> MAX_FALL = add(Setting.ofInt("pathing.maxFall",
            "Highest drop mobs plan to take", 3, 0, 32));
    public static final Setting<Integer> NODES_PER_SEARCH = add(Setting.ofInt("pathing.nodesPerSearch",
            "Path search size limit per mob (bigger = smarter over long distances, slower)", 3000, 100, 50_000));
    public static final Setting<Integer> NODES_PER_TICK = add(Setting.ofInt("pathing.nodesPerTick",
            "Path search limit for all mobs together, per tick (protects server performance)", 12_000, 100, 500_000));
    public static final Setting<Integer> SEARCH_TIME_LIMIT = add(Setting.ofInt("pathing.searchTimeLimitMs",
            "Longest a single path search may run, in milliseconds (it then returns the best partial path)", 10, 1, 1000));

    // ---------------------------------------------------------------- specialists
    public static final Setting<Double> MINER_DIG_SPEED = add(Setting.ofDouble("miner.digSpeed",
            "Miner digging speed multiplier (2 = twice as fast)", 1.0, 0.05, 50.0));
    public static final Setting<Integer> BUILDER_PLACE_DELAY = add(Setting.ofInt("builder.placeDelay",
            "Ticks a Builder waits between placing blocks (20 ticks = 1 second)", 4, 0, 400));
    public static final Setting<Identifier> BUILDER_BLOCK = add(Setting.ofBlock("builder.block",
            "Block Builders place", "minecraft:cobblestone"));
    public static final Setting<Integer> BOMBER_EXPLOSION_RADIUS = add(Setting.ofInt("bomber.explosionRadius",
            "Bomber explosion size (vanilla creeper = 3)", 3, 0, 32));
    public static final Setting<Integer> BOMBER_FUSE = add(Setting.ofInt("bomber.fuseTicks",
            "Ticks from ignition to explosion (vanilla creeper = 30)", 30, 1, 400));

    // ---------------------------------------------------------------- per mob
    public static final MobSettings MINER = new MobSettings("miner", 64.0, true, 0, 0);
    public static final MobSettings BUILDER = new MobSettings("builder", 64.0, true, 0, 0);
    public static final MobSettings BOMBER = new MobSettings("bomber", 16.0, false, 0, 0);
    public static final MobSettings ARCHER = new MobSettings("archer", 64.0, true, 15.0, 1.6);
    public static final MobSettings LONGBOWMAN = new MobSettings("longbowman", 64.0, true, 48.0, 3.0);

    private MonsterConfig() {
    }

    /** Settings shared by every mob type, plus bow settings for the skeletons. */
    public static final class MobSettings {
        public final Setting<Double> speed;
        public final Setting<Double> health;
        public final Setting<Double> huntRange;
        public final @Nullable Setting<Double> damage;
        public final @Nullable Setting<Double> range;
        public final @Nullable Setting<Double> arrowSpeed;

        private MobSettings(String mob, double huntRange, boolean hasDamage, double bowRange, double arrowSpeed) {
            this.speed = add(Setting.ofDouble(mob + ".speed", "Movement speed multiplier", 1.0, 0.05, 10.0));
            this.health = add(Setting.ofDouble(mob + ".health", "Max health multiplier", 1.0, 0.05, 100.0));
            this.huntRange = add(Setting.ofDouble(mob + ".huntRange", "How far away it goes after known players", huntRange, 0.0, 256.0));
            this.damage = hasDamage ? add(Setting.ofDouble(mob + ".damage", "Attack damage multiplier", 1.0, 0.0, 100.0)) : null;
            this.range = bowRange > 0 ? add(Setting.ofDouble(mob + ".range", "Distance it starts shooting from", bowRange, 2.0, 128.0)) : null;
            this.arrowSpeed = arrowSpeed > 0 ? add(Setting.ofDouble(mob + ".arrowSpeed", "Arrow speed (vanilla skeleton = 1.6)", arrowSpeed, 0.2, 10.0)) : null;
        }
    }

    private static <T> Setting<T> add(Setting<T> setting) {
        if (SETTINGS.putIfAbsent(setting.key(), setting) != null) {
            throw new IllegalStateException("Duplicate setting " + setting.key());
        }
        return setting;
    }

    public static Collection<Setting<?>> all() {
        return Collections.unmodifiableCollection(SETTINGS.values());
    }

    /** Settings by group, groups in the order they were first declared (e.g. every {@code miner.*} together). */
    public static Map<String, List<Setting<?>>> groups() {
        Map<String, List<Setting<?>>> groups = new LinkedHashMap<>();
        for (Setting<?> setting : SETTINGS.values()) {
            groups.computeIfAbsent(setting.group(), g -> new ArrayList<>()).add(setting);
        }
        return groups;
    }

    public static @Nullable Setting<?> get(String key) {
        return SETTINGS.get(key);
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    // ---------------------------------------------------------------- reading

    /**
     * Reads the config file; settings missing from it go back to their defaults. Returns the problems found
     * (they are logged too). If the file cannot be parsed, every setting keeps its current value.
     * <p>
     * The file is rewritten (adding new settings, refreshing comments) only after a clean read, so a typo
     * is never silently replaced by a default.
     */
    public static List<String> load() {
        Path file = file();
        if (!Files.exists(file)) {
            SETTINGS.values().forEach(Setting::reset);
            save();
            return List.of();
        }

        Map<String, JsonElement> values = new LinkedHashMap<>();
        try {
            // JsonParser is lenient, so the // comments are accepted.
            JsonElement root = JsonParser.parseString(Files.readString(file));
            if (!root.isJsonObject()) {
                throw new JsonParseException("expected { ... } at the top level");
            }
            flatten("", root.getAsJsonObject(), values);
        } catch (IOException | JsonParseException e) {
            String problem = "Could not read " + FILE_NAME + ", keeping the current settings: " + e.getMessage();
            Monster.LOGGER.error(problem);
            return List.of(problem);
        }

        SETTINGS.values().forEach(Setting::reset);
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            Setting<?> setting = SETTINGS.get(entry.getKey());
            if (setting == null) {
                problems.add("Unknown setting " + entry.getKey());
                continue;
            }
            JsonElement value = entry.getValue();
            String error = value.isJsonPrimitive() ? setting.set(value.getAsString()) : "expected a single value";
            if (error != null) {
                problems.add(entry.getKey() + ": " + error + " (using the default, " + setting.displayDefault() + ")");
            }
        }

        if (problems.isEmpty()) {
            save();
        } else {
            problems.forEach(problem -> Monster.LOGGER.warn("{}: {}", FILE_NAME, problem));
            Monster.LOGGER.warn("Left {} as it is; fix it and run /monster config reload", file);
        }
        return problems;
    }

    /** {@code {"core": {"maxHealth": 1}}} and {@code {"core.maxHealth": 1}} both become {@code core.maxHealth -> 1}. */
    private static void flatten(String prefix, JsonObject object, Map<String, JsonElement> out) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = prefix + entry.getKey();
            if (entry.getValue().isJsonObject()) {
                flatten(key + ".", entry.getValue().getAsJsonObject(), out);
            } else {
                out.put(key, entry.getValue());
            }
        }
    }

    // ---------------------------------------------------------------- writing

    /** Writes every setting to the config file. Returns false (and logs why) if it could not be written. */
    public static boolean save() {
        Path file = file();
        String text = render();
        try {
            if (Files.exists(file) && Files.readString(file).equals(text)) {
                return true;
            }
            Files.createDirectories(file.getParent());
            // Write a temporary file and swap it in, so a crash mid-write can't leave a truncated config.
            Path temp = file.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(temp, text);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            Monster.LOGGER.error("Could not save {}", file, e);
            return false;
        }
    }

    private static String render() {
        StringBuilder out = new StringBuilder()
                .append("// Monster settings. Change them in game with /monster config,\n")
                .append("// or edit this file and run /monster config reload.\n")
                .append("// Remove a line to put that setting back to its default.\n")
                .append("{\n");
        List<Map.Entry<String, List<Setting<?>>>> groups = List.copyOf(groups().entrySet());
        for (int g = 0; g < groups.size(); g++) {
            out.append("  ").append(new JsonPrimitive(groups.get(g).getKey())).append(": {\n");
            List<Setting<?>> settings = groups.get(g).getValue();
            for (int s = 0; s < settings.size(); s++) {
                Setting<?> setting = settings.get(s);
                out.append("    // ").append(setting.description()).append('\n')
                        .append("    // ").append(setting.range()).append(", default ").append(setting.displayDefault()).append('\n')
                        .append("    ").append(new JsonPrimitive(setting.name())).append(": ").append(setting.toJson())
                        .append(s + 1 < settings.size() ? ",\n" : "\n");
            }
            out.append(g + 1 < groups.size() ? "  },\n\n" : "  }\n");
        }
        return out.append("}\n").toString();
    }
}
