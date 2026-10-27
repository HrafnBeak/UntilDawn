package org.kennyerf.untildawn.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.Vec3;
import org.kennyerf.untildawn.game.CoreInterface;
import org.kennyerf.untildawn.game.CorePayloads;
import org.kennyerf.untildawn.registry.ModBlocks;
import org.kennyerf.untildawn.registry.ModItems;

/**
 * The Core's screen, styled like the {@link WorkbenchScreen}: how many mobs the next night sends, the Core's health, and
 * three buttons: start the night early, repair the Core for Scrap, and the encyclopedia. The server sends the state
 * ({@link CorePayloads.State}); the buttons only ask it to act, and it answers with a fresh state. The encyclopedia is a
 * second page of the same screen: a list of the wave mobs and a description of the one picked, and mobs the server has not
 * said are known show as ???.
 */
public class CoreScreen extends Screen {
    // The Workbench's palette.
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
    private static final int START_BUTTON = 0xFFA85A22;
    private static final int START_BUTTON_HOVER = 0xFFCE7A33;
    private static final int REPAIR_BUTTON = 0xFF5E7D2E;
    private static final int REPAIR_BUTTON_HOVER = 0xFF739A39;
    private static final int BUTTON_OFF = 0xFF3A3835;
    private static final int BOOK_BUTTON = 0xFF4A5C70;
    private static final int BOOK_BUTTON_HOVER = 0xFF5F7791;
    private static final int ROW_SELECTED = 0xFF3A3530;

    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 204;
    private static final int BOOK_WIDTH = 330;
    private static final int BOOK_HEIGHT = 214;
    private static final int BOOK_LIST_WIDTH = 104;
    private static final int BOOK_ROW = 15;
    private static final int HEADER_HEIGHT = 22;
    private static final int PAD = 10;
    private static final int BUTTON_HEIGHT = 20;

    private final BlockPos pos;
    private CorePayloads.State state;

    // Screen rectangles worked out in init().
    private int panelLeft;
    private int panelTop;
    private int startY;
    private int repairY;
    private int encyclopediaY;

    /** Showing the encyclopedia instead of the Core, and the mob picked in it (an index into {@link CorePayloads#MOBS}). */
    private boolean book;
    private int selected;

    public CoreScreen(CorePayloads.State state) {
        super(Component.translatable("core.untildawn.title"));
        this.pos = state.pos();
        this.state = state;
    }

    public BlockPos pos() {
        return pos;
    }

    /** New state from the server. */
    public void update(CorePayloads.State state) {
        this.state = state;
    }

    @Override
    protected void init() {
        layout();
    }

    private int panelWidth() {
        return book ? BOOK_WIDTH : PANEL_WIDTH;
    }

    private int panelHeight() {
        return book ? BOOK_HEIGHT : PANEL_HEIGHT;
    }

    private void layout() {
        panelLeft = (width - panelWidth()) / 2;
        panelTop = Math.max(8, (height - panelHeight()) / 2);
    }

    private void showBook(boolean show) {
        book = show;
        layout();
    }

    // ---------------------------------------------------------------- state helpers

    private Player player() {
        return minecraft.player;
    }

