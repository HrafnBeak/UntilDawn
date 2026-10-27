package org.kennyerf.untildawn.workbench;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.UntilDawn;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Workbench's tech tree, as the server's datapacks define it (one {@link TechNode} file each, in
 * {@code data/<namespace>/untildawn/tech_tree/}). It is reloaded with {@code /reload}, and sent to a player's client
 * whenever they open a bench, so the client never needs its own copy.
 */
public final class TechTree {
    private static final FileToIdConverter FILES = FileToIdConverter.json("untildawn/tech_tree");

    /** In display order: parents before children, siblings by {@link TechNode#order()} then id. */
    private static volatile Map<Identifier, TechNode> nodes = Map.of();

    private TechTree() {
    }

    public static void init() {
        DataResourceLoader.get().registerReloadListener(UntilDawn.id("tech_tree"), Loader::new);
    }

    public static @Nullable TechNode get(Identifier id) {
        return nodes.get(id);
    }

    public static List<TechNode> all() {
        return List.copyOf(nodes.values());
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<Identifier, TechNode>> {
        // Registry-aware, so ingredients can name item tags.
        private final DynamicOps<JsonElement> ops;

        Loader(HolderLookup.Provider registries) {
            this.ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        }

        @Override
        protected Map<Identifier, TechNode> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<Identifier, TechNode> loaded = new HashMap<>();
            for (Map.Entry<Identifier, Resource> entry : FILES.listMatchingResources(manager).entrySet()) {
                Identifier id = FILES.fileToId(entry.getKey());
                try (Reader reader = entry.getValue().openAsReader()) {
                    TechNode.FILE_CODEC.parse(ops, JsonParser.parseReader(reader))
                            .ifSuccess(node -> loaded.put(id, node.withId(id)))
                            .ifError(error -> UntilDawn.LOGGER.error("Couldn't load tech tree item {}: {}", id, error.message()));
                } catch (Exception e) {
                    UntilDawn.LOGGER.error("Couldn't load tech tree item {}", id, e);
                }
            }
            return loaded;
        }

        @Override
        protected void apply(Map<Identifier, TechNode> loaded, ResourceManager manager, ProfilerFiller profiler) {
            nodes = order(loaded);
            UntilDawn.LOGGER.info("Loaded {} tech tree items", nodes.size());
        }
    }

    /** Drops items whose parent is missing (or that are their own ancestor), and orders the rest top-down. */
    private static Map<Identifier, TechNode> order(Map<Identifier, TechNode> loaded) {
        Map<Identifier, List<TechNode>> children = new HashMap<>();
        List<TechNode> roots = new ArrayList<>();
        for (TechNode node : loaded.values()) {
            if (node.parent().isEmpty()) {
                roots.add(node);
            } else if (loaded.containsKey(node.parent().get())) {
                children.computeIfAbsent(node.parent().get(), k -> new ArrayList<>()).add(node);
            } else {
                UntilDawn.LOGGER.warn("Tech tree item {} has unknown parent {}; it is left out", node.id(), node.parent().get());
            }
        }
        Comparator<TechNode> siblings = Comparator.comparingInt(TechNode::order).thenComparing(n -> n.id().toString());
        Map<Identifier, TechNode> ordered = new LinkedHashMap<>();
        List<TechNode> queue = new ArrayList<>(roots);
        queue.sort(siblings);
        // Walking down from the roots never reaches a cycle, so items in one are left out too.
        for (int i = 0; i < queue.size(); i++) {
            TechNode node = queue.get(i);
            ordered.put(node.id(), node);
            List<TechNode> kids = new ArrayList<>(children.getOrDefault(node.id(), List.of()));
            kids.sort(siblings);
            queue.addAll(kids);
        }
        for (TechNode node : loaded.values()) {
            if (!ordered.containsKey(node.id()) && node.parent().isPresent() && loaded.containsKey(node.parent().get())) {
                UntilDawn.LOGGER.warn("Tech tree item {} is part of a parent loop; it is left out", node.id());
            }
        }
        return ordered;
    }
}
