package dev.sablelife.client;

import dev.sablelife.module.HeartDashboardMenu;
import dev.sablelife.module.ModuleNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Selects server-local blueprint files and displays their metadata. */
public final class BlueprintSpawnScreen extends Screen {
    private static final int COLUMNS = 3;
    private static final int PADDING = 10;
    private static final int COLUMN_GAP = 6;
    private static final int ROW_HEIGHT = 22;
    private static final int GRID_Y = 112;
    private static final String ACTIVATE = "spawnActivate";
    private static final Comparator<String> NAME_ORDER = String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());

    private final Screen parent;
    private final HeartDashboardMenu menu;
    private final UUID module;
    private int x, y, w, h, page;
    private int activationX;
    private int pageCount = 1;
    private String draft = "";
    private boolean requested;
    private EditBox input;
    private CustomData lastSnapshot;
    private CompoundTag metadata = new CompoundTag();
    private List<String> files = List.of();
    private boolean editable;
    private boolean activateCore;

    public BlueprintSpawnScreen(Screen parent, HeartDashboardMenu menu, UUID module) {
        super(tr("title"));
        this.parent = parent;
        this.menu = menu;
        this.module = module;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("blueprint_spawn.sablelife." + key, args);
    }

    private void send(String key, String text) {
        send("blueprint_spawn", key, 0, text);
    }

    private void send(String operation, String key, int value, String text) {
        PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId, menu.snapshot().getInt("Revision"),
                module, operation, key, value, text));
    }

    private Button button(Component text, int px, int py, int width, int height, Runnable action) {
        return addRenderableWidget(LifeUiStyle.button(text, px, py, width, height, action));
    }

    private boolean refreshSnapshot() {
        var current = menu.getSlot(0).getItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (current == lastSnapshot) return false;
        lastSnapshot = current;
        var snapshot = current.copyTag();
        var updated = new CompoundTag();
        boolean updatedActivation = false;
        var modules = snapshot.getList("Modules", 10);
        for (int i = 0; i < modules.size(); i++) {
            var row = modules.getCompound(i);
            if (row.hasUUID("Id") && row.getUUID("Id").equals(module)) {
                updated = row.getCompound("BlueprintSpawn");
                updatedActivation = row.getCompound("Settings").getDouble(ACTIVATE) == 1;
                break;
            }
        }
        boolean canEdit = snapshot.getBoolean("Editable") && snapshot.getBoolean("RespawnAllowed") && updated.getBoolean("Available");
        var updatedFiles = snapshot.getList("BlueprintFiles", 8).stream()
                .map(entry -> entry.getAsString()).sorted(NAME_ORDER).toList();
        boolean rebuild = editable != canEdit || !files.equals(updatedFiles)
                || !metadata.getString("Name").equals(updated.getString("Name"))
                || metadata.getBoolean("CanActivate") != updated.getBoolean("CanActivate")
                || activateCore != updatedActivation;
        metadata = updated;
        files = updatedFiles;
        editable = canEdit;
        activateCore = updatedActivation;
        return rebuild;
    }

    @Override
    protected void init() {
        refreshSnapshot();
        w = Math.min(480, width - 16);
        h = Math.min(330, height - 16);
        x = (width - w) / 2;
        y = (height - h) / 2;
        int innerWidth = w - PADDING * 2;
        int right = x + w - PADDING;
        int actionWidth = Math.min(64, innerWidth / 5);
        int clearX = right - actionWidth;
        activationX = clearX;
        int loadX = clearX - COLUMN_GAP - actionWidth;

        button(Component.translatable("dashboard.sablelife.back"), right - 60, y + 8, 60, 20, this::onClose);
        input = addRenderableWidget(new EditBox(font, x + PADDING, y + 36,
                loadX - COLUMN_GAP - (x + PADDING), 20, tr("name")));
        input.setMaxLength(80);
        input.setHint(tr("name"));
        input.setValue(draft);
        input.setResponder(text -> draft = text);
        input.active = editable;
        button(tr("load"), loadX, y + 36, actionWidth, 20, () -> send("load", draft)).active = editable;
        button(tr("clear"), clearX, y + 36, actionWidth, 20, () -> send("clear", "")).active =
                editable && !metadata.getString("Name").isEmpty();
        var activate = button(Component.translatable("dashboard.sablelife.choice_" + (activateCore ? "on" : "off")),
                clearX, y + 62, actionWidth, 18, () -> send("edit", ACTIVATE, activateCore ? 0 : 1, ""));
        activate.active = editable && (activateCore || metadata.getString("Name").isEmpty() || metadata.getBoolean("CanActivate"));
        if (activateCore) LifeUiStyle.fill(activate, 0xFF24583D);

        int pagerY = y + h - 40;
        int rows = Math.max(1, (pagerY - (y + GRID_Y) - 4) / ROW_HEIGHT);
        int capacity = rows * COLUMNS;
        pageCount = Math.max(1, (files.size() + capacity - 1) / capacity);
        page = Math.clamp(page, 0, pageCount - 1);
        int gridWidth = innerWidth - (COLUMNS - 1) * COLUMN_GAP;
        int start = page * capacity;
        for (int i = 0; i < capacity && start + i < files.size(); i++) {
            String name = files.get(start + i);
            int column = i / rows;
            int left = x + PADDING + column * COLUMN_GAP + gridWidth * column / COLUMNS;
            int columnWidth = gridWidth * (column + 1) / COLUMNS - gridWidth * column / COLUMNS;
            var label = Component.literal(name);
            var entry = button(label, left, y + GRID_Y + (i % rows) * ROW_HEIGHT, columnWidth, 18, () -> {
                input.setValue(name);
                send("load", name);
            });
            entry.active = editable;
            entry.setTooltip(Tooltip.create(label));
            if (name.equals(metadata.getString("Name"))) LifeUiStyle.fill(entry, 0xFF24583D);
        }

        button(Component.literal("‹"), x + PADDING, pagerY, 28, 18, () -> changePage(-1)).active = page > 0;
        button(Component.literal("›"), right - 28, pagerY, 28, 18, () -> changePage(1)).active = page + 1 < pageCount;
        if (!requested) {
            requested = true;
            send("list", "");
        }
    }

    private void changePage(int direction) {
        page += direction;
        rebuildWidgets();
    }

    @Override
    public void tick() {
        if (minecraft.player != null && minecraft.player.containerMenu != menu) {
            minecraft.setScreen(null);
            return;
        }
        if (refreshSnapshot()) rebuildWidgets();
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
        graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF9B614E);
        graphics.fill(x, y, x + w, y + h, 0xFF242632);
        drawLabel(graphics, title, x + PADDING, y + 14, w - 88, 0xD9C4ED, 1f);

        int infoWidth = (w - PADDING * 2 - COLUMN_GAP) / 2;
        int infoRight = x + PADDING + infoWidth + COLUMN_GAP;
        var phase = metadata.getString("Phase");
        var activateLabel = Component.translatable("wild.sablelife.activate_core_question");
        int labelWidth = (int) Math.ceil(font.width(activateLabel) * .85f);
        drawLabel(graphics, activateLabel, activationX - COLUMN_GAP - labelWidth, y + 68,
                labelWidth, 0xD9C4ED, .85f);
        drawLabel(graphics, tr("selected", displayValue("Name")), x + PADDING, y + 85, infoWidth, 0xD9C4ED, .85f);
        boolean arenaError = metadata.getString("Error").equals("arena");
        var state = arenaError ? tr("error_arena")
                : tr("state", tr("phase_" + (phase.isEmpty() ? "empty" : phase)), metadata.getInt("Count"));
        drawLabel(graphics, state, infoRight, y + 85, infoWidth, arenaError ? 0xFFBC65 : 0x79D3AE, .85f);
        drawLabel(graphics, tr("cores", metadata.getInt("Life"), metadata.getInt("Redstone"), metadata.getInt("Blocks")),
                x + PADDING, y + 98, infoWidth, 0xAEB8C7, .85f);
        drawLabel(graphics, tr("position", displayValue("Position")), infoRight, y + 98, infoWidth, 0xAEB8C7, .85f);
        graphics.drawCenteredString(font, (page + 1) + " / " + pageCount, x + w / 2, y + h - 35, 0xD9C4ED);
        drawLabel(graphics, tr(metadata.getBoolean("Available") ? "directory" : "requires"),
                x + PADDING, y + h - 16, w - PADDING * 2, 0xAEB8C7, .85f);
    }

    private String displayValue(String key) {
        var value = metadata.getString(key);
        return value.isEmpty() ? "—" : value;
    }

    private void drawLabel(GuiGraphics graphics, Component label, int px, int py, int width, int color, float scale) {
        String text = label.getString();
        int available = (int) (width / scale);
        if (font.width(text) > available) {
            text = font.plainSubstrByWidth(text, Math.max(0, available - font.width("…"))) + "…";
        }
        graphics.pose().pushPose();
        graphics.pose().translate(px, py, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }
}
