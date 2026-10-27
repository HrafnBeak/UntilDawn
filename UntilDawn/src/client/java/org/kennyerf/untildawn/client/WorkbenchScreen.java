package org.kennyerf.untildawn.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.locale.Language;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.registry.ModBlocks;
import org.kennyerf.untildawn.registry.ModItems;
import org.kennyerf.untildawn.workbench.TechNode;
import org.kennyerf.untildawn.workbench.WorkbenchPayloads;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Workbench's tech tree, in the style of Rust's: the tree on the left (drag with the right mouse button, scroll to zoom
 * toward the cursor), and the selected item's research and craft costs on the right. The server sends the tree and the
 * bench's research ({@link WorkbenchPayloads.State}); the buttons only ask it to act, and it answers with a fresh state.
 */
public class WorkbenchScreen extends Screen {
    // Rust-like palette: near-black glass panels, warm off-white text, green for done, rust orange for highlights.
    private static final int BACKDROP = 0xC00C0B0A;
    private static final int PANEL = 0xE81A1917;
    private static final int PANEL_EDGE = 0xFF3B3732;
    private static final int HEADER = 0xF0242220;
    private static final int TEXT = 0xFFE3D9CC;
    private static final int TEXT_DIM = 0xFF9A9188;
    private static final int GOOD = 0xFF8DB255;
    private static final int BAD = 0xFFCD4B32;
    private static final int ACCENT = 0xFFCE7A33;
    private static final int NODE_BG = 0xFF2A2825;
    private static final int NODE_BG_HOVER = 0xFF3A3733;
    private static final int NODE_EDGE = 0xFF59534B;
    private static final int NODE_DONE_BG = 0xFF2F3A22;
    private static final int LINE = 0xFF4A4540;
    private static final int LOCKED_SHADE = 0xB0141210;
    private static final int RESEARCH_BUTTON = 0xFF3C6E91;
    private static final int RESEARCH_BUTTON_HOVER = 0xFF4C88B0;
    private static final int CRAFT_BUTTON = 0xFF5E7D2E;
    private static final int CRAFT_BUTTON_HOVER = 0xFF739A39;
    private static final int BUTTON_OFF = 0xFF3A3835;

    // Tree layout, in tree units (pixels at zoom 1).
    private static final int NODE_HALF = 13;
    private static final int SPACING_X = 44;
    private static final int SPACING_Y = 50;
    private static final float MIN_ZOOM = 0.4F;
    private static final float MAX_ZOOM = 3.0F;

    private static final int MARGIN = 8;
    private static final int HEADER_HEIGHT = 22;
    private static final int BUTTON_HEIGHT = 18;

    private final BlockPos pos;
    private List<TechNode> nodes = List.of();
    private final Map<Identifier, TechNode> byId = new HashMap<>();
    private final Map<Identifier, float[]> layout = new HashMap<>();
    private Set<Identifier> researched = Set.of();
    private @Nullable Identifier selected;

    private float originX;
    private float originY;
    private float zoom = 1.0F;
    private boolean centred;
    private boolean panning;

    // Screen rectangles worked out in init().
    private int treeLeft;
    private int treeTop;
    private int treeRight;
    private int treeBottom;
    private int panelLeft;
    private int panelRight;
    private int researchButtonY = -1;
    private int craftButtonY = -1;

    public WorkbenchScreen(WorkbenchPayloads.State state) {
        super(Component.translatable("workbench.untildawn.title"));
        this.pos = state.pos();
        update(state);
    }

    public BlockPos pos() {
        return pos;
    }

    // For tests that click the screen for real: where things are, in GUI coordinates.

    public @Nullable Identifier selectedId() {
        return selected;
    }

    public float viewX() {
        return originX;
    }

    public float viewZoom() {
        return zoom;
    }

    public double[] nodeCentre(Identifier id) {
        float[] p = layout.get(id);
        return new double[]{screenX(p[0]), screenY(p[1])};
    }

    public double[] researchButtonCentre() {
        return new double[]{(panelLeft + panelRight) / 2.0, researchButtonY + BUTTON_HEIGHT / 2.0};
    }

    public double[] craftButtonCentre() {
        return new double[]{(panelLeft + panelRight) / 2.0, craftButtonY + BUTTON_HEIGHT / 2.0};
    }

