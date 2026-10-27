package org.kennyerf.untildawn.game;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.Vec3;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.registry.ModItems;
import org.kennyerf.untildawn.workbench.TechNode;
import org.kennyerf.untildawn.workbench.Workbench;

import java.util.List;

/**
 * The server's side of the Core screen: using the Core opens it, and the player can ask it to start the night early or
 * to repair the Core for Scrap. Every request is checked here; the client only asks. Creative players pay nothing.
 * The state also says which mobs the encyclopedia may describe, so the client never learns about the others.
 */
public final class CoreInterface {
    /** The most health one repair gives back, and the Scrap it costs per point of health. */
    public static final int REPAIR_STEP = 10;
    public static final int REPAIR_SCRAP_PER_HEALTH = 1;

    /** Players this close to the Core see its state change while they have it open. */
    private static final double VIEW_RANGE = 8.0;

    private CoreInterface() {
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(CorePayloads.State.TYPE, CorePayloads.State.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CorePayloads.Action.TYPE, CorePayloads.Action.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CorePayloads.Action.TYPE,
                (action, context) -> handle(context.player(), action));
    }

    /** Opens the screen for the active Core at {@code pos}. */
    public static void open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        CoreState core = CoreState.get(level.getServer());
        if (core.isActive() && core.pos().equals(pos) && ServerPlayNetworking.canSend(player, CorePayloads.State.TYPE)) {
            ServerPlayNetworking.send(player, state(level, core, true));
        }
    }

    /** Scrap a repair of {@code health} points costs. */
    public static int repairCost(int health) {
        return health * REPAIR_SCRAP_PER_HEALTH;
    }

    /** Health the next repair gives back: up to {@link #REPAIR_STEP}, and 0 when the Core is at full health. */
    public static int repairAmount(int health, int maxHealth) {
        return Math.max(0, Math.min(REPAIR_STEP, maxHealth - health));
    }

    private static CorePayloads.State state(ServerLevel level, CoreState core, boolean open) {
        return new CorePayloads.State(core.pos(), core.upcomingNight(), WaveManager.waveSize(core.upcomingNight()),
                core.health, UntilDawnConfig.CORE_MAX_HEALTH.get(), WaveManager.isNight(level), core.skippingToNight, open,
                knownMobs(core));
    }

    /** The mobs whose first night is over (the night in progress doesn't count), so a mob stays a mystery until it has been survived. */
    private static List<String> knownMobs(CoreState core) {
        int ended = core.wasNight ? core.night - 1 : core.night;
        return CorePayloads.MOBS.stream().filter(mob -> WaveManager.settingsOf(mob).firstNight.get() <= ended).toList();
    }

    private static void handle(ServerPlayer player, CorePayloads.Action action) {
        BlockPos pos = action.pos();
        if (player.isSpectator() || !player.isWithinBlockInteractionRange(pos, 1.0)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        CoreState core = CoreState.get(level.getServer());
        if (!core.isActive() || !core.pos().equals(pos) || !core.dimension().equals(level.dimension())) {
            return;
        }
        boolean done = switch (action.action()) {
            case CorePayloads.START_NIGHT -> startNight(player, level, core);
            case CorePayloads.REPAIR -> repair(player, level, core);
            default -> false;
        };
        if (done) {
            refreshViewers(level, core);
        }
    }

    private static boolean startNight(ServerPlayer player, ServerLevel level, CoreState core) {
        if (WaveManager.isNight(level) || core.skippingToNight) {
            fail(player, Component.translatable("core.untildawn.already_night"));
            return false;
        }
        core.skippingToNight = true;
        level.playSound(null, core.pos(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 0.8F);
        return true;
    }

    private static boolean repair(ServerPlayer player, ServerLevel level, CoreState core) {
        int amount = repairAmount(core.health, UntilDawnConfig.CORE_MAX_HEALTH.get());
        if (amount <= 0) {
            fail(player, Component.translatable("core.untildawn.full_health"));
            return false;
        }
        List<TechNode.Cost> price = List.of(new TechNode.Cost(Ingredient.of(ModItems.SCRAP), repairCost(amount)));
        if (!player.isCreative() && !Workbench.take(player, price)) {
            fail(player, Component.translatable("workbench.untildawn.not_enough_scrap"));
            return false;
        }
        core.health += amount;
        core.setDirty();
        level.playSound(null, core.pos(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 1.2F);
        return true;
    }

    private static void fail(ServerPlayer player, Component reason) {
        player.sendOverlayMessage(reason);
    }

    /** Players with the screen open see the new state (their screen ignores it if they have another open). */
    public static void refreshViewers(ServerLevel level, CoreState core) {
        CorePayloads.State state = state(level, core, false);
        Vec3 centre = Vec3.atCenterOf(core.pos());
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(centre) <= VIEW_RANGE * VIEW_RANGE && ServerPlayNetworking.canSend(viewer, CorePayloads.State.TYPE)) {
                ServerPlayNetworking.send(viewer, state);
            }
        }
    }
}
