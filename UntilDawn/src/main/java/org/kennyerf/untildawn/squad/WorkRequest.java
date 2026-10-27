package org.kennyerf.untildawn.squad;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.pathing.BlockView;
import org.kennyerf.untildawn.pathing.PathStep;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A squadmate asking for help: "dig this tunnel" or "build this bridge / stair". The work is a stretch of
 * the asker's own planned path, so whoever answers simply walks to its start and follows it, doing the work.
 */
public final class WorkRequest {
    public enum Kind {
        /** Break blocks (Miners dig, Bombers blast). */
        DIG,
        /** Place Unstable Planks or ladders (Builders). */
        BUILD
    }

    private static int nextId = 1;

    private final int id;
    private final Kind kind;
    private final List<PathStep> segment;
    private final LongSet cells;
    private final Set<UUID> waiting = new HashSet<>();
    private final long created;
    private @Nullable UUID responder;
    private boolean closed;

    WorkRequest(Kind kind, List<PathStep> segment, UUID asker, long now) {
        this.id = nextId++;
        this.kind = kind;
        this.segment = List.copyOf(segment);
        this.cells = cellsOf(kind, segment);
        this.waiting.add(asker);
        this.created = now;
    }

    /** The cells of the given kind of work in a stretch of path. */
    public static LongSet cellsOf(Kind kind, List<PathStep> steps) {
        LongSet cells = new LongOpenHashSet();
        for (PathStep step : steps) {
            if (kind == Kind.DIG) {
                step.breaks().forEach(pos -> cells.add(pos.asLong()));
            } else {
                step.places().forEach(pos -> cells.add(pos.asLong()));
            }
        }
        return cells;
    }

    public int id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    /** The stretch of path to follow; {@code segment.get(0)} is where the asker waits. */
    public List<PathStep> segment() {
        return segment;
    }

    public BlockPos site() {
        return segment.getFirst().pos();
    }

    public LongSet cells() {
        return LongSets.unmodifiable(cells);
    }

    public long created() {
        return created;
    }

    public @Nullable UUID responder() {
        return responder;
    }

    void setResponder(@Nullable UUID responder) {
        this.responder = responder;
    }

    Set<UUID> waiting() {
        return waiting;
    }

    public boolean isClosed() {
        return closed;
    }

    void close() {
        closed = true;
    }

    boolean overlaps(LongSet other) {
        for (long cell : other) {
            if (cells.contains(cell)) {
                return true;
            }
        }
        return false;
    }

    /** Every block of the work is done (by anyone). */
    public boolean isDone(ServerLevel level) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (PathStep step : segment) {
            if (kind == Kind.DIG) {
                for (BlockPos pos : step.breaks()) {
                    if (!BlockView.isOpen(level, pos)) {
                        return false;
                    }
                }
            } else {
                for (BlockPos pos : step.places()) {
                    if (level.getBlockState(cursor.set(pos)).canBeReplaced()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** A Bomber can only answer if every block is weak enough to blast. */
    boolean isBlastable(ServerLevel level) {
        for (long cell : cells) {
            BlockState state = level.getBlockState(BlockPos.of(cell));
            if (!state.isAir() && !BlockView.isBlastable(state)) {
                return false;
            }
        }
        return true;
    }
}
