package dev.sablelife.client;

import dev.sablelife.item.CommandWandItem;
import dev.sablelife.module.CommandTarget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

final class CommandWandScreen extends Screen {
    private static final int PADDING = 12;
    private static final int CONTROL_HEIGHT = 20;
    private static final int DURATION_Y = 34;
    private static final int FIRST_TOGGLE_Y = 60;
    private static final int ROW_SPACING = 24;
    private static final Component DURATION_LABEL = Component.translatable("screen.sablelife.command_wand_duration");
    private static final List<ModuleOption> MODULES = List.of(
            new ModuleOption(ModuleText.name("navigation"), CommandTarget.TARGET_ALIGNMENT, "screen.sablelife.command_wand_alignment_tip"),
            new ModuleOption(ModuleText.name("auto_facing"), CommandTarget.AUTO_FACING, "screen.sablelife.command_wand_facing_tip"),
            new ModuleOption(ModuleText.name("linear_movement"), CommandTarget.LINEAR_MOVEMENT, "screen.sablelife.command_wand_movement_tip"),
            new ModuleOption(Component.translatable("screen.sablelife.command_wand_weapon"), CommandTarget.WEAPON_TARGETING,
                    "screen.sablelife.command_wand_weapon_tip"));
    private static final int FOOTER_Y = FIRST_TOGGLE_Y + ROW_SPACING * MODULES.size() + 2;
    private static final int PANEL_HEIGHT = FOOTER_Y + CONTROL_HEIGHT + PADDING;

    private record ModuleOption(Component label, int mask, String tooltipKey) {}

    private final int initialSeconds;
    private final int initialModules;
    private final List<Button> moduleToggles = new ArrayList<>(MODULES.size());
    private int enabledModules;
    private EditBox duration;
    private Button save;
    private int left;
    private int top;
    private int panelWidth;

    CommandWandScreen(int seconds, int enabledModules) {
        super(Component.translatable("screen.sablelife.command_wand_settings"));
        initialSeconds = seconds;
        initialModules = enabledModules;
        this.enabledModules = enabledModules;
    }

    @Override
    protected void init() {
        var previous = duration == null ? Integer.toString(initialSeconds) : duration.getValue();
        panelWidth = Math.min(246, width - 20);
        left = (width - panelWidth) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        int x = left + PADDING;
        int fieldWidth = panelWidth - PADDING * 2;
        int controlWidth = Math.min(84, fieldWidth / 3);
        int controlX = x + fieldWidth - controlWidth;
        duration = addRenderableWidget(new EditBox(font, controlX, top + DURATION_Y,
                controlWidth, CONTROL_HEIGHT, DURATION_LABEL));
        duration.setMaxLength(3);
        duration.setFilter(text -> text.chars().allMatch(c -> c >= '0' && c <= '9'));
        duration.setValue(previous);
        moduleToggles.clear();
        for (int i = 0; i < MODULES.size(); i++) {
            int row = i;
            var toggle = addRenderableWidget(LifeUiStyle.button(Component.empty(), controlX,
                    top + FIRST_TOGGLE_Y + ROW_SPACING * row, controlWidth, CONTROL_HEIGHT, () -> toggle(row)));
            toggle.setTooltip(Tooltip.create(Component.translatable(MODULES.get(row).tooltipKey())));
            moduleToggles.add(toggle);
            updateToggle(row);
        }
        int half = (fieldWidth - 6) / 2;
        save = addRenderableWidget(LifeUiStyle.button(Component.translatable("screen.sablelife.command_wand_save"),
                x, top + FOOTER_Y, half, CONTROL_HEIGHT, this::save));
        addRenderableWidget(LifeUiStyle.button(Component.translatable("gui.cancel"),
                x + half + 6, top + FOOTER_Y, fieldWidth - half - 6, CONTROL_HEIGHT, this::onClose));
        duration.setResponder(value -> save.active = validSeconds(value));
        save.active = validSeconds(previous);
        setInitialFocus(duration);
    }

    private void toggle(int row) {
        enabledModules ^= MODULES.get(row).mask();
        updateToggle(row);
    }

    private void updateToggle(int row) {
        var toggle = moduleToggles.get(row);
        boolean enabled = (enabledModules & MODULES.get(row).mask()) != 0;
        toggle.setMessage(Component.translatable(enabled ? "dashboard.sablelife.enabled" : "dashboard.sablelife.disabled"));
        LifeUiStyle.fill(toggle, enabled ? 0xFF24583D : 0);
    }

    private static boolean validSeconds(String text) {
        if (text.isEmpty()) return false;
        int seconds = Integer.parseInt(text);
        return seconds >= CommandWandItem.MIN_SECONDS && seconds <= CommandWandItem.MAX_SECONDS;
    }

    private void save() {
        if (!save.active) return;
        int seconds = Integer.parseInt(duration.getValue());
        var input = enabledModules == initialModules
                ? new CommandWandItem.Input(CommandWandItem.Operation.DURATION, seconds)
                : CommandWandItem.Input.settings(seconds, enabledModules);
        PacketDistributor.sendToServer(input);
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (duration.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            save();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0 || !duration.isMouseOver(x, y)) return super.mouseScrolled(x, y, horizontal, vertical);
        int seconds = duration.getValue().isEmpty() ? initialSeconds : Integer.parseInt(duration.getValue());
        duration.setValue(Integer.toString(Math.clamp(seconds + (vertical > 0 ? 5 : -5),
                CommandWandItem.MIN_SECONDS, CommandWandItem.MAX_SECONDS)));
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.renderBackground(graphics, mouseX, mouseY, partial);
        graphics.fill(left, top, left + panelWidth, top + PANEL_HEIGHT, 0xFF9B614E);
        graphics.fill(left + 2, top + 2, left + panelWidth - 2, top + PANEL_HEIGHT - 2, 0xFF242632);
        graphics.drawCenteredString(font, title, width / 2, top + 14, 0xFFE6D8EA);
        drawRowLabel(graphics, DURATION_LABEL, DURATION_Y);
        for (int i = 0; i < MODULES.size(); i++) {
            drawRowLabel(graphics, MODULES.get(i).label(), FIRST_TOGGLE_Y + ROW_SPACING * i);
        }
    }

    private void drawRowLabel(GuiGraphics graphics, Component label, int rowY) {
        int labelX = left + PADDING;
        int labelWidth = duration.getX() - labelX - 6;
        float scale = Math.min(1f, labelWidth / (float) Math.max(1, font.width(label)));
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(labelX, top + rowY + (CONTROL_HEIGHT - font.lineHeight * scale) / 2, 0);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, label, 0, 0, 0xFFE6D8EA, false);
        pose.popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
