package org.kennyerf.untildawn.workbench;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.Vec3;
import org.kennyerf.untildawn.registry.ModItems;

import java.util.ArrayList;
import java.util.List;

/**
 * The server's side of the Workbench: it opens the tech tree for a player and carries out what they ask for in it.
 * Every request is checked here (reach, the tree, research, what the player is carrying); the client only asks.
 * <ul>
 *   <li>Research is paid in Scrap once per bench, and needs the item's parent researched first.</li>
 *   <li>Crafting a researched item takes its craft cost from the player's inventory and gives them the item.</li>
 * </ul>
 * Creative players pay nothing.
 */
public final class Workbench {
    /** Players this close to a bench see its research change while they have it open. */
    private static final double VIEW_RANGE = 8.0;

    private Workbench() {
    }

    public static void init() {
        TechTree.init();
        PayloadTypeRegistry.clientboundPlay().register(WorkbenchPayloads.State.TYPE, WorkbenchPayloads.State.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(WorkbenchPayloads.Action.TYPE, WorkbenchPayloads.Action.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(WorkbenchPayloads.Action.TYPE,
                (action, context) -> handle(context.player(), action));
    }

    public static void open(ServerPlayer player, WorkbenchBlockEntity bench) {
        if (ServerPlayNetworking.canSend(player, WorkbenchPayloads.State.TYPE)) {
            ServerPlayNetworking.send(player, state(bench, true));
        }
    }

    private static WorkbenchPayloads.State state(WorkbenchBlockEntity bench, boolean open) {
        return new WorkbenchPayloads.State(bench.getBlockPos(), TechTree.all(), bench.researched(), open);
    }

    private static void handle(ServerPlayer player, WorkbenchPayloads.Action action) {
        BlockPos pos = action.pos();
        if (player.isSpectator() || !player.isWithinBlockInteractionRange(pos, 1.0)
                || !(player.level().getBlockEntity(pos) instanceof WorkbenchBlockEntity bench)) {
            return;
        }
        TechNode node = TechTree.get(action.node());
        if (node == null) {
            return;
        }
        boolean done = action.craft() ? craft(player, bench, node) : research(player, bench, node);
        if (done) {
            refreshViewers(player.level(), bench);
        }
    }

    private static boolean research(ServerPlayer player, WorkbenchBlockEntity bench, TechNode node) {
        if (bench.isResearched(node.id())) {
            return false;
        }
        if (node.parent().isPresent() && !bench.isResearched(node.parent().get())) {
            fail(player, Component.translatable("workbench.untildawn.needs_parent"));
            return false;
        }
        List<TechNode.Cost> price = List.of(new TechNode.Cost(Ingredient.of(ModItems.SCRAP), node.researchCost()));
        if (!player.isCreative() && !take(player, price)) {
            fail(player, Component.translatable("workbench.untildawn.not_enough_scrap"));
            return false;
        }
        bench.research(node.id());
        player.level().playSound(null, bench.getBlockPos(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.2F);
        return true;
    }

    private static boolean craft(ServerPlayer player, WorkbenchBlockEntity bench, TechNode node) {
        if (!bench.isResearched(node.id())) {
            return false;
        }
        if (!player.isCreative() && !take(player, node.craftCost())) {
            fail(player, Component.translatable("workbench.untildawn.missing_resources"));
            return false;
        }
        ItemStack result = node.createResult();
        if (!player.getInventory().add(result)) {
            player.spawnAtLocation(player.level(), result);
        }
        player.level().playSound(null, bench.getBlockPos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /** Takes every cost from the player's inventory, or nothing at all if they can't pay it all. */
    public static boolean take(ServerPlayer player, List<TechNode.Cost> costs) {
        NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
        // Worked out on copies first, so costs that overlap (two tags sharing items) are counted honestly.
        List<ItemStack> after = new ArrayList<>(items.size());
        for (ItemStack stack : items) {
            after.add(stack.copy());
        }
        for (TechNode.Cost cost : costs) {
            int left = cost.count();
            for (ItemStack stack : after) {
                if (left <= 0) {
                    break;
                }
                if (!stack.isEmpty() && cost.ingredient().test(stack)) {
                    int used = Math.min(left, stack.getCount());
                    stack.shrink(used);
                    left -= used;
                }
            }
            if (left > 0) {
                return false;
            }
        }
        for (int i = 0; i < items.size(); i++) {
            items.set(i, after.get(i).isEmpty() ? ItemStack.EMPTY : after.get(i));
        }
        player.getInventory().setChanged();
        return true;
    }

    private static void fail(ServerPlayer player, Component reason) {
        player.sendOverlayMessage(reason);
    }

    /** Players with the bench open see its new research (their screen ignores it if they have another open). */
    private static void refreshViewers(ServerLevel level, WorkbenchBlockEntity bench) {
        WorkbenchPayloads.State state = state(bench, false);
        Vec3 centre = Vec3.atCenterOf(bench.getBlockPos());
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(centre) <= VIEW_RANGE * VIEW_RANGE && ServerPlayNetworking.canSend(viewer, WorkbenchPayloads.State.TYPE)) {
                ServerPlayNetworking.send(viewer, state);
            }
        }
    }
}
