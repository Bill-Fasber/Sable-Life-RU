package dev.sablelife.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

final class VisualEffectPickerScreen extends Screen {
    private static final int COLUMNS = 2;
    private static final int PADDING = 8;
    private static final int GAP = 6;
    private static final int ROW_HEIGHT = 24;
    private static final int MAX_ROWS = 5;
    private static final int HEADER_HEIGHT = 36;
    private static final int PAGER_HEIGHT = 24;
    private static final int PAGER_BUTTON_HEIGHT = 18;
    private static final int BACK_WIDTH = 56;

    private final Screen parent;
    private final List<String> options;
    private final int selected;
    private final IntConsumer choose;
    private int page = -1;
    private int pages;
    private int x, y, panelWidth, panelHeight;

    VisualEffectPickerScreen(Screen parent, List<String> options, int selected, IntConsumer choose) {
        this(parent,options,selected,choose,Component.translatable("dashboard.sablelife.visual_pattern_picker"));
    }

    VisualEffectPickerScreen(Screen parent, List<String> options, int selected, IntConsumer choose, Component title) {
        super(title);
        this.parent = parent;
        this.options = List.copyOf(options);
        this.selected = Math.clamp(selected, 0, options.size() - 1);
        this.choose = choose;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(370, width - 24);
        int totalRows = (options.size() + COLUMNS - 1) / COLUMNS;
        int availableRows = (height - 24 - HEADER_HEIGHT - PAGER_HEIGHT - PADDING) / ROW_HEIGHT;
        int rows = Math.min(totalRows, Math.clamp(availableRows, 1, MAX_ROWS));
        int perPage = rows * COLUMNS;
        pages = (options.size() + perPage - 1) / perPage;
        page = page < 0 ? selected / perPage : Math.min(page, pages - 1);
        panelHeight = HEADER_HEIGHT + rows * ROW_HEIGHT + PAGER_HEIGHT + PADDING;
        x = (width - panelWidth) / 2;
        y = (height - panelHeight) / 2;

        addRenderableWidget(LifeUiStyle.button(Component.translatable("dashboard.sablelife.back"),
                x + panelWidth - PADDING - BACK_WIDTH, y + PADDING, BACK_WIDTH, 20, this::onClose));

        int contentWidth = panelWidth - PADDING * 2;
        int cellWidth = (contentWidth - GAP) / COLUMNS;
        for (int option = page * perPage; option < Math.min(options.size(), (page + 1) * perPage); option++) {
            int index = option;
            int cell = option - page * perPage;
            int column = cell % COLUMNS;
            int buttonX = x + PADDING + column * (cellWidth + GAP);
            int buttonY = y + HEADER_HEIGHT + cell / COLUMNS * ROW_HEIGHT;
            int buttonWidth = column == 0 ? cellWidth : contentWidth - GAP - cellWidth;
            Component name = Component.translatable("dashboard.sablelife.choice_" + options.get(option));
            var button = addRenderableWidget(LifeUiStyle.button(
                    option == selected ? Component.literal("✓ ").append(name) : name,
                    buttonX, buttonY, buttonWidth, 20, () -> select(index)));
            if (option == selected) LifeUiStyle.fill(button, 0xFF4D6355);
        }

        addRenderableWidget(LifeUiStyle.button(Component.literal("‹"),
                x + PADDING, pagerY(), 32, PAGER_BUTTON_HEIGHT, () -> changePage(-1))).active = page > 0;
        addRenderableWidget(LifeUiStyle.button(Component.literal("›"),
                x + panelWidth - PADDING - 32, pagerY(), 32, PAGER_BUTTON_HEIGHT, () -> changePage(1)))
                .active = page + 1 < pages;
    }

    private int pagerY() {
        return y + panelHeight - PADDING - PAGER_BUTTON_HEIGHT;
    }

    private void changePage(int direction) {
        page += direction;
        rebuildWidgets();
    }

    private void select(int index) {
        choose.accept(index);
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(x - 2, y - 2, x + panelWidth + 2, y + panelHeight + 2, 0xFF9B614E);
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xFF242632);
        graphics.drawString(font, title, x + PADDING, y + PADDING + 6, 0xFFD9C4ED, false);
        graphics.drawCenteredString(font, (page + 1) + " / " + pages,
                x + panelWidth / 2, pagerY() + (PAGER_BUTTON_HEIGHT - font.lineHeight) / 2, 0xFFD9C4ED);
    }
}