    private int scrap() {
        int total = 0;
        Ingredient scrap = Ingredient.of(ModItems.SCRAP);
        for (ItemStack stack : player().getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && scrap.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private int repairAmount() {
        return CoreInterface.repairAmount(state.health(), state.maxHealth());
    }

    private boolean canStart() {
        return !state.night() && !state.skipping();
    }

    private boolean canRepair() {
        int amount = repairAmount();
        return amount > 0 && (player().isCreative() || scrap() >= CoreInterface.repairCost(amount));
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
        int left = panelLeft;
        int right = panelLeft + panelWidth();
        int bottom = panelTop + panelHeight();
        graphics.fill(left, panelTop, right, bottom, PANEL);
        graphics.outline(left, panelTop, panelWidth(), panelHeight(), PANEL_EDGE);

        // Header: the title, and the Scrap the player carries.
        graphics.fill(left + 1, panelTop + 1, right - 1, panelTop + HEADER_HEIGHT, HEADER);
        graphics.fill(left + 1, panelTop + HEADER_HEIGHT - 1, right - 1, panelTop + HEADER_HEIGHT, ACCENT);
        int textY = panelTop + (HEADER_HEIGHT - 9) / 2;
        if (book) {
            graphics.text(font, Component.translatable("core.untildawn.encyclopedia"), left + PAD, textY, TEXT, false);
            drawBook(graphics, left, right, mouseX, mouseY);
            super.extractRenderState(graphics, mouseX, mouseY, a);
            return;
        }
        graphics.text(font, title, left + PAD, textY, TEXT, false);
        Component owned = Component.translatable("core.untildawn.scrap_owned", scrap());
        int ownedX = right - PAD - font.width(owned);
        graphics.item(new ItemStack(ModItems.SCRAP), ownedX - 19, panelTop + (HEADER_HEIGHT - 16) / 2);
        graphics.text(font, owned, ownedX, textY, ACCENT, false);

        int x1 = left + PAD;
        int x2 = right - PAD;
        int y = panelTop + HEADER_HEIGHT + 8;

        // The next night's wave.
        graphics.fill(x1, y, x2, y + 36, NODE_BG);
        graphics.text(font, Component.translatable("core.untildawn.next_night_label"), x1 + 6, y + 5, TEXT_DIM, false);
        Component night = Component.translatable("core.untildawn.next_night", state.upcomingNight()).withStyle(s -> s.withBold(true));
        graphics.text(font, night, x1 + 6, y + 21, TEXT, false);
        Component mobs = Component.translatable("core.untildawn.mob_count", state.upcomingSize()).withStyle(s -> s.withBold(true));
        int mobsWidth = font.width(mobs);
        float scale = Math.min(1.5F, (x2 - x1 - 18 - font.width(night)) / (float) Math.max(1, mobsWidth));
        graphics.pose().pushMatrix();
        graphics.pose().translate(x2 - 6 - mobsWidth * scale, y + 18 - 4.5F * scale);
        graphics.pose().scale(scale, scale);
        graphics.text(font, mobs, 0, 0, ACCENT, false);
        graphics.pose().popMatrix();
        y += 44;

        // The Core's health.
        graphics.text(font, Component.translatable("core.untildawn.health"), x1, y, TEXT_DIM, false);
        String health = state.health() + " / " + state.maxHealth();
        graphics.text(font, health, x2 - font.width(health), y, TEXT, false);
        y += 12;
        float fraction = Mth.clamp(state.health() / (float) Math.max(1, state.maxHealth()), 0.0F, 1.0F);
        graphics.fill(x1, y, x2, y + 6, NODE_BG);
        graphics.fill(x1, y, x1 + Math.round((x2 - x1) * fraction), y + 6, fraction > 0.3F ? GOOD : BAD);
        graphics.outline(x1, y, x2 - x1, 6, PANEL_EDGE);
        y += 16;

        // Start Night.
        startY = y;
        button(graphics, Component.translatable(state.skipping() ? "core.untildawn.skipping" : "core.untildawn.start_night"),
                x1, x2, startY, canStart(), START_BUTTON, START_BUTTON_HOVER, mouseX, mouseY);
        y += BUTTON_HEIGHT + 4;

        // Repair, with what it costs under it.
        repairY = y;
        button(graphics, Component.translatable("core.untildawn.repair"), x1, x2, repairY, canRepair(),
                REPAIR_BUTTON, REPAIR_BUTTON_HOVER, mouseX, mouseY);
        y += BUTTON_HEIGHT + 3;
        int amount = Math.max(repairAmount(), 1);
        int cost = CoreInterface.repairCost(amount);
        boolean creative = player().isCreative();
        graphics.centeredText(font, Component.translatable("core.untildawn.repair_info", cost, amount), (x1 + x2) / 2, y,
                repairAmount() <= 0 || creative || scrap() >= cost ? TEXT_DIM : BAD);
        y += 15;

        encyclopediaY = y;
        button(graphics, Component.translatable("core.untildawn.encyclopedia"), x1, x2, encyclopediaY, true,
                BOOK_BUTTON, BOOK_BUTTON_HOVER, mouseX, mouseY);
        graphics.centeredText(font, Component.translatable("core.untildawn.encyclopedia_info"), (x1 + x2) / 2,
                encyclopediaY + BUTTON_HEIGHT + 3, TEXT_DIM);

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private boolean known(int index) {
        return state.known().contains(CorePayloads.MOBS.get(index));
    }

    private Component mobName(int index) {
        return known(index) ? Component.translatable("entity.untildawn." + CorePayloads.MOBS.get(index))
                : Component.translatable("encyclopedia.untildawn.unknown_name");
    }

    private int backLeft() {
        return panelLeft + panelWidth() - PAD - 50;
    }

    private int listTop() {
        return panelTop + HEADER_HEIGHT + 8;
    }

    private void drawBook(GuiGraphicsExtractor graphics, int left, int right, int mouseX, int mouseY) {
        // Back to the Core page.
        int backTop = panelTop + 3;
        boolean backHover = mouseX >= backLeft() && mouseX < right - PAD && mouseY >= backTop && mouseY < backTop + HEADER_HEIGHT - 6;
        graphics.fill(backLeft(), backTop, right - PAD, backTop + HEADER_HEIGHT - 6, backHover ? START_BUTTON_HOVER : START_BUTTON);
        graphics.centeredText(font, Component.translatable("core.untildawn.back").copy().withStyle(s -> s.withBold(true)),
                (backLeft() + right - PAD) / 2, backTop + 5, 0xFFFFFFFF);

        // The list of mobs.
        int listLeft = left + PAD;
        for (int i = 0; i < CorePayloads.MOBS.size(); i++) {
            int rowTop = listTop() + i * BOOK_ROW;
            boolean hover = mouseX >= listLeft && mouseX < listLeft + BOOK_LIST_WIDTH && mouseY >= rowTop && mouseY < rowTop + BOOK_ROW - 1;
            graphics.fill(listLeft, rowTop, listLeft + BOOK_LIST_WIDTH, rowTop + BOOK_ROW - 1,
                    i == selected ? ROW_SELECTED : hover ? NODE_BG : PANEL);
            if (i == selected) {
                graphics.fill(listLeft, rowTop, listLeft + 2, rowTop + BOOK_ROW - 1, ACCENT);
            }
            graphics.text(font, mobName(i), listLeft + 7, rowTop + 3, known(i) ? TEXT : TEXT_DIM, false);
        }

        // The mob picked.
        int detailLeft = listLeft + BOOK_LIST_WIDTH + 12;
        int detailWidth = right - PAD - detailLeft;
        int y = listTop();
        graphics.fill(detailLeft - 6, y, detailLeft - 5, panelTop + panelHeight() - PAD, PANEL_EDGE);
        Component name = mobName(selected);
        graphics.pose().pushMatrix();
        graphics.pose().translate(detailLeft, y);
        graphics.pose().scale(1.5F, 1.5F);
        graphics.text(font, name.copy().withStyle(s -> s.withBold(true)), 0, 0, known(selected) ? ACCENT : TEXT_DIM, false);
        graphics.pose().popMatrix();
        y += 22;
        String id = CorePayloads.MOBS.get(selected);
        Component summary = Component.translatable(known(selected) ? "encyclopedia.untildawn." + id : "encyclopedia.untildawn.unknown");
        for (var line : font.split(summary, detailWidth)) {
            graphics.text(font, line, detailLeft, y, known(selected) ? TEXT : TEXT_DIM, false);
            y += 11;
        }
    }

    private void button(GuiGraphicsExtractor graphics, Component label, int left, int right, int y, boolean active,
                        int colour, int hoverColour, int mouseX, int mouseY) {
        boolean hover = active && mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
        graphics.fill(left, y, right, y + BUTTON_HEIGHT, active ? (hover ? hoverColour : colour) : BUTTON_OFF);
        graphics.centeredText(font, label.copy().withStyle(s -> s.withBold(true)), (left + right) / 2, y + 6,
                active ? 0xFFFFFFFF : TEXT_DIM);
    }

    // ---------------------------------------------------------------- input

    private boolean over(double x, double y, int buttonY) {
        return x >= panelLeft + PAD && x < panelLeft + PANEL_WIDTH - PAD && y >= buttonY && y < buttonY + BUTTON_HEIGHT;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (book) {
            clickBook(event.x(), event.y());
            return true;
        }
        if (over(event.x(), event.y(), encyclopediaY)) {
            AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
            showBook(true);
        } else if (over(event.x(), event.y(), startY) && canStart()) {
            send(CorePayloads.START_NIGHT);
            onClose(); // watch the sky instead
        } else if (over(event.x(), event.y(), repairY) && canRepair()) {
            send(CorePayloads.REPAIR);
        }
        return true;
    }

    private void clickBook(double x, double y) {
        if (x >= backLeft() && x < panelLeft + panelWidth() - PAD && y >= panelTop + 3 && y < panelTop + HEADER_HEIGHT - 3) {
            AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
            showBook(false);
            return;
        }
        int row = (int) Math.floor((y - listTop()) / BOOK_ROW);
        if (x >= panelLeft + PAD && x < panelLeft + PAD + BOOK_LIST_WIDTH && row >= 0 && row < CorePayloads.MOBS.size()
                && y >= listTop() && row != selected) {
            AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
            selected = row;
        }
    }

    private void send(int action) {
        AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
        ClientPlayNetworking.send(new CorePayloads.Action(pos, action));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (book && event.isEscape()) {
            showBook(false);
            return true;
        }
        if (minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    /** Closes when the Core is gone or the player has walked away from it. */
    @Override
    public void tick() {
        super.tick();
        Player player = player();
        if (player == null || !player.level().getBlockState(pos).is(ModBlocks.CORE)
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0) {
            onClose();
        }
    }
}