    /** New research or a reloaded tree from the server. Keeps the view and selection where they are. */
    public void update(WorkbenchPayloads.State state) {
        nodes = state.nodes();
        researched = new HashSet<>(state.researched());
        byId.clear();
        for (TechNode node : nodes) {
            byId.put(node.id(), node);
        }
        if (selected != null && !byId.containsKey(selected)) {
            selected = null;
        }
        if (selected == null && !nodes.isEmpty()) {
            selected = nodes.getFirst().id();
        }
        layOut();
    }

    // ---------------------------------------------------------------- layout

    /** Parents sit centred over their children; leaves are spaced evenly, each tree beside the last. */
    private void layOut() {
        layout.clear();
        Map<Identifier, List<TechNode>> children = new HashMap<>();
        List<TechNode> roots = new ArrayList<>();
        for (TechNode node : nodes) {
            if (node.parent().isPresent() && byId.containsKey(node.parent().get())) {
                children.computeIfAbsent(node.parent().get(), k -> new ArrayList<>()).add(node);
            } else {
                roots.add(node);
            }
        }
        float[] nextLeaf = {0};
        for (TechNode root : roots) {
            place(root, 0, children, nextLeaf);
            nextLeaf[0] += 0.5F;
        }
    }

    private float place(TechNode node, int depth, Map<Identifier, List<TechNode>> children, float[] nextLeaf) {
        List<TechNode> kids = children.getOrDefault(node.id(), List.of());
        float x;
        if (kids.isEmpty()) {
            x = nextLeaf[0]++ * SPACING_X;
        } else {
            float first = place(kids.getFirst(), depth + 1, children, nextLeaf);
            float last = first;
            for (int i = 1; i < kids.size(); i++) {
                last = place(kids.get(i), depth + 1, children, nextLeaf);
            }
            x = (first + last) / 2;
        }
        layout.put(node.id(), new float[]{x, depth * SPACING_Y});
        return x;
    }

    @Override
    protected void init() {
        int panelWidth = Mth.clamp(width / 3, 150, 210);
        panelRight = width - MARGIN;
        panelLeft = panelRight - panelWidth;
        treeLeft = MARGIN;
        treeTop = MARGIN + HEADER_HEIGHT + 4;
        treeRight = panelLeft - 6;
        treeBottom = height - MARGIN;
        if (!centred) {
            centreView();
            centred = true;
        }
    }

