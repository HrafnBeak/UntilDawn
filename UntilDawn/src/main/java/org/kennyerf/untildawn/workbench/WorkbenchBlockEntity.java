package org.kennyerf.untildawn.workbench;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.kennyerf.untildawn.registry.ModBlockEntities;
import org.kennyerf.untildawn.registry.ModComponents;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds what a Workbench has researched. Research belongs to the bench, not the player: anyone using it can craft what
 * it knows. Breaking the bench keeps the research on the dropped item ({@link ModComponents#RESEARCH}), and placing it
 * again restores it.
 */
public class WorkbenchBlockEntity extends BlockEntity {
    private static final String RESEARCH_KEY = "research";

    private final Set<Identifier> researched = new LinkedHashSet<>();

    public WorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WORKBENCH, pos, state);
    }

    public boolean isResearched(Identifier node) {
        return researched.contains(node);
    }

    public List<Identifier> researched() {
        return List.copyOf(researched);
    }

    public void research(Identifier node) {
        if (researched.add(node)) {
            setChanged();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        researched.clear();
        input.read(RESEARCH_KEY, Identifier.CODEC.listOf()).ifPresent(researched::addAll);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store(RESEARCH_KEY, Identifier.CODEC.listOf(), List.copyOf(researched));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        researched.clear();
        researched.addAll(components.getOrDefault(ModComponents.RESEARCH, List.of()));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!researched.isEmpty()) {
            components.set(ModComponents.RESEARCH, List.copyOf(researched));
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard(RESEARCH_KEY);
    }
}
