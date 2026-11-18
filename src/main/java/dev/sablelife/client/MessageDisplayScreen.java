package dev.sablelife.client;

import dev.sablelife.module.HeartDashboardMenu;
import dev.sablelife.module.MessageProgram;
import dev.sablelife.module.ModuleNetworking;
import dev.sablelife.module.VirtualModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MessageDisplayScreen extends Screen {
    private static final int ROW_STEP = 22;
    private static final int INPUT_HEIGHT = 18;
    private static final int LIST_Y = 84;
    private static final int LABEL_COLOR = 0xD9C4ED;

    private final HeartDashboardScreen parent;
    private final HeartDashboardMenu menu;
    private final UUID moduleId;
    private final List<Button> entries = new ArrayList<>();
    private final Map<Integer, Draft> drafts = new HashMap<>();
    private record Draft(String text, String subtitle, String duration, String delay) {}
    private CustomData lastSnapshot;
    private List<MessageProgram.Row> rows = List.of();
    private int revision, ticks, pendingUntil, confirmUntil;
    private int selected, page, slots, left, top, panelWidth, panelHeight, listWidth, editorX;
    private boolean canEdit, global, survival;
    private int previousInterval = 20;
    private double radius = 64;
    private EditBox radiusInput, textInput, subtitleInput, durationInput, delayInput;
    private Button globalButton, radiusSave, textSave, subtitleSave, durationSave, delaySave;
    private Button modeButton, addButton, deleteButton, previous, next;

    MessageDisplayScreen(HeartDashboardScreen parent, HeartDashboardMenu menu, UUID moduleId) {
        super(tr("title"));
        this.parent = parent;
        this.menu = menu;
        this.moduleId = moduleId;
    }

    private static Component tr(String key) {
        return Component.translatable("message_display.sablelife." + key);
    }

    @Override
    protected void init() {
        stashRow();
        String radiusDraft = radiusInput == null ? null : unsaved(radiusInput, integer(radius));
        panelWidth = Math.min(width - 12, 520);
        panelHeight = Math.min(height - 12, 320);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        int inside = panelWidth - 20;
        int footer = panelHeight - 30;
        int paginator = footer - ROW_STEP;
        listWidth = inside / 3;
        editorX = 10 + listWidth + 12;
        int editorWidth = panelWidth - editorX - 10;
        int numericWidth = 42;
        int saveX = panelWidth - 29;
        slots = Math.max(1, (paginator - LIST_Y) / ROW_STEP);
        page = selected / slots;

        button(tr("back"), panelWidth - 61, 8, 51, this::onClose);
        globalButton = button(Component.empty(), panelWidth - 78, 30, 68,
                () -> sendSetting("messageGlobal", global ? 0 : 1));
        globalButton.setTooltip(Tooltip.create(tr("global_help")));

        radiusInput = numeric("radius", saveX - numericWidth - 3, 52, numericWidth);
        radiusInput.setTooltip(Tooltip.create(tr("radius_help")));
        radiusSave = saveButton(saveX, 52, () -> save(radiusInput));

        entries.clear();
        for (int slot = 0; slot < slots; slot++) {
            int entry = slot;
            entries.add(button(Component.empty(), 10, LIST_Y + slot * ROW_STEP, listWidth,
                    () -> select(page * slots + entry)));
        }
        previous = button(Component.literal("‹"), 10, paginator, 20, () -> changePage(-1));
        next = button(Component.literal("›"), 10 + listWidth - 20, paginator, 20, () -> changePage(1));
        addButton = button(tr("add"), 10, footer, (listWidth - 4) / 2, () -> sendRow("add", ""));
        int deleteX = 14 + (listWidth - 4) / 2;
        deleteButton = button(tr("delete"), deleteX, footer, 10 + listWidth - deleteX, () -> {
            if (waiting()) return;
            if (confirmUntil > ticks) {
                confirmUntil = 0;
                sendRow("remove", "");
            } else {
                confirmUntil = ticks + 60;
                refreshControls();
            }
        });

        int modeWidth = Math.min(90, editorWidth * 2 / 3);
        modeButton = button(Component.empty(), panelWidth - 10 - modeWidth, LIST_Y, modeWidth, () -> cycleMode(1));
        LifeUiStyle.reverse(modeButton, () -> cycleMode(-1));

        textInput = input("text", editorX, 116, editorWidth - 23, MessageProgram.MAX_TEXT);
        textInput.setTooltip(Tooltip.create(tr("format_help")));
        textSave = saveButton(saveX, 116, () -> save(textInput));
        delayInput = numeric("delay_after", saveX - numericWidth - 3, 138, numericWidth);
        delayInput.setTooltip(Tooltip.create(tr("delay_after_help")));
        delaySave = saveButton(saveX, 138, () -> save(delayInput));
        subtitleInput = input("subtitle", editorX, 170, editorWidth - 23, MessageProgram.MAX_TEXT);
        subtitleInput.setTooltip(Tooltip.create(tr("format_help")));
        subtitleSave = saveButton(saveX, 170, () -> save(subtitleInput));
        durationInput = numeric("duration", saveX - numericWidth - 3, 192, numericWidth);
        durationSave = saveButton(saveX, 192, () -> save(durationInput));

        if (lastSnapshot == null) sync();
        radiusInput.setValue(radiusDraft == null ? integer(radius) : radiusDraft);
        loadRow();
        refreshControls();
    }

    private Button button(Component label, int x, int y, int width, Runnable handler) {
        return addRenderableWidget(LifeUiStyle.button(label, left + x, top + y, width, INPUT_HEIGHT, handler));
    }

    private Button saveButton(int x, int y, Runnable handler) {
        return button(Component.literal("✓"), x, y, 19, handler);
    }

    private EditBox input(String key, int x, int y, int width, int maxLength) {
        var box = addRenderableWidget(new EditBox(font, left + x, top + y, width, INPUT_HEIGHT, tr(key)));
        box.setMaxLength(maxLength);
        return box;
    }

    private EditBox numeric(String key, int x, int y, int width) {
        var box = input(key, x, y, width, 6);
        box.setFilter(key.equals("radius") ? value -> value.matches("[0-9]{0,3}")
                : value -> value.matches("[0-9]{0,2}(\\.[0-9]{0,2})?"));
        return box;
    }

    private static String integer(double value) {
        return Integer.toString((int) value);
    }

    private static String seconds(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private boolean waiting() {
        return pendingUntil > ticks;
    }

    private void send(String operation, String key, double value, String text) {
        if (!canEdit || waiting()) return;
        pendingUntil = ticks + 60;
        PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId, revision, moduleId,
                operation, key, value, text));
    }

    private void sendSetting(String key, double value) {
        send("edit", key, value, "");
    }

    private void sendRow(String key, String payload) {
        send("msg_row", key, selected, payload);
    }

    private void cycleMode(int direction) {
        var row = row();
        if (row != null) sendRow("mode", Integer.toString(Math.floorMod(row.mode() + direction, MessageProgram.ACTION_BAR + 1)));
    }

    private MessageProgram.Row row() {
        return selected < rows.size() ? rows.get(selected) : null;
    }

    private void select(int index) {
        if (index == selected || index >= rows.size() || waiting()) return;
        stashRow();
        selected = index;
        confirmUntil = 0;
        loadRow();
        refreshControls();
    }

    private void loadRow() {
        var row = row();
        var draft = drafts.get(selected);
        refreshInput(textInput, draft != null && draft.text() != null ? draft.text() : row == null ? "" : row.text());
        refreshInput(subtitleInput, draft != null && draft.subtitle() != null ? draft.subtitle() : row == null ? "" : row.subtitle());
        refreshInput(durationInput, draft != null && draft.duration() != null ? draft.duration() : row == null ? "" : seconds(row.duration() / 20.0));
        refreshInput(delayInput, draft != null && draft.delay() != null ? draft.delay() : row == null ? "" : delayText(row));
    }

    private void stashRow() {
        var row = row();
        if (textInput == null || row == null) return;
        trackDraft(selected, new Draft(unsaved(textInput, row.text()), unsaved(subtitleInput, row.subtitle()),
                unsaved(durationInput, seconds(row.duration() / 20.0)), unsaved(delayInput, delayText(row))));
    }

    private String delayText(MessageProgram.Row row) {
        return seconds(row.delayAfterOr(previousInterval) / 20.0);
    }

    private void trackDraft(int index, Draft draft) {
        if (draft.text() == null && draft.subtitle() == null && draft.duration() == null && draft.delay() == null) drafts.remove(index);
        else drafts.put(index, draft);
    }

    private static String unsaved(EditBox input, String saved) {
        return input.getValue().equals(saved) ? null : input.getValue();
    }

    private static void refreshInput(EditBox box, String value) {
        if (!box.getValue().equals(value)) box.setValue(value);
        box.setTextColor(0xFFFFFF);
    }

    private void changePage(int direction) {
        page = Math.clamp(page + direction, 0, pageCount() - 1);
        refreshControls();
    }

    private int pageCount() {
        return Math.max(1, (rows.size() + slots - 1) / slots);
    }

    private void save(EditBox field) {
        if (waiting() || !field.active || !field.visible) return;
        try {
            if (field == textInput) sendRow("text", field.getValue());
            else if (field == subtitleInput) sendRow("subtitle", field.getValue());
            else {
                double value = Double.parseDouble(field.getValue());
                if (field == radiusInput) {
                    if (value < 1 || value > 256) throw new NumberFormatException();
                    sendSetting("messageRadius", value);
                } else {
                    if (value < .05 || value > 60) throw new NumberFormatException();
                    sendRow(field == delayInput ? "delay_after" : "duration", Long.toString(Math.round(value * 20)));
                }
            }
            field.setTextColor(0xFFFFFF);
            field.setFocused(false);
        } catch (NumberFormatException invalid) {
            field.setTextColor(0xFF7777);
        }
    }

    private boolean sync() {
        var data = menu.getSlot(0).getItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (data == lastSnapshot) return false;
        lastSnapshot = data;
        var snapshot = data.copyTag();
        VirtualModule module = null;
        for (var entry : snapshot.getList("Modules", 10)) {
            var tag = (net.minecraft.nbt.CompoundTag) entry;
            if (tag.hasUUID("Id") && tag.getUUID("Id").equals(moduleId)) {
                module = VirtualModule.load(tag);
                break;
            }
        }
        int nextRevision = snapshot.getInt("Revision");
        boolean wasWaiting = waiting();
        if (nextRevision != revision) pendingUntil = 0;
        revision = nextRevision;
        survival = snapshot.getBoolean("SurvivalCore");
        canEdit = module != null && snapshot.getBoolean("Editable") && HeartDashboardMenu.messageEditable(snapshot)
                && snapshot.getList("AvailableTypes", 8).stream().anyMatch(type -> type.getAsString().equals("message_display"));
        var oldRows = rows;
        int oldSize = rows.size();
        var updatedRows = module == null ? List.<MessageProgram.Row>of() : module.messages().rows();
        boolean rowsChanged = !oldRows.equals(updatedRows);
        int updatedInterval = module == null ? 20 : (int) module.value("messageInterval");
        boolean intervalChanged = updatedInterval != previousInterval;
        if (rowsChanged || intervalChanged) {
            stashRow();
            // Rows are indexed. A deletion can shift every subsequent draft.
            if (updatedRows.size() < oldSize) drafts.clear();
            else for (int index : List.copyOf(drafts.keySet())) {
                var old = oldRows.get(index);
                var updated = updatedRows.get(index);
                var draft = drafts.get(index);
                trackDraft(index, new Draft(old.text().equals(updated.text()) ? draft.text() : null,
                        old.subtitle().equals(updated.subtitle()) ? draft.subtitle() : null,
                        old.duration() == updated.duration() ? draft.duration() : null,
                        old.delayAfterOr(previousInterval) == updated.delayAfterOr(updatedInterval) ? draft.delay() : null));
            }
            rows = updatedRows;
        }
        if (wasWaiting && rows.size() > oldSize) selected = rows.size() - 1;
        selected = Math.clamp(selected, 0, Math.max(0, rows.size() - 1));
        if (rows.size() != oldSize) page = Math.clamp(selected / slots, 0, pageCount() - 1);
        global = !survival && module != null && module.value("messageGlobal") != 0;
        double newRadius = module == null ? 64 : module.value("messageRadius");
        if (newRadius != radius) refreshInput(radiusInput, integer(newRadius));
        radius = newRadius;
        previousInterval = updatedInterval;
        if (rowsChanged || intervalChanged) loadRow();
        return true;
    }

    private void refreshControls() {
        var current = row();
        boolean rowEditable = canEdit && current != null;
        boolean titleRow = current != null && current.mode() == MessageProgram.TITLE;
        globalButton.active = canEdit;
        globalButton.visible = !survival;
        globalButton.setMessage(tr(global ? "on" : "off"));
        LifeUiStyle.fill(globalButton, global ? 0xFF24583D : 0);
        setInputActive(radiusInput, radiusSave, canEdit && !global);
        setInputActive(delayInput, delaySave, rowEditable);
        setInputActive(textInput, textSave, rowEditable);
        setInputActive(subtitleInput, subtitleSave, rowEditable && titleRow);
        setInputActive(durationInput, durationSave, rowEditable && titleRow);
        subtitleInput.visible = subtitleSave.visible = durationInput.visible = durationSave.visible = titleRow;
        modeButton.active = rowEditable;
        modeButton.setMessage(tr("mode_" + (current == null ? 0 : current.mode())));
        addButton.active = canEdit && rows.size() < MessageProgram.MAX_ROWS;
        deleteButton.active = canEdit && rows.size() > 1;
        deleteButton.setMessage(tr(confirmUntil > ticks ? "confirm_delete" : "delete"));
        previous.active = page > 0;
        next.active = page + 1 < pageCount();
        for (int slot = 0; slot < entries.size(); slot++) {
            int index = page * slots + slot;
            var button = entries.get(slot);
            button.visible = index < rows.size();
            if (!button.visible) continue;
            var row = rows.get(index);
            var name = row.text().isBlank() ? tr("empty") : Component.literal(row.text());
            String number = (index == selected ? "▶ " : "") + (index + 1) + ". ";
            String preview = font.plainSubstrByWidth(name.getString(), Math.max(1, listWidth - 9 - font.width(number)));
            button.setMessage(Component.literal(number + preview));
            button.setTooltip(Tooltip.create(tr("mode_" + row.mode()).copy().append(" · ").append(name)));
        }
    }

    private static void setInputActive(EditBox box, Button save, boolean active) {
        box.active = save.active = active;
        box.setEditable(active);
        if (!active) box.setFocused(false);
    }

    @Override
    public void tick() {
        if (minecraft.player != null && minecraft.player.containerMenu != menu) {
            minecraft.setScreen(null);
            return;
        }
        ticks++;
        if (sync() || ticks == confirmUntil) refreshControls();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (getFocused() instanceof EditBox box && box.isFocused() && key != GLFW.GLFW_KEY_ESCAPE
                && key != GLFW.GLFW_KEY_TAB) {
            if (waiting()) return true;
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                save(box);
                return true;
            }
            return box.keyPressed(key, scan, modifiers);
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return waiting() && getFocused() instanceof EditBox || super.charTyped(character, modifiers);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0 && !waiting()) {
            for (var box : List.of(radiusInput, delayInput, durationInput)) {
                if (!box.active || !box.visible || !box.isMouseOver(x, y)) continue;
                boolean isRadius = box == radiusInput;
                double value;
                try {
                    value = Double.parseDouble(box.getValue());
                } catch (NumberFormatException ignored) {
                    value = isRadius ? radius : (box == delayInput ? row().delayAfterOr(previousInterval) : row().duration()) / 20.0;
                }
                double adjusted = Math.clamp(value + Math.signum(vertical) * (isRadius ? 8 : box == durationInput ? 1 : .1),
                        isRadius ? 1 : .05, isRadius ? 256 : 60);
                refreshInput(box, isRadius ? integer(adjusted) : seconds(adjusted));
                return true;
            }
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xFF9B614E);
        g.fill(left + 2, top + 2, left + panelWidth - 2, top + panelHeight - 2, 0xFF242632);
        g.fill(left + 10, top + 76, left + panelWidth - 10, top + 77, 0xFF6B5879);
        g.fill(left + editorX - 7, top + LIST_Y, left + editorX - 6, top + panelHeight - 10, 0xFF9A79B6);
        drawLabel(g, title, 10, 13, panelWidth - 80, 1);
        drawLabel(g, tr(survival ? "local_only" : "global"), 10, 34, globalButton.getX() - left - 14, .85f);
        drawLabel(g, tr("radius"), 10, 56, radiusInput.getX() - left - 14, .85f);
        drawLabel(g, tr("type"), editorX, LIST_Y + 4, modeButton.getX() - left - editorX - 4, .85f);
        drawLabel(g, tr("text"), editorX, 105, textInput.getWidth(), .85f);
        drawLabel(g, tr("delay_after"), editorX, delayInput.getY() - top + 4, delayInput.getX() - left - editorX - 4, .85f);
        if (subtitleInput.visible) {
            drawLabel(g, tr("subtitle"), editorX, subtitleInput.getY() - top - 11, subtitleInput.getWidth(), .85f);
            drawLabel(g, tr("duration"), editorX, durationInput.getY() - top + 4, durationInput.getX() - left - editorX - 4, .85f);
        }
        g.drawCenteredString(font, (page + 1) + " / " + pageCount(), left + 10 + listWidth / 2,
                top + panelHeight - 47, LABEL_COLOR);
    }

    private void drawLabel(GuiGraphics g, Component label, int x, int y, int maxWidth, float maxScale) {
        float scale = Math.min(maxScale, maxWidth / (float) Math.max(1, font.width(label)));
        g.pose().pushPose();
        g.pose().translate(left + x, top + y + (font.lineHeight * (1 - scale) / 2), 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, label, 0, 0, LABEL_COLOR, false);
        g.pose().popPose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
