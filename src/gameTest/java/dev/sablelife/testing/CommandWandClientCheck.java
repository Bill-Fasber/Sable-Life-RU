package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.client.ManualScreen;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.item.CommandWandItem;
import dev.sablelife.module.CommandTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "sablelife_tests")
public final class CommandWandClientCheck {
    private static final BlockPos CORE = new BlockPos(4, 96, 4);
    private static final BlockPos LIFE_CORE = new BlockPos(4, 100, 132);
    private static final BlockPos GROUND = CORE.south(128).below();
    private static final BlockPos COVERED = new BlockPos(0, 94, 11);
    private static final Vec3 MOB = CORE.getCenter().add(125, 0, 2);
    private static final Vec3 START = CORE.getCenter().add(0, -1.62, 3);
    private static final int TEST_MOVEMENT_MODULES = CommandTarget.TARGET_ALIGNMENT | CommandTarget.LINEAR_MOVEMENT;
    private static final int TEST_MODULES = TEST_MOVEMENT_MODULES | CommandTarget.WEAPON_TARGETING;
    private static int serverPhase, delay;
    private static HeartBlockEntity targetLife;

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("sablelife.commandWandNetwork")) return;
        var server = event.getServer();
        var player = server.getPlayerList().getPlayerByName("WandTester");
        if (player == null) return;
        var level = server.overworld();
        if (serverPhase == 0) {
            String replay = System.getProperty("sablelife.commandWandReplay", "");
            if (!replay.isEmpty()) {
                var savedWand = player.getMainHandItem();
                int expectedModules = switch (replay) {
                    case "legacy" -> CommandTarget.ALL_MODULES;
                    case "saved" -> TEST_MODULES;
                    default -> throw new AssertionError("Unknown Wand playerdata replay: " + replay);
                };
                if (!savedWand.is(SableLife.COMMAND_WAND.get()) || CommandWandItem.duration(savedWand) != 7
                        || CommandWandItem.enabledModulesMask(savedWand) != expectedModules) {
                    throw new AssertionError("Loaded wand settings did not match the " + replay + " playerdata");
                }
                System.out.println("COMMAND_WAND_RELOAD_PASS " + replay);
                if (replay.equals("saved")) {
                    serverPhase++;
                    server.halt(false);
                    return;
                }
            }
            level.setBlockAndUpdate(CORE, SableLife.HEART.get().defaultBlockState());
            level.setBlockAndUpdate(GROUND, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(COVERED, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(COVERED.below(), Blocks.SANDSTONE.defaultBlockState());
            level.setBlockAndUpdate(COVERED.east(), Blocks.SANDSTONE.defaultBlockState());
            for (var pos : BlockPos.betweenClosed(GROUND.offset(-4, -1, -4), GROUND.offset(4, -1, 4))) {
                level.setBlockAndUpdate(pos, Blocks.SANDSTONE.defaultBlockState());
            }
            level.setBlockAndUpdate(BlockPos.containing(START).below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(LIFE_CORE.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(LIFE_CORE.east().below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(LIFE_CORE, SableLife.HEART.get().defaultBlockState());
            level.setBlockAndUpdate(LIFE_CORE.east(), Blocks.IRON_BLOCK.defaultBlockState());
            var bounds = new BoundingBox3i(LIFE_CORE.getX(), LIFE_CORE.getY(), LIFE_CORE.getZ(),
                    LIFE_CORE.getX() + 1, LIFE_CORE.getY(), LIFE_CORE.getZ());
            var body = SubLevelAssemblyHelper.assembleBlocks(level, LIFE_CORE,
                    java.util.List.of(LIFE_CORE, LIFE_CORE.east()), bounds);
            targetLife = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
            if (!targetLife.activateForRespawn()) throw new AssertionError("Could not activate the wand test Life");
            var core = (HeartBlockEntity) level.getBlockEntity(CORE);
            core.relations().excludeMob("minecraft:pig");
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.setNoGravity(true);
            player.teleportTo(START.x, START.y, START.z);
            if (replay.isEmpty()) {
                var wand = SableLife.COMMAND_WAND.get().getDefaultInstance();
                if (CommandWandItem.enabledModulesMask(wand) != CommandTarget.SUPPORTED_MODULES) {
                    throw new AssertionError("A newly issued Command Wand did not enable weapon follow");
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, wand);
            }
            var pig = EntityType.PIG.create(level);
            pig.setNoAi(true);
            pig.setNoGravity(true);
            pig.setPos(MOB.add(0, -pig.getBbHeight() / 2, 0));
            level.addFreshEntity(pig);
            serverPhase++;
            return;
        }
        if (!(level.getBlockEntity(CORE) instanceof HeartBlockEntity core)) return;
        var order = core.modules().commandTarget().current(level);
        if (order != null && COVERED.above().equals(order.destination())) {
            throw new AssertionError("Immediate destination click replaced the preceding mob order");
        }
        if (serverPhase >= 4 && order != null && order.entity() != null) {
            throw new AssertionError("Immediate mob click replaced the preceding destination order");
        }
        if (serverPhase == 1 && core.remoteId() != null) {
            core.health().activate();
            serverPhase++;
        } else if (serverPhase == 2 && order != null && order.entity() != null) {
            if (order.entity().getHealth() != 10) throw new AssertionError("Command click damaged the target");
            if (order.enabledModulesMask() != TEST_MODULES
                    || !order.entity().getUUID().equals(core.modules().commandTarget().weaponTarget(level))) {
                throw new AssertionError("Mob order did not supply the commanded weapon target");
            }
            System.out.println("COMMAND_WAND_MOB_TARGET_PASS " + order.entity().distanceTo(player));
            serverPhase++;
        } else if (serverPhase == 3 && order != null && targetLife.creatureId().equals(order.lifeId())) {
            if (targetLife.health().current() != targetLife.health().maximum()) {
                throw new AssertionError("Command click damaged the target Life");
            }
            if (order.enabledModulesMask() != TEST_MODULES
                    || !order.lifeId().equals(core.modules().commandTarget().weaponTarget(level))) {
                throw new AssertionError("Life order did not supply the commanded weapon target");
            }
            System.out.println("COMMAND_WAND_LIFE_TARGET_PASS " + player.getEyePosition().distanceTo(
                    SableBodyProbe.toWorld(level, targetLife.getBlockPos().east().getCenter())));
            System.out.println("COMMAND_WAND_MOB_THEN_TERRAIN_COOLDOWN_PASS");
            serverPhase++;
        } else if (serverPhase == 3 && (order == null || order.entity() == null)) {
            throw new AssertionError("The mob order did not survive the rapid follow-up click");
        } else if (serverPhase == 4 && order != null && GROUND.above().equals(order.destination())) {
            var wand = player.getMainHandItem();
            if (CommandWandItem.duration(wand) != 7 || CommandWandItem.enabledModulesMask(wand) != TEST_MODULES
                    || order.enabledModulesMask() != TEST_MOVEMENT_MODULES
                    || core.modules().commandTarget().weaponTarget(level) != null) {
                throw new AssertionError("Duration or module toggles did not reach the server");
            }
            System.out.println("COMMAND_WAND_TERRAIN_TARGET_PASS " + player.getEyePosition().distanceTo(GROUND.getCenter()));
            delay = 25;
            serverPhase++;
        } else if (serverPhase == 5) {
            if (order == null || !GROUND.above().equals(order.destination())) {
                throw new AssertionError("The destination order did not survive the rapid follow-up click");
            }
            if (--delay > 0) return;
            System.out.println("COMMAND_WAND_TERRAIN_THEN_MOB_COOLDOWN_PASS");
            player.setGameMode(GameType.SURVIVAL);
            serverPhase++;
            delay = 40;
        } else if (serverPhase == 6 && --delay <= 0) {
            if (order == null || !GROUND.above().equals(order.destination())) {
                throw new AssertionError("Survival could override an ordinary core's order");
            }
            System.out.println("COMMAND_WAND_SERVER_PASS");
            serverPhase++;
            server.halt(false);
        }
    }

    @EventBusSubscriber(modid = "sablelife_tests", value = Dist.CLIENT)
    public static final class Client {
        private static int phase, ticks, since, rendered;
        private static boolean destinationCaptured;
        private static boolean distantPigRendered;
        private static boolean weaponHelpRendered;
        private static boolean itemHelpRendered;
        private static boolean itemHelpCaptured;
        private static String lifeAimStatus;
        private static String mobAimStatus;
        private static Button weaponToggle;
        private static CustomData previousWandData;

        @SubscribeEvent
        public static void render(RenderLevelStageEvent event) {
            if (Boolean.getBoolean("sablelife.commandWandClient")
                    && event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) rendered++;
        }

        @SubscribeEvent
        public static void renderPig(RenderLivingEvent.Pre<?, ?> event) {
            if (phase == 4 && event.getEntity() instanceof Pig pig
                    && pig.position().distanceToSqr(MOB) < 4
                    && Minecraft.getInstance().shouldEntityAppearGlowing(pig)) {
                distantPigRendered = true;
            }
        }

        @SubscribeEvent
        public static void renderWandHelp(RenderTooltipEvent.Color event) {
            if (phase == 8 && event.getItemStack().is(SableLife.COMMAND_WAND.get())) {
                var mc = Minecraft.getInstance();
                String scope = Component.translatable("tooltip.sablelife.command_wand_weapon_scope").getString();
                String targeting = Component.translatable("tooltip.sablelife.command_wand_weapon_target").getString();
                boolean chinese = mc.options.languageCode.equals("zh_cn");
                if (!(chinese ? scope.contains("武器跟随左键目标") && targeting.contains("自定义动作抽选")
                        : scope.contains("left-click target") && targeting.contains("Move Set target"))) {
                    throw new AssertionError("Weapon targeting notice was not localized");
                }
                var widths = event.getComponents().stream().map(line -> line.getWidth(mc.font)).toList();
                if (!widths.contains(mc.font.width(scope)) || !widths.contains(mc.font.width(targeting))) {
                    throw new AssertionError("Command Wand tooltip did not render both targeting lines");
                }
                itemHelpRendered = true;
            }
            if (phase != 16 || !weaponToggle.isHovered()) return;
            var mc = Minecraft.getInstance();
            var expected = moduleTooltip("weapon").toCharSequence(mc).stream().map(mc.font::width).toList();
            var actual = event.getComponents().stream().map(line -> line.getWidth(mc.font)).toList();
            if (!actual.equals(expected)) throw new AssertionError("Weapon toggle hover rendered the wrong tooltip");
            weaponHelpRendered = true;
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post ignored) throws ReflectiveOperationException {
            if (!Boolean.getBoolean("sablelife.commandWandClient")) return;
            var mc = Minecraft.getInstance();
            if (++ticks > 1200) throw new AssertionError("Command Wand timed out in client phase " + phase
                    + ", screen=" + mc.screen + ", player=" + (mc.player == null ? null : mc.player.position())
                    + ", frames=" + rendered + ", core=" + (mc.level == null ? null : mc.level.getBlockState(CORE))
                    + ", item=" + (mc.player == null ? null : mc.player.getMainHandItem())
                    + ", Life aim=" + lifeAimStatus + ", mob aim=" + mobAimStatus);
            if (phase == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                phase++;
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                var address = "127.0.0.1:" + Integer.getInteger("sablelife.commandWandPort", 25596);
                ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(address),
                        new ServerData("Command Wand", address, ServerData.Type.OTHER), false, null);
            }
            var player = mc.player;
            if (phase == 8 && itemHelpRendered && !itemHelpCaptured && mc.screen instanceof InventoryScreen) {
                screenshot(mc, "command-wand-item-tooltip.png");
                itemHelpCaptured = true;
            }
            if (phase == 8 && player == null) {
                if (!itemHelpRendered) throw new AssertionError("Command Wand item tooltip was not shown");
                System.out.println("COMMAND_WAND_CLIENT_PASS " + mc.options.languageCode);
                mc.stop();
                return;
            }
            if (player == null || !player.getMainHandItem().is(SableLife.COMMAND_WAND.get())) return;
            if (phase == 1 && player.position().distanceToSqr(START) < .5
                    && mc.level.getBlockState(CORE).is(SableLife.HEART.get())
                    && mc.screen == null && rendered > 5) {
                mc.getToasts().clear();
                var manual = new ManualScreen(new TitleScreen(), "server_limits");
                mc.setScreen(manual);
                manualButton(manual, Component.translatable("manual.sablelife.guides").getString()).onPress();
                String wand = Component.translatable("manual.sablelife.chapter.command_wand").getString();
                String limits = Component.translatable("manual.sablelife.chapter.server_limits").getString();
                var labels = new java.util.ArrayList<String>();
                for (int page = 0; page < 16; page++) {
                    manual.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                            .map(button -> button.getMessage().getString()).filter(label -> label.equals(wand) || label.equals(limits))
                            .forEach(labels::add);
                    var next = manualButton(manual, "›");
                    if (!next.active || labels.contains(limits)) break;
                    next.onPress();
                }
                if (!labels.equals(java.util.List.of(wand, limits))) throw new AssertionError("Incorrect guide order: " + labels);
                try {
                    manualButton(manual, wand).onPress();
                } catch (java.util.NoSuchElementException previousPage) {
                    manualButton(manual, "‹").onPress();
                    manualButton(manual, wand).onPress();
                }
                since = rendered;
                phase = 10;
            } else if (phase == 10 && rendered > since + 3) {
                var manual = (ManualScreen) mc.screen;
                screenshot(mc, "command-wand-manual-usage.png");
                manualButton(manual, Component.translatable("manual.sablelife.example").getString()).onPress();
                since = rendered;
                phase = 11;
            } else if (phase == 11 && rendered > since + 3) {
                screenshot(mc, "command-wand-manual-example.png");
                var manual = (ManualScreen) mc.screen;
                manualButton(manual, Component.translatable("manual.sablelife.troubleshooting").getString()).onPress();
                since = rendered;
                phase = 12;
            } else if (phase == 12 && rendered > since + 3) {
                screenshot(mc, "command-wand-manual-troubleshooting.png");
                mc.setScreen(null);
                lookAt(mc, CORE.getCenter());
                shift(mc, true);
                var aimed = CommandWandItem.pickBlock(player, player.blockInteractionRange() + 1);
                if (!aimed.getBlockPos().equals(CORE)) throw new AssertionError("Binding ray missed test core: " + aimed.getBlockPos());
                if (!net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(player.connection, CommandWandItem.Input.TYPE.id())) {
                    throw new AssertionError("Server did not negotiate the command channel");
                }
                previousWandData = player.getMainHandItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                click(mc, "startUseItem");
                shift(mc, false);
                phase = 2;
            } else if (phase == 2 && !player.getMainHandItem()
                    .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).equals(previousWandData)) {
                lookAt(mc, player.getEyePosition().add(0, 20, 0));
                shift(mc, true);
                click(mc, "startUseItem");
                shift(mc, false);
                phase++;
            } else if (phase == 3 && mc.screen != null && mc.screen.getClass().getSimpleName().equals("CommandWandScreen")) {
                String title = mc.screen.getTitle().getString();
                String expected = Component.translatable("screen.sablelife.command_wand_settings").getString();
                if (!title.equals(expected) || title.equals("screen.sablelife.command_wand_settings")
                        || mc.font.width(title) > 198) throw new AssertionError("Incorrect settings title: " + title);
                var input = mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                var enabled = Component.translatable("dashboard.sablelife.enabled").getString();
                var disabled = Component.translatable("dashboard.sablelife.disabled").getString();
                var toggles = mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> button.getTooltip() != null).toList();
                var tooltipNames = java.util.List.of("alignment", "facing", "movement", "weapon");
                if (toggles.size() != tooltipNames.size()) throw new AssertionError("Command Wand did not show four toggles");
                for (int i = 0; i < toggles.size(); i++) {
                    var toggle = toggles.get(i);
                    boolean initiallyEnabled = i != 3 || (CommandWandItem.enabledModulesMask(player.getMainHandItem())
                            & CommandTarget.WEAPON_TARGETING) != 0;
                    if (!toggle.getMessage().getString().equals(initiallyEnabled ? enabled : disabled)) {
                        throw new AssertionError("Unexpected default toggle for " + tooltipNames.get(i));
                    }
                    if (toggle.getX() != input.getX() || toggle.getWidth() != input.getWidth()
                            || i > 0 && toggle.getY() <= toggles.get(i - 1).getY() + toggles.get(i - 1).getHeight()) {
                        throw new AssertionError("Module toggle did not align with the settings column");
                    }
                    var tooltip = toggle.getTooltip();
                    if (tooltip == null || !tooltipText(tooltip, mc).equals(tooltipText(moduleTooltip(tooltipNames.get(i)), mc))) {
                        throw new AssertionError("Missing or wrong help for " + tooltipNames.get(i));
                    }
                    mc.screen.mouseClicked(toggle.getX() + toggle.getWidth() / 2.0, toggle.getY() + toggle.getHeight() / 2.0, 0);
                    if (!toggle.getMessage().getString().equals(initiallyEnabled ? disabled : enabled)) {
                        throw new AssertionError("Toggle did not change for " + tooltipNames.get(i));
                    }
                    if (i != 1 && initiallyEnabled) {
                        mc.screen.mouseClicked(toggle.getX() + toggle.getWidth() / 2.0, toggle.getY() + toggle.getHeight() / 2.0, 0);
                        if (!toggle.getMessage().getString().equals(enabled)) throw new AssertionError("Module toggle did not turn on");
                    }
                }
                mc.screen.mouseClicked(input.getX() + 2, input.getY() + 2, 0);
                input.setValue("116");
                scrollDuration(mc, input, 1, "120");
                scrollDuration(mc, input, -1, "115");
                input.setValue("2");
                scrollDuration(mc, input, -1, "1");
                input.setValue("2");
                scrollDuration(mc, input, 1, "7");
                weaponToggle = toggles.getLast();
                since = rendered;
                phase = 15;
            } else if (phase == 15 && rendered > since + 3) {
                screenshot(mc, "command-wand-settings.png");
                pointAtWeaponToggle(mc);
                since = rendered;
                phase = 16;
            } else if (phase == 16 && weaponHelpRendered && rendered > since + 3) {
                screenshot(mc, "command-wand-weapon-help.png");
                System.out.println("COMMAND_WAND_WEAPON_HELP_PASS " + mc.options.languageCode);
                mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                if (mc.screen != null) throw new AssertionError("Enter did not save the settings");
                lookAt(mc, MOB);
                since = rendered;
                phase = 4;
            } else if (phase == 4 && rendered > since + 2 && CommandWandItem.duration(player.getMainHandItem()) == 7
                    && CommandWandItem.enabledModulesMask(player.getMainHandItem()) == TEST_MODULES) {
                if (mc.level.getEntitiesOfClass(Pig.class,
                        new net.minecraft.world.phys.AABB(MOB, MOB).inflate(2)).isEmpty()) return;
                var entity = CommandWandItem.pick(player).entity();
                mobAimStatus = "picked " + entity + ", outline rendered=" + distantPigRendered;
                if (entity == null || !mc.shouldEntityAppearGlowing(entity)) {
                    lookAt(mc, MOB);
                    return;
                }
                if (entity.distanceToSqr(player) < 124 * 124 || entity.isCurrentlyGlowing()) {
                    throw new AssertionError("Command hover did not outline the distant entity locally");
                }
                if (!distantPigRendered) return;
                System.out.println("COMMAND_WAND_MOB_OUTLINE_RENDER_PASS");
                screenshot(mc, "command-wand-entity.png");
                click(mc, "startAttack");
                lookAt(mc, COVERED.getCenter().add(0, .48, 0));
                if (!CommandWandItem.pick(player).block().getBlockPos().equals(COVERED)) {
                    throw new AssertionError("Cooldown test could not aim at the nearby destination");
                }
                click(mc, "startUseItem");
                since = ticks;
                phase++;
            } else if (phase == 5 && ticks - since >= 12) {
                since = rendered;
                phase = 13;
            } else if (phase == 13 && rendered > since + 4) {
                var container = SubLevelContainer.getContainer(mc.level);
                if (container == null) return;
                var body = container.getAllSubLevels().stream().filter(candidate -> !candidate.isRemoved()
                        && candidate.logicalPose().transformPosition(candidate.getPlot().getCenterBlock().getCenter())
                        .distanceToSqr(LIFE_CORE.getCenter()) < 16).findFirst().orElse(null);
                if (body == null) {
                    lifeAimStatus = "tracked body count=" + container.getAllSubLevels().size();
                    return;
                }
                var targetBlock = body.getPlot().getCenterBlock().east();
                var targetPoint = body.logicalPose().transformPosition(targetBlock.getCenter());
                lookAt(mc, targetPoint);
                var hit = CommandWandItem.pick(player);
                var pos = hit.block().getBlockPos();
                if (hit.block().getType() != HitResult.Type.BLOCK || hit.entity() != null || !pos.equals(targetBlock)) {
                    lifeAimStatus = "expected " + targetBlock + " at " + targetPoint + ", picked " + hit.block().getType()
                            + " at " + pos + ", entity=" + hit.entity();
                    return;
                }
                click(mc, "startAttack");
                since = ticks;
                phase = 14;
            } else if (phase == 14 && ticks - since >= 12) {
                screenshot(mc, "command-wand-life-target.png");
                lookAt(mc, COVERED.getCenter().add(0, .48, 0));
                since = rendered;
                phase = 9;
            } else if (phase == 9 && rendered > since + 4) {
                if (!CommandWandItem.pick(player).block().getBlockPos().equals(COVERED)) {
                    throw new AssertionError("The partially covered test block was not selected");
                }
                screenshot(mc, "command-wand-occlusion.png");
                lookAt(mc, GROUND.getCenter());
                since = rendered;
                phase = 6;
            } else if (phase == 6 && rendered > since + 2) {
                var hit = CommandWandItem.pick(player);
                if (!hit.block().getBlockPos().equals(GROUND) || hit.entity() != null) {
                    throw new AssertionError("Command hover did not pick loaded terrain near the wand's limit");
                }
                var pig = mc.level.getEntitiesOfClass(Pig.class,
                        new net.minecraft.world.phys.AABB(MOB, MOB).inflate(2)).getFirst();
                if (mc.shouldEntityAppearGlowing(pig)) throw new AssertionError("Wand outline lingered after looking away");
                screenshot(mc, "command-wand-terrain.png");
                mc.options.keyUse.setDown(true);
                click(mc, "startUseItem");
                lookAt(mc, MOB);
                if (CommandWandItem.pick(player).entity() != pig) {
                    throw new AssertionError("Cooldown test could not aim at the distant pig");
                }
                click(mc, "startAttack");
                lookAt(mc, GROUND.getCenter());
                since = ticks;
                phase++;
            } else if (phase == 7 && mc.gameMode.getPlayerMode() == GameType.SURVIVAL) {
                mc.options.keyUse.setDown(false);
                lookAt(mc, MOB);
                click(mc, "startAttack");
                var inventory = new InventoryScreen(player);
                mc.setScreen(inventory);
                var slot = inventory.getMenu().getSlot(36 + player.getInventory().selected);
                var window = mc.getWindow();
                double x = (inventory.getGuiLeft() + slot.x + 8.0) * window.getScreenWidth() / window.getGuiScaledWidth();
                double y = (inventory.getGuiTop() + slot.y + 8.0) * window.getScreenHeight() / window.getGuiScaledHeight();
                var move = MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
                move.setAccessible(true);
                move.invoke(mc.mouseHandler, window.getWindow(), x, y);
                phase++;
            } else if (phase == 7 && !destinationCaptured && ticks - since >= 12) {
                screenshot(mc, "command-wand-destination.png");
                destinationCaptured = true;
            }
        }

        private static Button manualButton(ManualScreen manual, String label) {
            return manual.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
        }

        private static void scrollDuration(Minecraft mc, EditBox input, double direction, String expected) {
            if (!mc.screen.mouseScrolled(input.getX() + 2, input.getY() + 2, 0, direction)
                    || !input.getValue().equals(expected)) {
                throw new AssertionError("Unexpected wheel step, got " + input.getValue() + " instead of " + expected);
            }
        }

        private static Tooltip moduleTooltip(String module) {
            String key = "screen.sablelife.command_wand_" + module + "_tip";
            var text = Component.translatable(key);
            if (text.getString().equals(key)) throw new AssertionError("Untranslated Command Wand tooltip: " + key);
            return Tooltip.create(text);
        }

        private static String tooltipText(Tooltip tooltip, Minecraft mc) {
            var text = new StringBuilder();
            for (var line : tooltip.toCharSequence(mc)) {
                line.accept((index, style, codePoint) -> {
                    text.appendCodePoint(codePoint);
                    return true;
                });
                text.append('\n');
            }
            return text.toString();
        }

        private static void pointAtWeaponToggle(Minecraft mc) throws ReflectiveOperationException {
            var window = mc.getWindow();
            double mouseX = (weaponToggle.getX() + weaponToggle.getWidth() / 2.0)
                    * window.getScreenWidth() / window.getGuiScaledWidth();
            double mouseY = (weaponToggle.getY() + weaponToggle.getHeight() / 2.0)
                    * window.getScreenHeight() / window.getGuiScaledHeight();
            // Send a normal mouse move to the hidden client window used by this test.
            var onMove = MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
            onMove.setAccessible(true);
            onMove.invoke(mc.mouseHandler, window.getWindow(), mouseX, mouseY);
        }

        private static void screenshot(Minecraft mc, String name) {
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), ignored -> {});
        }

        private static void click(Minecraft mc, String method) throws ReflectiveOperationException {
            var click = Minecraft.class.getDeclaredMethod(method);
            click.setAccessible(true);
            click.invoke(mc);
        }

        private static void lookAt(Minecraft mc, Vec3 point) {
            var delta = point.subtract(mc.player.getEyePosition());
            mc.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            mc.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        }

        private static void shift(Minecraft mc, boolean down) {
            mc.options.keyShift.setDown(down);
            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, down
                    ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
        }
    }
}
