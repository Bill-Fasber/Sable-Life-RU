package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.client.HeartDashboardScreen;
import dev.sablelife.client.SurvivalTopUpScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

/** Opens Refuel through a real core and sends the normal container button packets. */
@EventBusSubscriber(modid = "sablelife_tests", value = Dist.CLIENT)
public final class SurvivalRefuelClientPreview {
    private enum Step { CREATE, PLACE, OPEN, DASHBOARD, REFUEL, ENGLISH, CHINESE, OFF, STOCK, RELOAD, REOPEN, VERIFY, DONE }

    private static Step step = Step.CREATE;
    private static int ticks;
    private static BlockPos corePos;
    private static String worldName;
    private static CompletableFuture<?> languageReload;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("sablelife.survivalRefuelPreview") || step == Step.DONE) return;
        var mc = Minecraft.getInstance();
        if (++ticks > 1800) throw new AssertionError("Refuel test timed out on " + step);
        switch (step) {
            case CREATE -> {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null || !languageReady(mc, "en_us")) return;
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false;
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                var settings = new LevelSettings("Refuel test", GameType.SURVIVAL, false,
                        Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                worldName = "refuel-" + System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(worldName, settings, new WorldOptions(1234, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), mc.screen);
                advance(Step.PLACE);
            }
            case PLACE -> {
                if (mc.player == null || mc.screen != null) return;
                corePos = mc.player.blockPosition().east(2);
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    var level = server.overworld();
                    level.setBlockAndUpdate(corePos, SableLife.SURVIVAL_LIFE_CORE.get().defaultBlockState());
                    level.setBlockAndUpdate(corePos.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
                    ((HopperBlockEntity) level.getBlockEntity(corePos.above())).setItem(0, new ItemStack(Items.COPPER_INGOT, 3));
                });
                advance(Step.OPEN);
            }
            case OPEN -> {
                if (!mc.level.getBlockState(corePos).is(SableLife.SURVIVAL_LIFE_CORE.get())) return;
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(corePos.getCenter(), Direction.WEST, corePos, false));
                advance(Step.DASHBOARD);
            }
            case DASHBOARD -> {
                if (!(mc.screen instanceof HeartDashboardScreen dashboard) || !dashboard.getMenu().snapshot().getBoolean("Ready")) return;
                click(dashboard, button(dashboard, "balance_top_up", 0));
                advance(Step.REFUEL);
            }
            case REFUEL -> {
                if (ticks < 30 || !(mc.screen instanceof SurvivalTopUpScreen screen)
                        || screen.getMenu().snapshot().getLong("Value") != 30) return;
                var auto = button(screen, "automatic_off");
                checkLayout(screen, auto);
                if (screen.getMenu().snapshot().getLong("Balance") != 0 || screen.getMenu().getSlot(0).getItem().getCount() != 3)
                    throw new AssertionError("Hopper deposits did not synchronize to the player");
                screenshot(mc, "english-off");
                click(screen, auto);
                advance(Step.ENGLISH);
            }
            case ENGLISH -> {
                if (ticks < 8 || !(mc.screen instanceof SurvivalTopUpScreen screen)
                        || screen.getMenu().snapshot().getLong("Balance") != 30) return;
                checkLayout(screen, button(screen, "automatic_on"));
                screenshot(mc, "english-on");
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
                advance(Step.CHINESE);
            }
            case CHINESE -> {
                if (!languageReady(mc, "zh_cn") || mc.getOverlay() != null || !(mc.screen instanceof SurvivalTopUpScreen screen)) return;
                var auto = button(screen, "automatic_on");
                checkLayout(screen, auto);
                screenshot(mc, "chinese-on");
                click(screen, auto);
                advance(Step.OFF);
            }
            case OFF -> {
                if (ticks < 8 || !(mc.screen instanceof SurvivalTopUpScreen screen)
                        || screen.getMenu().snapshot().getBoolean("Automatic")) return;
                checkLayout(screen, button(screen, "automatic_off"));
                screenshot(mc, "chinese-off");
                var server = mc.getSingleplayerServer();
                server.execute(() -> ((HopperBlockEntity) server.overworld().getBlockEntity(corePos.above()))
                        .setItem(0, new ItemStack(Items.COPPER_INGOT, 2)));
                advance(Step.STOCK);
            }
            case STOCK -> {
                if (!(mc.screen instanceof SurvivalTopUpScreen screen) || screen.getMenu().snapshot().getLong("Value") != 20) return;
                advance(Step.RELOAD);
                mc.level.disconnect();
                mc.disconnect(new TitleScreen());
            }
            case RELOAD -> {
                if (mc.level != null || mc.getOverlay() != null || !(mc.screen instanceof TitleScreen)) return;
                advance(Step.REOPEN);
                mc.createWorldOpenFlows().openWorld(worldName, () -> mc.setScreen(new TitleScreen()));
            }
            case REOPEN -> {
                if (mc.screen != null || mc.player == null || !mc.level.getBlockState(corePos).is(SableLife.SURVIVAL_LIFE_CORE.get())) return;
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(corePos.getCenter(), Direction.WEST, corePos, false));
                advance(Step.VERIFY);
            }
            case VERIFY -> {
                if (mc.screen instanceof HeartDashboardScreen dashboard && dashboard.getMenu().snapshot().getBoolean("Ready")) {
                    click(dashboard, button(dashboard, "balance_top_up", 30));
                    return;
                }
                if (!(mc.screen instanceof SurvivalTopUpScreen screen) || !screen.getMenu().snapshot().contains("Automatic")) return;
                var data = screen.getMenu().snapshot();
                if (data.getBoolean("Automatic") || data.getLong("Balance") != 30 || data.getLong("Value") != 20
                        || screen.getMenu().getSlot(0).getItem().getCount() != 2)
                    throw new AssertionError("Stored fuel, Energy or Auto Refuel changed after reopening");
                advance(Step.DONE);
                System.out.println("SURVIVAL_REFUEL_UI_PASS");
                mc.stop();
            }
            case DONE -> {}
        }
    }

    private static void advance(Step next) { step = next; ticks = 0; }

    private static boolean languageReady(Minecraft mc, String locale) {
        if (languageReload != null && !languageReload.isDone()) return false;
        if (mc.options.languageCode.equals(locale)) return true;
        mc.options.languageCode = locale;
        mc.getLanguageManager().setSelected(locale);
        languageReload = mc.reloadResourcePacks();
        return false;
    }

    private static Button button(Screen screen, String key, Object... args) {
        var label = Component.translatable("survival_tokens.sablelife." + key, args).getString();
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }

    private static void click(Screen screen, Button button) {
        if (!screen.mouseClicked(button.getX() + button.getWidth() / 2., button.getY() + button.getHeight() / 2., 0))
            throw new AssertionError("The button did not accept a click");
    }

    private static void checkLayout(SurvivalTopUpScreen screen, Button auto) {
        var confirm = button(screen, "confirm");
        if (auto.getTooltip() == null || auto.getX() != confirm.getX() || auto.getWidth() != confirm.getWidth()
                || auto.getY() <= confirm.getY() + confirm.getHeight() || auto.getY() + auto.getHeight() > screen.height)
            throw new AssertionError("Auto Refuel is clipped or misaligned");
    }

    private static void screenshot(Minecraft mc, String name) throws Exception {
        var path = mc.gameDirectory.toPath().resolve("screenshots");
        Files.createDirectories(path);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(path.resolve(name + ".png"));
        }
    }
}
