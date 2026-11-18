package dev.sablelife.client;

import dev.sablelife.module.InspectionSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

final class InspectionScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int PADDING = 12;
    private List<InspectionSnapshot.Row> rows;
    private int page;
    private int pageCount;
    private int x;
    private int y;
    private int panelWidth;
    private int panelHeight;

    InspectionScreen(List<InspectionSnapshot.Row> rows) {
        super(Component.translatable("screen.sablelife.inspection"));
        this.rows = rows;
    }

    void updateRows(List<InspectionSnapshot.Row> rows) {
        if (!this.rows.equals(rows)) {
            this.rows = rows;
            rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        int capacity = Math.clamp((height - 90) / ROW_HEIGHT, 1, 8);
        pageCount = Math.max(1, (rows.size() + capacity - 1) / capacity);
        page = Math.clamp(page, 0, pageCount - 1);
        panelWidth = Math.min(338, width - 20);
        panelHeight = 80 + Math.max(1, Math.min(capacity, rows.size())) * ROW_HEIGHT;
        x = (width - panelWidth) / 2;
        y = (height - panelHeight) / 2;
        int left = x + PADDING;
        int contentWidth = panelWidth - PADDING * 2;
        int rowY = y + 34;
        for (int i = page * capacity; i < Math.min(rows.size(), (page + 1) * capacity); i++) {
            var row = rows.get(i);
            boolean checked = InspectionClient.checked(row.id());
            var message = Component.literal(checked ? "[✓] " : "[ ] ").append(InspectionClient.name(row));
            var button = addRenderableWidget(LifeUiStyle.button(message, left, rowY, contentWidth, 19,
                    () -> { InspectionClient.toggle(row); refreshChecks(); }));
            button.active = checked || InspectionClient.count() < InspectionClient.MAX_SELECTED;
            LifeUiStyle.bookmark(button, ModuleCategory.of(row.moduleType()).color);
            button.setTooltip(Tooltip.create(InspectionClient.name(row).copy().append("\n")
                    .append(ModuleText.description(row.moduleType()))));
            rowButtons.add(new RowButton(row, button));
            rowY += ROW_HEIGHT;
        }
        int controls = y + panelHeight - 31;
        var back = addRenderableWidget(LifeUiStyle.button(Component.literal("‹"), left, controls, 28, 19,
                () -> turn(-1)));
        var next = addRenderableWidget(LifeUiStyle.button(Component.literal("›"), left + 34, controls, 28, 19,
                () -> turn(1)));
        back.active = page > 0;
        next.active = page + 1 < pageCount;
        addRenderableWidget(LifeUiStyle.button(Component.translatable("gui.done"),
                left + contentWidth - 62, controls, 62, 19, this::onClose));
    }

    private record RowButton(InspectionSnapshot.Row row, net.minecraft.client.gui.components.Button button) {}
    private final java.util.List<RowButton> rowButtons = new java.util.ArrayList<>();

    @Override
    protected void clearWidgets() {
        super.clearWidgets();
        rowButtons.clear();
    }

    private void refreshChecks() {
        for (var entry : rowButtons) {
            boolean checked = InspectionClient.checked(entry.row.id());
            entry.button.setMessage(Component.literal(checked ? "[✓] " : "[ ] ").append(InspectionClient.name(entry.row)));
            entry.button.active = checked || InspectionClient.count() < InspectionClient.MAX_SELECTED;
        }
    }

    private void turn(int delta) {
        page += delta;
        rebuildWidgets();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.renderBackground(graphics, mouseX, mouseY, partial);
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xFF9B614E);
        graphics.fill(x + 2, y + 2, x + panelWidth - 2, y + panelHeight - 2, 0xFF242632);
        graphics.drawCenteredString(font, title, width / 2, y + 12, 0xFFE6D8EA);
        if (rows.isEmpty()) graphics.drawCenteredString(font, Component.translatable("screen.sablelife.inspection_empty"),
                width / 2, y + 39, 0xFFC8BDD1);
        var status = Component.translatable("screen.sablelife.inspection_status", page + 1, pageCount,
                InspectionClient.count(), InspectionClient.MAX_SELECTED);
        graphics.drawCenteredString(font, status, width / 2, y + panelHeight - 26, 0xFFC8BDD1);
    }

    @Override public boolean isPauseScreen() { return false; }
}
