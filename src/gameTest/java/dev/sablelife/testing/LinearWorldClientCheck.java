package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.client.HeartDashboardScreen;
import dev.sablelife.client.LifeUiStyle;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.ModuleFields;
import dev.sablelife.module.VirtualModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = "sablelife_tests", value = Dist.CLIENT)
public final class LinearWorldClientCheck {
    private static int phase, ticks;
    private static BlockPos core, ground;
    private static CompletableFuture<Void> reload;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post ignored) throws ReflectiveOperationException {
        if (!Boolean.getBoolean("sablelife.linearWorldClientCheck")) return;
        var mc = Minecraft.getInstance();
        if (++ticks > 1600) throw new AssertionError("World movement client check timed out at phase " + phase);
        if (phase == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            phase++;
            mc.options.pauseOnLostFocus = false;
            GLFW.glfwHideWindow(mc.getWindow().getWindow());
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
            while (LifeUiStyle.density() != 3) LifeUiStyle.cycle();
            var settings = new LevelSettings("World destination", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                    new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("world-destination", settings, new WorldOptions(1234, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), mc.screen);
        }
        if (phase == 11 && mc.level == null && mc.getOverlay() == null) {
            phase++;
            mc.createWorldOpenFlows().openWorld("world-destination", () -> mc.setScreen(new TitleScreen()));
        }
        if (mc.player == null || mc.level == null) return;
        if (phase == 1 && mc.screen == null) {
            phase++;
            core = mc.player.blockPosition().east(2);
            ground = mc.player.blockPosition().north(2);
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.overworld();
                level.setBlockAndUpdate(core, SableLife.HEART.get().defaultBlockState());
                level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
                var heart = (HeartBlockEntity) level.getBlockEntity(core);
                heart.modules().add("linear_movement").edit("linearMode", 0, "");
            });
        }
        if (phase == 2 && mc.level.getBlockState(core).is(SableLife.HEART.get())) {
            phase++;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit(core));
        }
        if (phase == 3 && mc.screen instanceof HeartDashboardScreen screen && module(screen) != null) {
            phase++;
            verify(screen, "moveDistance", "moveHorizontal", "moveVertical", "direction", "moveObstruction", "moveForce");
            click(screen, button(screen, label("choice_move_fixed")), 1);
        } else if (phase == 4 && mc.screen instanceof HeartDashboardScreen screen && module(screen).value("moveAim") == 2) {
            phase++;
            verify(screen, "moveObstruction", "moveForce");
            click(screen, button(screen, label("mark_move_world")), 0);
        } else if (phase == 5 && mc.screen == null && HeartDashboardScreen.marking) {
            phase++;
            var delta = ground.getCenter().subtract(mc.player.getEyePosition());
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround()));
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit(ground));
        } else if (phase == 6 && mc.screen instanceof HeartDashboardScreen screen && module(screen) != null) {
            phase++;
            if (!ground.above().getCenter().equals(module(screen).arenaCenter())) throw new AssertionError("Wrong marked coordinate");
            click(screen, button(screen, label("preview")), 0);
        } else if (phase == 7 && mc.screen == null) {
            phase++;
            mc.getLanguageManager().setSelected("zh_cn");
            mc.options.languageCode = "zh_cn";
            mc.options.guiScale().set(4);
            mc.resizeDisplay();
            while (LifeUiStyle.density() != 0) LifeUiStyle.cycle();
            reload = mc.reloadResourcePacks();
        } else if (phase == 8 && reload.isDone() && mc.getOverlay() == null) {
            phase++;
            reload.join();
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit(core));
        } else if (phase == 9 && mc.screen instanceof HeartDashboardScreen screen && module(screen) != null) {
            phase++;
            verify(screen, "moveObstruction", "moveForce");
            button(screen, label("choice_move_world"));
            click(screen, button(screen, label("choice_move_teleport")), 0);
        } else if (phase == 10 && mc.screen instanceof HeartDashboardScreen screen && module(screen).value("linearMode") == 1) {
            verify(screen, "travelSpeed", "moveGravity", "moveForce");
            phase++;
            mc.level.disconnect();
            mc.disconnect(new TitleScreen());
        } else if (phase == 12 && mc.screen == null && mc.level.getBlockState(core).is(SableLife.HEART.get())) {
            phase++;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit(core));
        } else if (phase == 13 && mc.screen instanceof HeartDashboardScreen screen && module(screen) != null) {
            phase++;
            var movement = module(screen);
            if (movement.value("linearMode") != 1 || movement.value("moveAim") != 2
                    || !ground.above().getCenter().equals(movement.arenaCenter()))
                throw new AssertionError("Movement mode or destination did not survive reopening");
            click(screen, button(screen, label("choice_move_world")), 1);
        } else if (phase == 14 && mc.screen instanceof HeartDashboardScreen screen && module(screen).value("moveAim") == 1) {
            verify(screen, "target", "moveTargetRange", "moveStopDistance", "moveTargetVertical", "facingCreative",
                    "travelSpeed", "moveGravity", "moveStep", "moveForce");
            System.out.println("LINEAR_WORLD_CLIENT_PASS: both languages, all aims, world ray mark, preview, Travel and save/reopen");
            mc.stop();
        }
    }

    private static void verify(HeartDashboardScreen screen, String... expected) throws ReflectiveOperationException {
        var parameters = HeartDashboardScreen.class.getDeclaredField("parameters");
        parameters.setAccessible(true);
        var keys = ((List<?>) parameters.get(screen)).stream().map(p -> ((ModuleFields.Field) p).key()).toList();
        var conditional = List.of("target", "moveTargetRange", "moveStopDistance", "moveTargetVertical", "facingCreative",
                "moveDistance", "moveHorizontal", "moveVertical", "direction", "travelSpeed", "moveGravity",
                "moveStep", "moveStepHeight", "moveStepCooldown", "moveObstruction", "moveForce");
        var actual = keys.stream().filter(conditional::contains).collect(java.util.stream.Collectors.toSet());
        if (!actual.equals(java.util.Set.of(expected))) throw new AssertionError("Unexpected visible controls " + actual);
        for (String key : keys) if (label(key).startsWith("dashboard.")) throw new AssertionError("Missing translation " + key);
    }

    private static VirtualModule module(HeartDashboardScreen screen) {
        var modules = screen.getMenu().snapshot().getList("Modules", 10);
        return modules.isEmpty() ? null : VirtualModule.load(modules.getCompound(0));
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(pos.getCenter(), Direction.UP, pos, false);
    }

    private static String label(String key) {
        return Component.translatable("dashboard.sablelife." + key).getString();
    }

    private static Button button(HeartDashboardScreen screen, String label) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.visible && b.active && b.getMessage().getString().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError("Button missing " + label + " at phase " + phase));
    }

    private static void click(HeartDashboardScreen screen, Button button, int mouseButton) {
        if (!screen.mouseClicked(button.getX() + button.getWidth() / 2., button.getY() + button.getHeight() / 2., mouseButton))
            throw new AssertionError("Cannot click " + button.getMessage().getString());
    }
}
