package org.kennyerf.untildawn.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.kennyerf.untildawn.UntilDawn;

import java.util.List;

/** The Core screen's messages. The server owns the game; the client only shows its state and asks. */
public final class CorePayloads {
    /** What the buttons of the Core screen ask for. */
    public static final int START_NIGHT = 0;
    public static final int REPAIR = 1;

    /** The wave mobs the encyclopedia describes, in the order it lists them (their ids, as in {@code squads.compositions}). */
    public static final List<String> MOBS = List.of("miner", "builder", "bomber", "archer", "longbowman", "sprinter",
            "swarmer", "warrior", "assassin", "webber", "sneaking_spider");

    private CorePayloads() {
    }

    /**
     * The Core's state, to the players using it.
     *
     * @param upcomingNight the number of the next night, and {@code upcomingSize} how many mobs it will send
     * @param skipping      the daylight is already being sped up towards night
     * @param open          open the screen (otherwise only refresh it, if it is already open)
     * @param known         the {@link #MOBS} the encyclopedia may tell about: those whose first night has ended
     */
    public record State(BlockPos pos, int upcomingNight, int upcomingSize, int health, int maxHealth, boolean night,
                        boolean skipping, boolean open, List<String> known) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(UntilDawn.id("core_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, State::pos,
                ByteBufCodecs.VAR_INT, State::upcomingNight,
                ByteBufCodecs.VAR_INT, State::upcomingSize,
                ByteBufCodecs.VAR_INT, State::health,
                ByteBufCodecs.VAR_INT, State::maxHealth,
                ByteBufCodecs.BOOL, State::night,
                ByteBufCodecs.BOOL, State::skipping,
                ByteBufCodecs.BOOL, State::open,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), State::known,
                State::new);

        @Override
        public Type<State> type() {
            return TYPE;
        }
    }

    /** A player's request at the Core: {@link #START_NIGHT} or {@link #REPAIR}. */
    public record Action(BlockPos pos, int action) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(UntilDawn.id("core_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Action::pos,
                ByteBufCodecs.VAR_INT, Action::action,
                Action::new);

        @Override
        public Type<Action> type() {
            return TYPE;
        }
    }
}
