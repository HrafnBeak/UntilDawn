package org.kennyerf.untildawn.config;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parses {@code squads.compositions}: squads separated by {@code ;}, each a list of mob names joined by {@code +},
 * e.g. {@code miner+archer+archer; builder+bomber+archer}.
 */
public final class SquadCompositions {
    public static final Set<String> MOB_NAMES = Set.of("miner", "builder", "bomber", "archer", "longbowman", "sprinter", "swarmer", "warrior",
            "assassin", "webber", "sneaking_spider");
    private static final int MAX_SQUAD = 50;

    private static String parsedFrom = "";
    private static List<List<String>> parsed = List.of();

    private SquadCompositions() {
    }

    /** Returns an error message, or null if the text is a valid list of squads. */
    static @Nullable String validate(String text) {
        try {
            parse(text);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    /** The configured squads as lists of mob names. Cached until the setting changes. */
    public static List<List<String>> current() {
        String text = UntilDawnConfig.SQUAD_COMPOSITIONS.get();
        if (!text.equals(parsedFrom)) {
            parsed = parse(text);
            parsedFrom = text;
        }
        return parsed;
    }

    private static List<List<String>> parse(String text) {
        List<List<String>> squads = new ArrayList<>();
        for (String squadText : text.split(";")) {
            if (squadText.isBlank()) {
                continue;
            }
            List<String> squad = new ArrayList<>();
            for (String name : squadText.split("\\+")) {
                String mob = name.trim().toLowerCase(Locale.ROOT);
                if (!MOB_NAMES.contains(mob)) {
                    throw new IllegalArgumentException("unknown mob '" + name.trim() + "' (use " + String.join(", ", MOB_NAMES) + ")");
                }
                squad.add(mob);
            }
            if (squad.size() > MAX_SQUAD) {
                throw new IllegalArgumentException("a squad can have at most " + MAX_SQUAD + " mobs");
            }
            squads.add(List.copyOf(squad));
        }
        if (squads.isEmpty()) {
            throw new IllegalArgumentException("list at least one squad, e.g. miner+archer+archer");
        }
        return List.copyOf(squads);
    }
}
