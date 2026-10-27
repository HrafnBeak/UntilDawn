package org.kennyerf.untildawn.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.kennyerf.untildawn.UntilDawn;
import org.kennyerf.untildawn.client.WorkbenchScreen;
import org.kennyerf.untildawn.registry.ModBlocks;
import org.kennyerf.untildawn.registry.ModItems;
import org.kennyerf.untildawn.workbench.Workbench;
import org.kennyerf.untildawn.workbench.WorkbenchBlockEntity;
import org.kennyerf.untildawn.workbench.WorkbenchPayloads;

/**
 * The Workbench's screen in a real client: it opens, pans and zooms with the mouse, and research and crafting go
 * through the server and come back to the screen. Screenshots (in {@code run/screenshots}) show how it looks.
 */
public class WorkbenchClientGameTests implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getConnection().waitForChunksRender();
            TestServerContext server = singleplayer.getServer();
            BlockPos bench = server.computeOnServer(s -> {
                ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                BlockPos pos = player.blockPosition().offset(2, 0, 0);
                player.level().setBlockAndUpdate(pos, ModBlocks.WORKBENCH.defaultBlockState());
                player.getInventory().add(new ItemStack(ModItems.SCRAP, 12));
                player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 3));
                return pos;
            });
            context.waitTicks(5);
            server.runOnServer(s -> Workbench.open(s.getPlayerList().getPlayers().getFirst(),
                    (WorkbenchBlockEntity) s.overworld().getBlockEntity(bench)));
            context.waitTicks(10);
            if (!context.computeOnClient(mc -> mc.gui.screen() instanceof WorkbenchScreen)) {
                throw new AssertionError("using the bench should open its screen");
            }
            context.takeScreenshot("workbench-open");

            double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
            Identifier wooden = UntilDawn.id("wooden_sword");
            Identifier stone = UntilDawn.id("stone_sword");

            // Clicking an item selects it.
            clickAt(context, scale, context.computeOnClient(mc -> screen(mc).nodeCentre(stone)), 0);
            expect(context.computeOnClient(mc -> stone.equals(screen(mc).selectedId())), "clicking the stone sword should select it");
            clickAt(context, scale, context.computeOnClient(mc -> screen(mc).nodeCentre(wooden)), 0);
            expect(context.computeOnClient(mc -> wooden.equals(screen(mc).selectedId())), "clicking the wooden sword should select it");

            // The Research button takes 5 Scrap; Craft then gives the sword for 2 planks (12 - 5 Scrap, 3 - 2 planks left).
            clickAt(context, scale, context.computeOnClient(mc -> screen(mc).researchButtonCentre()), 0);
            context.waitTicks(5);
            expect(server.computeOnServer(s -> ((WorkbenchBlockEntity) s.overworld().getBlockEntity(bench)).isResearched(wooden)),
                    "the Research button should research the wooden sword");
            context.takeScreenshot("workbench-researched");
            clickAt(context, scale, context.computeOnClient(mc -> screen(mc).craftButtonCentre()), 0);
            context.waitTicks(5);
            String inventory = server.computeOnServer(s -> {
                ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
                return player.getInventory().countItem(ModItems.SCRAP) + " scrap, "
                        + player.getInventory().countItem(Items.OAK_PLANKS) + " planks, "
                        + player.getInventory().countItem(Items.WOODEN_SWORD) + " sword";
            });
            expect(inventory.equals("7 scrap, 1 planks, 1 sword"), "research and craft should take their costs: " + inventory);

            // Dragging with either button pans the tree.
            float startX = context.computeOnClient(mc -> screen(mc).viewX());
            dragFrom(context, scale, 150, 200, 0, -40, 0);
            float afterLeft = context.computeOnClient(mc -> screen(mc).viewX());
            expect(afterLeft < startX - 20, "dragging with the left button should pan the tree (moved " + (afterLeft - startX) + ")");
            dragFrom(context, scale, 150, 200, 1, -40, 0);
            float afterRight = context.computeOnClient(mc -> screen(mc).viewX());
            expect(afterRight < afterLeft - 20, "dragging with the right button should pan the tree (moved " + (afterRight - afterLeft) + ")");

            // A drag must not count as a click: it leaves the selection alone.
            expect(context.computeOnClient(mc -> wooden.equals(screen(mc).selectedId())), "dragging should not change the selection");

            // Right-clicking an item selects it too.
            clickAt(context, scale, context.computeOnClient(mc -> screen(mc).nodeCentre(stone)), 1);
            expect(context.computeOnClient(mc -> stone.equals(screen(mc).selectedId())), "right-clicking the stone sword should select it");

            // Scrolling zooms toward the cursor.
            float zoomBefore = context.computeOnClient(mc -> screen(mc).viewZoom());
            context.getInput().setCursorPos(150 * scale, 120 * scale);
            context.getInput().scroll(3);
            context.waitTicks(2);
            expect(context.computeOnClient(mc -> screen(mc).viewZoom()) > zoomBefore, "scrolling up should zoom in");
            context.takeScreenshot("workbench-zoomed");
            context.runOnClient(mc -> mc.gui.setScreen(null));
        }
    }

    private static WorkbenchScreen screen(Minecraft mc) {
        return (WorkbenchScreen) mc.gui.screen();
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Moves the cursor to a point in GUI coordinates and clicks there with a button (0 left, 1 right). */
    private static void clickAt(ClientGameTestContext context, double scale, double[] at, int button) {
        context.getInput().setCursorPos(at[0] * scale, at[1] * scale);
        context.waitTick();
        context.getInput().holdMouseFor(button, 2);
        context.waitTicks(2);
    }

    /** Holds a button down at a GUI point and drags the cursor by (dx, dy) GUI pixels. */
    private static void dragFrom(ClientGameTestContext context, double scale, double x, double y, int button, double dx, double dy) {
        context.getInput().setCursorPos(x * scale, y * scale);
        context.waitTick();
        context.getInput().holdMouse(button);
        context.waitTick();
        context.getInput().moveCursor(dx * scale, dy * scale);
        context.waitTicks(2);
        context.getInput().releaseMouse(button);
        context.waitTick();
    }
}

