package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.client.HeartDashboardScreen;
import dev.sablelife.client.ManualScreen;
import dev.sablelife.client.MessageDisplayScreen;
import dev.sablelife.client.ModulePickerScreen;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.VirtualModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;

/** Opt-in graphical check using the normal dashboard and editor in an isolated world. */
@EventBusSubscriber(modid = "sablelife_tests", value = Dist.CLIENT)
public final class MessageDisplayClientPreview {
    private enum Step { CREATE, PLACE, OPEN, DASHBOARD, WAIT_MODULE, MANUAL, ADD, TITLE, SAVE, SAVE_TEXT, GLOBAL, HOVER, LAYOUT, RETURN, DONE }

    private static final boolean SURVIVAL = Boolean.getBoolean("sablelife.messageDisplaySurvivalPreview");

    private static Step step = Step.CREATE;
    private static int stepTicks;
    private static BlockPos corePosition;
    private static HeartDashboardScreen dashboard;
    private static MessageDisplayScreen editor;
    private static boolean tooltipRendered;
    private static boolean titleCaptured;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post ignored) throws Exception {
        if (!Boolean.getBoolean("sablelife.messageDisplayPreview") || step == Step.DONE) return;
        var mc = Minecraft.getInstance();
        require(++stepTicks <= 2000, "Timed out on " + step);
        switch (step) {
            case CREATE -> {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                mc.options.pauseOnLostFocus = false;
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                var settings = new LevelSettings("Message Display test", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("message-display", settings,
                        new WorldOptions(1234, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), mc.screen);
                advance(Step.PLACE);
            }
            case PLACE -> {
                if (mc.player == null || mc.screen != null) return;
                corePosition = mc.player.blockPosition().east(2);
                var server = mc.getSingleplayerServer();
                var playerId = mc.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    var level = player.serverLevel();
                    var block = SURVIVAL ? SableLife.SURVIVAL_LIFE_CORE.get() : SableLife.HEART.get();
                    level.setBlockAndUpdate(corePosition, block.defaultBlockState());
                    var core = (HeartBlockEntity) level.getBlockEntity(corePosition);
                    if (SURVIVAL) {
                        var setting = (ModConfigSpec.BooleanValue) SableLifeServerConfig.SPEC.getValues()
                                .get(java.util.List.of("modules", "survivalHeartCore", "message_display"));
                        setting.set(true);
                        player.setGameMode(GameType.SURVIVAL);
                    } else core.modules().add("message_display").approveCommands();
                });
                advance(Step.OPEN);
            }
            case OPEN -> {
                var block = SURVIVAL ? SableLife.SURVIVAL_LIFE_CORE.get() : SableLife.HEART.get();
                if (!mc.level.getBlockState(corePosition).is(block) || SURVIVAL && mc.player.isCreative()) return;
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(corePosition.getCenter(), Direction.UP, corePosition, false));
                advance(Step.DASHBOARD);
            }
            case DASHBOARD -> {
                if (!(mc.screen instanceof HeartDashboardScreen current)
                        || !current.getMenu().snapshot().getBoolean("Ready")) return;
                dashboard = current;
                click(dashboard, button(dashboard, translated("dashboard.sablelife.add_short")));
                require(mc.screen instanceof ModulePickerScreen, "Could not open the module picker");
                checkCategory(mc.screen, "module_picker.sablelife.");
                if (SURVIVAL) {
                    click(mc.screen, button(mc.screen, translated("dashboard.sablelife.type_message_display")));
                    click(mc.screen, button(mc.screen, translated("module_picker.sablelife.load")));
                } else mc.screen.onClose();
                advance(Step.WAIT_MODULE);
            }
            case WAIT_MODULE -> {
                if (dashboard.getMenu().snapshot().getList("Modules", 10).isEmpty()) return;
                mc.setScreen(new ManualScreen(dashboard, "message_display"));
                checkCategory(mc.screen, "manual.sablelife.");
                click(mc.screen, button(mc.screen, translated("manual.sablelife.example")));
                require(!button(mc.screen, translated("manual.sablelife.example")).active
                                && !translated("manual.sablelife.message_display.brief_example").contains("manual.sablelife."),
                        "The localized Message Display example is unavailable");
                advance(Step.MANUAL);
            }
            case MANUAL -> {
                if (stepTicks < 3) return;
                require(mc.screen instanceof ManualScreen, "Message Display manual closed early");
                capture(mc, "message-display-death-example");
                mc.screen.onClose();
                click(dashboard, button(dashboard, Component.translatable("dashboard.sablelife.choice_message_editor").getString()));
                require(mc.screen instanceof MessageDisplayScreen, "Dashboard did not open Message Display");
                editor = (MessageDisplayScreen) mc.screen;
                advance(Step.ADD);
            }
            case ADD -> {
                var module = selectedModule();
                require(module.messages().rows().size() == 1 && module.messages().rows().getFirst().text().isEmpty(),
                        "Default row is missing or prefilled");
                require(input("text").getValue().isEmpty() && input("radius").getValue().equals("64")
                        && input("delay_after").getValue().equals("1.00") && !input("subtitle").visible,
                        "Default editor values differ");
                scroll(input("radius"), 1);
                require(input("radius").getValue().equals("72"), "Radius wheel step differs from eight blocks");
                scroll(input("radius"), -1);
                require(input("radius").getValue().equals("64"), "Radius wheel reverse step differs from eight blocks");
                capture(mc, "message-display-chat");
                var add = button(editor, label("add"));
                click(editor, add);
                require(add.active && input("text").active && input("radius").active,
                        "Sending a change dimmed the editor while awaiting the server");
                click(editor, add);
                advance(Step.TITLE);
            }
            case TITLE -> {
                if (selectedModule().messages().rows().size() != 2 || !button(editor, label("mode_0")).active) return;
                button(editor, "▶ 2. " + label("empty"));
                click(editor, button(editor, label("mode_0")));
                advance(Step.SAVE);
            }
            case SAVE -> {
                var row = selectedModule().messages().rows().getLast();
                if (row.mode() != 1 || !input("text").active) return;
                require(input("subtitle").visible && input("subtitle").active
                        && input("duration").visible && input("duration").getValue().equals("3.00"),
                        "Title controls or duration missing");
                scroll(input("duration"), 1);
                require(input("duration").getValue().equals("4.00"), "Title duration wheel step differs from one second");
                scroll(input("duration"), -1);
                var delay = input("delay_after");
                click(editor, delay);
                delay.setValue("2.25");
                require(editor.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0), "Enter did not save the wait");
                advance(Step.SAVE_TEXT);
            }
            case SAVE_TEXT -> {
                if (selectedModule().messages().rows().getLast().delayAfter() != 45) return;
                var title = input("text");
                click(editor, title);
                title.setValue("&c&lRetreat");
                editor.keyPressed(GLFW.GLFW_KEY_E, 0, 0);
                require(mc.screen == editor, "E unexpectedly closed the editor");
                require(editor.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0), "Enter did not save the text");
                advance(Step.GLOBAL);
            }
            case GLOBAL -> {
                if (!selectedModule().messages().rows().getLast().text().equals("&c&lRetreat")) return;
                if (SURVIVAL) {
                    require(buttonIfPresent(editor, label("off")) == null && buttonIfPresent(editor, label("on")) == null
                            && input("radius").active && selectedModule().value("messageGlobal") == 0,
                            "Survival editor exposed broadcast or locked the recipient radius");
                    capture(mc, "message-display-survival-title");
                    mc.options.guiScale().set(4);
                    mc.resizeDisplay();
                    advance(Step.LAYOUT);
                    return;
                }
                if (!button(editor, label("off")).active) return;
                click(editor, button(editor, label("off")));
                advance(Step.HOVER);
            }
            case HOVER -> {
                var on = buttonIfPresent(editor, label("on"));
                if (selectedModule().value("messageGlobal") != 1 || on == null || !on.active) return;
                require(selectedModule().messages().rows().size() == 2, "A pending Add sent a duplicate request");
                require(!input("radius").active && input("delay_after").active
                        && input("delay_after").getValue().equals("2.25"), "Global scope changed the message wait");
                verifyLayout();
                if (!titleCaptured) {
                    capture(mc, "message-display-title");
                    titleCaptured = true;
                }
                pointAt(mc, input("subtitle"));
                if (!tooltipRendered) return;
                capture(mc, "message-display-format-help");
                mc.options.guiScale().set(4);
                mc.resizeDisplay();
                advance(Step.LAYOUT);
            }
            case LAYOUT -> {
                if (stepTicks < 4) return;
                verifyLayout();
                var locale = mc.getLanguageManager().getSelected();
                var chinese = locale.equals("zh_cn");
                require(editor.getTitle().getString().equals(chinese ? "消息显示" : "Message Display")
                        && label("mode_1").equals(chinese ? "屏幕标题" : "Title")
                        && label("format_help").contains(chinese ? "粗体" : "bold"), "Locale text did not load");
                capture(mc, "message-display-compact");
                click(editor, button(editor, label("back")));
                advance(Step.RETURN);
            }
            case RETURN -> {
                require(mc.screen == dashboard, "Back did not reopen the dashboard");
                System.out.println("MESSAGE_DISPLAY_UI_PASS " + mc.getLanguageManager().getSelected());
                advance(Step.DONE);
                mc.stop();
            }
            case DONE -> { }
        }
    }

    @SubscribeEvent
    public static void formatHelp(RenderTooltipEvent.Color tooltip) {
        if (step != Step.HOVER || editor == null || !input("subtitle").isHovered()) return;
        var font = Minecraft.getInstance().font;
        var expected = Tooltip.create(Component.translatable("message_display.sablelife.format_help"))
                .toCharSequence(Minecraft.getInstance()).stream().map(font::width).toList();
        var actual = tooltip.getComponents().stream().map(line -> line.getWidth(font)).toList();
        require(expected.equals(actual), "Wrong subtitle formatting tooltip");
        tooltipRendered = true;
    }

    private static void verifyLayout() {
        var text = input("text");
        var subtitle = input("subtitle");
        var radius = input("radius");
        var delay = input("delay_after");
        var duration = input("duration");
        require(text.getX() == subtitle.getX() && text.getRight() == subtitle.getRight()
                && text.getBottom() < subtitle.getY(), "Text and subtitle rows are misaligned");
        require(radius.getRight() == delay.getRight() && duration.getRight() == delay.getRight()
                && text.getBottom() < delay.getY() && delay.getBottom() < subtitle.getY(), "Numeric fields are misaligned");
        for (var child : editor.children()) {
            if (child instanceof AbstractWidget widget && widget.visible) {
                require(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= editor.width
                        && widget.getBottom() <= editor.height, "Widget escaped the editor bounds");
            }
        }
    }

    private static VirtualModule selectedModule() {
        var rows = dashboard.getMenu().snapshot().getList("Modules", 10);
        return VirtualModule.load(rows.getCompound(0));
    }

    private static EditBox input(String key) {
        return editor.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> box.getMessage().getString().equals(label(key))).findFirst().orElseThrow();
    }

    private static Button button(Screen screen, String text) {
        var button = buttonIfPresent(screen, text);
        require(button != null, "Missing button " + text);
        return button;
    }

    private static String translated(String key) {
        return Component.translatable(key).getString();
    }

    private static void checkCategory(Screen screen, String namespace) {
        var search = screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .findFirst().orElseThrow();
        search.setValue("message_display");
        String module = translated("dashboard.sablelife.type_message_display");
        click(screen, button(screen, translated(namespace + "signal")));
        require(buttonIfPresent(screen, module) == null, "Message Display still appears under Signals");
        click(screen, button(screen, translated(namespace + "function")));
        require(buttonIfPresent(screen, module) != null, "Message Display is missing under Functions");
    }

    private static void scroll(EditBox box, int direction) {
        require(editor.mouseScrolled(box.getX() + 2, box.getY() + 2, 0, direction), "Scroll was ignored");
    }

    private static Button buttonIfPresent(Screen screen, String text) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.visible && button.getMessage().getString().equals(text)).findFirst().orElse(null);
    }

    private static void click(Screen screen, AbstractWidget widget) {
        require(widget.active && screen.mouseClicked(widget.getX() + widget.getWidth() / 2.0,
                widget.getY() + widget.getHeight() / 2.0, 0), "Could not click " + widget.getMessage().getString());
    }

    private static void pointAt(Minecraft mc, AbstractWidget widget) throws ReflectiveOperationException {
        var window = mc.getWindow();
        double x = (widget.getX() + widget.getWidth() / 2.0) * window.getScreenWidth() / window.getGuiScaledWidth();
        double y = (widget.getY() + widget.getHeight() / 2.0) * window.getScreenHeight() / window.getGuiScaledHeight();
        var move = MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
        move.setAccessible(true);
        move.invoke(mc.mouseHandler, window.getWindow(), x, y);
    }

    private static void capture(Minecraft mc, String name) throws Exception {
        var folder = mc.gameDirectory.toPath().resolve("screenshots");
        Files.createDirectories(folder);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(folder.resolve(name + ".png"));
        }
    }

    private static String label(String key) {
        return Component.translatable("message_display.sablelife." + key).getString();
    }

    private static void advance(Step next) {
        step = next;
        stepTicks = 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