    /** Puts the top of the tree at the top middle of the tree area. */
    private void centreView() {
        float minX = 0;
        float maxX = 0;
        for (float[] p : layout.values()) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
        }
        zoom = 1.0F;
        originX = (treeLeft + treeRight) / 2.0F - (minX + maxX) / 2.0F;
        originY = treeTop + 30;
    }

    // ---------------------------------------------------------------- state helpers

    private boolean isResearched(TechNode node) {
        return researched.contains(node.id());
    }

    private boolean canResearch(TechNode node) {
        return node.parent().map(researched::contains).orElse(true);
    }

    private Player player() {
        return minecraft.player;
    }

    private int count(Ingredient ingredient) {
        int total = 0;
        for (ItemStack stack : player().getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && ingredient.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private int scrap() {
        return count(Ingredient.of(ModItems.SCRAP));
    }

    private boolean canAfford(TechNode node) {
        if (player().isCreative()) {
            return true;
        }
        for (TechNode.Cost cost : node.craftCost()) {
            if (count(cost.ingredient()) < cost.count()) {
                return false;
            }
        }
        return true;
    }

    /** One of an ingredient's items, changing every second so tags show what they accept. */
    private static ItemStack shown(Ingredient ingredient) {
        List<Holder<Item>> items = ingredient.items().toList();
        if (items.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int index = (int) (System.currentTimeMillis() / 1000 % items.size());
        return new ItemStack(items.get(index));
    }

    private float screenX(float treeX) {
        return originX + treeX * zoom;
    }

    private float screenY(float treeY) {
        return originY + treeY * zoom;
    }

    private boolean inTree(double x, double y) {
        return x >= treeLeft && x < treeRight && y >= treeTop && y < treeBottom;
    }

    private @Nullable TechNode nodeAt(double x, double y) {
        if (!inTree(x, y)) {
            return null;
        }
        double treeX = (x - originX) / zoom;
        double treeY = (y - originY) / zoom;
        for (TechNode node : nodes) {
            float[] p = layout.get(node.id());
            if (p != null && Math.abs(treeX - p[0]) <= NODE_HALF && Math.abs(treeY - p[1]) <= NODE_HALF) {
                return node;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, width, height, BACKDROP);
        drawHeader(graphics);
        drawTree(graphics, mouseX, mouseY);
        graphics.nextStratum();
        drawPanel(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void drawHeader(GuiGraphicsExtractor graphics) {
        int top = MARGIN;
        int bottom = MARGIN + HEADER_HEIGHT;
        graphics.fill(MARGIN, top, panelRight, bottom, HEADER);
        graphics.fill(MARGIN, bottom - 1, panelRight, bottom, ACCENT);
        int textY = top + (HEADER_HEIGHT - 9) / 2;
        graphics.text(font, title, MARGIN + 8, textY, TEXT, false);
        Component owned = Component.translatable("workbench.untildawn.scrap_owned", scrap());
        int ownedX = panelRight - 8 - font.width(owned);
        Component hint = Component.translatable("workbench.untildawn.hint");
        int hintX = MARGIN + 16 + font.width(title);
        if (hintX + font.width(hint) < ownedX - 28) {
            graphics.text(font, hint, hintX, textY, TEXT_DIM, false);
        }
        graphics.item(new ItemStack(ModItems.SCRAP), ownedX - 19, top + (HEADER_HEIGHT - 16) / 2);
        graphics.text(font, owned, ownedX, textY, ACCENT, false);
    }

    private void drawTree(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(treeLeft, treeTop, treeRight, treeBottom, PANEL);
        graphics.outline(treeLeft, treeTop, treeRight - treeLeft, treeBottom - treeTop, PANEL_EDGE);
        if (nodes.isEmpty()) {
            graphics.centeredText(font, Component.translatable("workbench.untildawn.empty"),
                    (treeLeft + treeRight) / 2, (treeTop + treeBottom) / 2, TEXT_DIM);
            return;
        }
        TechNode hovered = nodeAt(mouseX, mouseY);

        graphics.enableScissor(treeLeft + 1, treeTop + 1, treeRight - 1, treeBottom - 1);
        graphics.pose().pushMatrix();
        graphics.pose().translate(originX, originY);
        graphics.pose().scale(zoom, zoom);

        // Links: down from the parent, across, and down into the child; green once the child is researched.
        for (TechNode node : nodes) {
            if (node.parent().isEmpty()) {
                continue;
            }
            float[] from = layout.get(node.parent().get());
            float[] to = layout.get(node.id());
            if (from == null || to == null) {
                continue;
            }
            int colour = isResearched(node) ? GOOD : LINE;
            int fx = (int) from[0];
            int tx = (int) to[0];
            int fy = (int) from[1] + NODE_HALF;
            int ty = (int) to[1] - NODE_HALF;
            int mid = (fy + ty) / 2;
            graphics.fill(fx - 1, fy, fx + 1, mid + 1, colour);
            graphics.fill(Math.min(fx, tx) - 1, mid - 1, Math.max(fx, tx) + 1, mid + 1, colour);
            graphics.fill(tx - 1, mid - 1, tx + 1, ty, colour);
        }

        for (TechNode node : nodes) {
            float[] p = layout.get(node.id());
            int x = (int) p[0];
            int y = (int) p[1];
            boolean done = isResearched(node);
            int edge = node.id().equals(selected) ? ACCENT : done ? GOOD : NODE_EDGE;
            int background = node == hovered ? NODE_BG_HOVER : done ? NODE_DONE_BG : NODE_BG;
            graphics.fill(x - NODE_HALF - 1, y - NODE_HALF - 1, x + NODE_HALF + 1, y + NODE_HALF + 1, edge);
            graphics.fill(x - NODE_HALF + 1, y - NODE_HALF + 1, x + NODE_HALF - 1, y + NODE_HALF - 1, background);
            graphics.item(node.createResult(), x - 8, y - 8);
        }

        // Items not yet available to research are shaded over their icons (a later layer, so it covers them).
        graphics.nextStratum();
        for (TechNode node : nodes) {
            if (!isResearched(node) && !canResearch(node)) {
                float[] p = layout.get(node.id());
                int x = (int) p[0];
                int y = (int) p[1];
                graphics.fill(x - NODE_HALF + 1, y - NODE_HALF + 1, x + NODE_HALF - 1, y + NODE_HALF - 1, LOCKED_SHADE);
            }
        }

        graphics.pose().popMatrix();
        graphics.disableScissor();

        if (hovered != null) {
            graphics.setTooltipForNextFrame(font, hovered.createResult(), mouseX, mouseY);
        }
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int top = treeTop;
        graphics.fill(panelLeft, top, panelRight, treeBottom, PANEL);
        graphics.outline(panelLeft, top, panelRight - panelLeft, treeBottom - top, PANEL_EDGE);
        researchButtonY = -1;
        craftButtonY = -1;
        TechNode node = selected == null ? null : byId.get(selected);
        if (node == null) {
            graphics.centeredText(font, Component.translatable("workbench.untildawn.select"),
                    (panelLeft + panelRight) / 2, (top + treeBottom) / 2, TEXT_DIM);
            return;
        }
        int left = panelLeft + 8;
        int right = panelRight - 8;
        boolean creative = player().isCreative();

        // Name and a big icon.
        graphics.fill(panelLeft + 1, top + 1, panelRight - 1, top + 50, HEADER);
        graphics.fill(left, top + 8, left + 36, top + 44, NODE_BG);
        graphics.outline(left, top + 8, 36, 36, isResearched(node) ? GOOD : NODE_EDGE);
        graphics.pose().pushMatrix();
        graphics.pose().translate(left + 2, top + 10);
        graphics.pose().scale(2.0F, 2.0F);
        graphics.item(node.createResult(), 0, 0);
        graphics.pose().popMatrix();
        int nameLeft = left + 44;
        graphics.textWithWordWrap(font, node.createResult().getHoverName(), nameLeft, top + 12, right - nameLeft, TEXT, false);
        if (node.result().count() > 1) {
            graphics.text(font, "x" + node.result().count(), nameLeft, top + 34, TEXT_DIM, false);
        }

        int y = top + 58;

        // Research.
        y = sectionTitle(graphics, Component.translatable("workbench.untildawn.research_cost"), left, right, y);
        if (isResearched(node)) {
            graphics.text(font, Component.translatable("workbench.untildawn.researched"), left, y + 4, GOOD, false);
            y += 18;
        } else {
            int have = scrap();
            boolean enough = creative || have >= node.researchCost();
            ItemStack scrap = new ItemStack(ModItems.SCRAP);
            y = costRow(graphics, scrap, scrap.getHoverName(), node.researchCost(), have, enough, left, right, y);
            boolean unlocked = canResearch(node);
            if (!unlocked) {
                TechNode parent = byId.get(node.parent().get());
                Component parentName = parent == null ? Component.literal(node.parent().get().toString()) : parent.createResult().getHoverName();
                y += 2 + graphics.textWithWordWrap(font, Component.translatable("workbench.untildawn.locked", parentName),
                        left, y + 2, right - left, BAD, false) - (y + 2);
            }
            researchButtonY = y + 4;
            button(graphics, Component.translatable("workbench.untildawn.research"), left, right, researchButtonY,
                    unlocked && enough, RESEARCH_BUTTON, RESEARCH_BUTTON_HOVER, mouseX, mouseY);
            y = researchButtonY + BUTTON_HEIGHT + 6;
        }

        // Crafting.
        y = sectionTitle(graphics, Component.translatable("workbench.untildawn.craft_cost"), left, right, y + 4);
        if (node.craftCost().isEmpty()) {
            graphics.text(font, Component.translatable("workbench.untildawn.free"), left, y + 4, TEXT_DIM, false);
            y += 18;
        }
        for (TechNode.Cost cost : node.craftCost()) {
            int have = count(cost.ingredient());
            ItemStack shown = shown(cost.ingredient());
            y = costRow(graphics, shown, costName(cost.ingredient(), shown), cost.count(), have, creative || have >= cost.count(),
                    left, right, y);
        }
        craftButtonY = y + 4;
        button(graphics, Component.translatable("workbench.untildawn.craft"), left, right, craftButtonY,
                isResearched(node) && canAfford(node), CRAFT_BUTTON, CRAFT_BUTTON_HOVER, mouseX, mouseY);
        y = craftButtonY + BUTTON_HEIGHT + 6;

        if (creative) {
            graphics.textWithWordWrap(font, Component.translatable("workbench.untildawn.creative"), left, y + 2, right - left, TEXT_DIM, false);
        }
    }

    private int sectionTitle(GuiGraphicsExtractor graphics, Component title, int left, int right, int y) {
        graphics.text(font, title.copy().withStyle(s -> s.withBold(true)), left, y, TEXT_DIM, false);
        graphics.fill(left, y + 11, right, y + 12, PANEL_EDGE);
        return y + 15;
    }

    /** An icon, its name and "have / need", green when there is enough. Returns the y below it. */
    /**
     * A tag's name when the language has one ({@code tag.item.<namespace>.<path>}, as Fabric's convention tags do), so
     * "any planks" reads "Planks" rather than whichever plank is showing; otherwise the shown item's name.
     */
    private static Component costName(Ingredient ingredient, ItemStack shown) {
        if (ingredient.display() instanceof SlotDisplay.TagSlotDisplay(HolderSet<Item> set) && set.unwrapKey().isPresent()) {
            Identifier tag = set.unwrapKey().get().location();
            String key = "tag.item." + tag.getNamespace() + "." + tag.getPath().replace('/', '.');
            if (Language.getInstance().has(key)) {
                return Component.translatable(key);
            }
        }
        return shown.isEmpty() ? Component.literal("?") : shown.getHoverName();
    }

    private int costRow(GuiGraphicsExtractor graphics, ItemStack stack, Component name, int need, int have, boolean enough,
                        int left, int right, int y) {
        graphics.fill(left, y, right, y + 18, NODE_BG);
        graphics.item(stack, left + 1, y + 1);
        String amount = have + " / " + need;
        int amountX = right - 4 - font.width(amount);
        String clipped = font.plainSubstrByWidth(name.getString(), amountX - (left + 21) - 4);
        graphics.text(font, clipped, left + 21, y + 5, TEXT, false);
        graphics.text(font, amount, amountX, y + 5, enough ? GOOD : BAD, false);
        return y + 20;
    }

    private void button(GuiGraphicsExtractor graphics, Component label, int left, int right, int y, boolean active,
                        int colour, int hoverColour, int mouseX, int mouseY) {
        boolean hover = active && mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
        graphics.fill(left, y, right, y + BUTTON_HEIGHT, active ? (hover ? hoverColour : colour) : BUTTON_OFF);
        graphics.centeredText(font, label.copy().withStyle(s -> s.withBold(true)), (left + right) / 2, y + 5,
                active ? 0xFFFFFFFF : TEXT_DIM);
    }

    // ---------------------------------------------------------------- input
    // Any mouse button works the same way: dragging pans the tree, and a press and release without dragging is a click.

    /** Where the current press began, and whether it has turned into a drag. */
    private double pressX;
    private double pressY;
    private boolean pressed;
    private boolean pressedInTree;

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        pressX = event.x();
        pressY = event.y();
        pressed = true;
        panning = false;
        pressedInTree = inTree(pressX, pressY);
        return true;
    }

    /** A click (not a drag) at the point: presses a button of the panel, or selects the item under it. */
    private void click(double x, double y) {
        TechNode node = selected == null ? null : byId.get(selected);
        if (node != null && x >= panelLeft + 8 && x < panelRight - 8) {
            if (researchButtonY >= 0 && y >= researchButtonY && y < researchButtonY + BUTTON_HEIGHT
                    && canResearch(node) && (player().isCreative() || scrap() >= node.researchCost())) {
                send(node, false);
                return;
            }
            if (craftButtonY >= 0 && y >= craftButtonY && y < craftButtonY + BUTTON_HEIGHT
                    && isResearched(node) && canAfford(node)) {
                send(node, true);
                return;
            }
        }
        TechNode clicked = nodeAt(x, y);
        if (clicked != null) {
            selected = clicked.id();
            AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
        }
    }

    private void send(TechNode node, boolean craft) {
        AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
        ClientPlayNetworking.send(new WorkbenchPayloads.Action(pos, node.id(), craft));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!pressed) {
            return super.mouseDragged(event, dx, dy);
        }
        if (!panning && Math.hypot(event.x() - pressX, event.y() - pressY) > 3.0) {
            panning = pressedInTree;
            if (!pressedInTree) {
                pressed = false; // dragged off a button or the panel: it was not a click
            }
        }
        if (panning) {
            originX += (float) dx;
            originY += (float) dy;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean wasClick = pressed && !panning;
        pressed = false;
        panning = false;
        if (wasClick) {
            click(event.x(), event.y());
        }
        return true;
    }

    /** Zooms toward (or away from) the cursor: the point under it stays under it. */
    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (!inTree(x, y) || scrollY == 0) {
            return super.mouseScrolled(x, y, scrollX, scrollY);
        }
        float newZoom = Mth.clamp(zoom * (float) Math.pow(1.15, scrollY), MIN_ZOOM, MAX_ZOOM);
        float treeX = (float) ((x - originX) / zoom);
        float treeY = (float) ((y - originY) / zoom);
        zoom = newZoom;
        originX = (float) x - treeX * zoom;
        originY = (float) y - treeY * zoom;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    /** Closes when the bench is gone or the player has walked away from it. */
    @Override
    public void tick() {
        super.tick();
        Player player = player();
        if (player == null || !player.level().getBlockState(pos).is(ModBlocks.WORKBENCH)
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0) {
            onClose();
        }
    }
}
