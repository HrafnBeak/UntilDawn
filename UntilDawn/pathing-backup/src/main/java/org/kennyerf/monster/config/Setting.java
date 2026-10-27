package org.kennyerf.monster.config;

import com.google.gson.JsonPrimitive;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Function;

/**
 * One named, typed, range-checked config value. Keys are {@code group.name}, e.g. {@code core.maxHealth}.
 */
public final class Setting<T> {
    /** Turns user text into a value, or returns an error message. */
    private interface Parser<T> {
        Result<T> parse(String text);
    }

    private record Result<T>(@Nullable T value, @Nullable String error) {
        static <T> Result<T> ok(T value) {
            return new Result<>(value, null);
        }

        static <T> Result<T> error(String error) {
            return new Result<>(null, error);
        }
    }

    private final String key;
    private final String description;
    private final T defaultValue;
    private final String range;
    private final Parser<T> parser;
    /** Display text; always valid input for {@link #set}. */
    private final Function<T, String> formatter;
    private final Function<T, JsonPrimitive> serializer;
    private T value;

    private Setting(String key, String description, T defaultValue, String range, Parser<T> parser,
                    Function<T, String> formatter, Function<T, JsonPrimitive> serializer) {
        if (key.indexOf('.') <= 0 || key.indexOf('.') != key.lastIndexOf('.')) {
            throw new IllegalArgumentException("Setting key must look like group.name: " + key);
        }
        this.key = key;
        this.description = description;
        this.defaultValue = defaultValue;
        this.range = range;
        this.parser = parser;
        this.formatter = formatter;
        this.serializer = serializer;
        this.value = defaultValue;
    }

    static Setting<Integer> ofInt(String key, String description, int defaultValue, int min, int max) {
        return new Setting<>(key, description, defaultValue, min + " to " + max, text -> {
            try {
                int v = Integer.parseInt(text.trim());
                return v < min || v > max ? Result.error("must be between " + min + " and " + max) : Result.ok(v);
            } catch (NumberFormatException e) {
                return Result.error("not a whole number: " + text);
            }
        }, String::valueOf, JsonPrimitive::new);
    }

    static Setting<Double> ofDouble(String key, String description, double defaultValue, double min, double max) {
        return new Setting<>(key, description, defaultValue, format(min) + " to " + format(max), text -> {
            try {
                double v = Double.parseDouble(text.trim());
                // Written so NaN fails the check too.
                return !(v >= min && v <= max) ? Result.error("must be between " + format(min) + " and " + format(max)) : Result.ok(v);
            } catch (NumberFormatException e) {
                return Result.error("not a number: " + text);
            }
        }, Setting::format, JsonPrimitive::new);
    }

    static Setting<Boolean> ofBool(String key, String description, boolean defaultValue) {
        return new Setting<>(key, description, defaultValue, "true/false", text -> switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes" -> Result.ok(true);
            case "false", "off", "no" -> Result.ok(false);
            default -> Result.error("must be true or false");
        }, String::valueOf, JsonPrimitive::new);
    }

    /** A block id such as {@code minecraft:cobblestone}. */
    static Setting<Identifier> ofBlock(String key, String description, String defaultValue) {
        return new Setting<>(key, description, Identifier.parse(defaultValue), "a block id", text -> {
            Identifier id = Identifier.tryParse(text.trim());
            if (id == null || BuiltInRegistries.BLOCK.getOptional(id).isEmpty()) {
                return Result.error("unknown block: " + text);
            }
            return Result.ok(id);
        }, Identifier::toString, id -> new JsonPrimitive(id.toString()));
    }

    public T get() {
        return value;
    }

    public String key() {
        return key;
    }

    /** The part of the key before the dot, e.g. {@code core}. */
    public String group() {
        return key.substring(0, key.indexOf('.'));
    }

    /** The part of the key after the dot, e.g. {@code maxHealth}. */
    public String name() {
        return key.substring(key.indexOf('.') + 1);
    }

    public String description() {
        return description;
    }

    public String range() {
        return range;
    }

    public String display() {
        return formatter.apply(value);
    }

    public String displayDefault() {
        return formatter.apply(defaultValue);
    }

    public boolean isDefault() {
        return value.equals(defaultValue);
    }

    /** Sets the value from user text. Returns an error message, or null on success. */
    public @Nullable String set(String text) {
        Result<T> result = parser.parse(text);
        if (result.error() != null) {
            return result.error();
        }
        value = result.value();
        return null;
    }

    public void reset() {
        value = defaultValue;
    }

    JsonPrimitive toJson() {
        return serializer.apply(value);
    }

    private static String format(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
