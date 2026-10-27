package org.kennyerf.untildawn.debug;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.kennyerf.untildawn.UntilDawn;

import java.util.List;

/**
 * The paths a player is watching ({@code /untildawn path show} and {@code predict}), for their client to draw with
 * Baritone. Sent whenever they change; an empty list clears the drawing.
 */
public record PathOverlayPayload(List<Path> paths) implements CustomPacketPayload {
    public static final Type<PathOverlayPayload> TYPE = new Type<>(UntilDawn.id("path_overlay"));

    /**
     * @param positions feet positions, from the step being walked on
     * @param breaks    blocks still to break along it
     * @param places    blocks still to place along it
     * @param complete  the path reaches its goal (otherwise it gets as close as it can)
     */
    public record Path(List<BlockPos> positions, List<BlockPos> breaks, List<BlockPos> places, boolean complete) {
        private static final StreamCodec<ByteBuf, List<BlockPos>> POSITIONS = BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list());
        static final StreamCodec<ByteBuf, Path> CODEC = StreamCodec.composite(
                POSITIONS, Path::positions,
                POSITIONS, Path::breaks,
                POSITIONS, Path::places,
                ByteBufCodecs.BOOL, Path::complete,
                Path::new);
    }

    public static final StreamCodec<ByteBuf, PathOverlayPayload> CODEC =
            Path.CODEC.apply(ByteBufCodecs.list()).map(PathOverlayPayload::new, PathOverlayPayload::paths);

    @Override
    public Type<PathOverlayPayload> type() {
        return TYPE;
    }
}
