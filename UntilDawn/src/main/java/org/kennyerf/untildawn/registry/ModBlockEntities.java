package org.kennyerf.untildawn.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.kennyerf.untildawn.UntilDawn;
import org.kennyerf.untildawn.workbench.WorkbenchBlockEntity;

import java.util.Set;

public final class ModBlockEntities {
    public static final BlockEntityType<WorkbenchBlockEntity> WORKBENCH = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            UntilDawn.id("workbench"), new BlockEntityType<>(WorkbenchBlockEntity::new, Set.of(ModBlocks.WORKBENCH)));

    private ModBlockEntities() {
    }

    public static void init() {
        // Registered by loading the class.
    }
}
