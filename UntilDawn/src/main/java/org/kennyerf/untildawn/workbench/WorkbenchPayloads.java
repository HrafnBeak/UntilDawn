package org.kennyerf.untildawn.workbench;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.kennyerf.untildawn.UntilDawn;

import java.util.List;

/** The Workbench's messages. The server owns the tree and the research; the client only shows them and asks. */
public final class WorkbenchPayloads {
    private WorkbenchPayloads() {
    }

    /**
     * A bench's tree and what it has researched, to the players using it.
     *
     * @param open open the bench's screen (otherwise only refresh it, if it is already open)
     */
    public record State(BlockPos pos, List<TechNode> nodes, List<Identifier> researched, boolean open) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(UntilDawn.id("workbench_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, State::pos,
                TechNode.STREAM_CODEC.apply(ByteBufCodecs.list()), State::nodes,
                Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), State::researched,
                ByteBufCodecs.BOOL, State::open,
                State::new);

        @Override
        public Type<State> type() {
            return TYPE;
        }
    }

    /** A player's request to research (or, with {@code craft}, to craft) one item at a bench. */
    public record Action(BlockPos pos, Identifier node, boolean craft) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(UntilDawn.id("workbench_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Action::pos,
                Identifier.STREAM_CODEC, Action::node,
                ByteBufCodecs.BOOL, Action::craft,
                Action::new);

        @Override
        public Type<Action> type() {
            return TYPE;
        }
    }
}
