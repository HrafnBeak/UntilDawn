package org.kennyerf.untildawn.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import org.kennyerf.untildawn.UntilDawn;

import java.util.List;

public final class ModComponents {
    /**
     * The tech tree nodes a Workbench has researched. Held by the bench's block entity, and copied onto its item when it
     * is broken (see its loot table), so a bench keeps its research when it is moved.
     */
    public static final DataComponentType<List<Identifier>> RESEARCH = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            UntilDawn.id("research"),
            DataComponentType.<List<Identifier>>builder()
                    .persistent(Identifier.CODEC.listOf())
                    .networkSynchronized(Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()))
                    .build());

    private ModComponents() {
    }

    public static void init() {
        // Registered by loading the class.
    }
}
