package dev.sablelife.testing;

import com.mojang.authlib.GameProfile;
import dev.sablelife.SableLife;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.compat.photomancy.PhotomancyRespawnBridge;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.HeartDashboardMenu;
import dev.sablelife.module.HeartModules;
import dev.sablelife.module.MessageProgram;
import dev.sablelife.module.ModuleNetworking;
import dev.sablelife.module.VirtualModule;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder("sablelife_message_display_tests")
@PrefixGameTestTemplate(false)
public final class MessageDisplayGameTests {
    private static final String TYPE = "message_display";

    @GameTest(template = "combat", batch = "message_display_survival")
    public static void survivalPlayersCanOnlySendLocalMessagesWhenEnabled(GameTestHelper h) throws ReflectiveOperationException {
        var setting = (ModConfigSpec.BooleanValue) SableLifeServerConfig.SPEC.getValues()
                .get(List.of("modules", "survivalHeartCore", TYPE));
        h.assertTrue(!setting.getDefault(), "Survival Message Display must default off");
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(4, 40, 4));
        level.setBlockAndUpdate(pos, SableLife.SURVIVAL_LIFE_CORE.get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos, pos.east()),
                new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY(), pos.getZ()));
        var core = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
        var player = new Editor(level, 0);
        player.setGameMode(GameType.SURVIVAL);
        var center = position(core);
        player.setPos(center);
        var menu = new HeartDashboardMenu(1, player.getInventory(), core);
        boolean previous = setting.get();
        try {
            var create = new ModuleNetworking.Action(1, core.modules().revision(), new UUID(0, 0), "add", TYPE, 0, "");
            setting.set(false);
            h.assertTrue(!apply(menu, player, create), "Disabled module was added by a survival player");
            setting.set(true);
            h.assertTrue(apply(menu, player, create), "Enabled module was rejected for a survival player");
            var module = core.modules().all().getFirst();
            h.assertTrue(editRow(menu, player, core, module, "text", 0, "Local title")
                    && editRow(menu, player, core, module, "mode", 0, "1"), "Survival player could not author a title");
            var broadcast = new ModuleNetworking.Action(1, core.modules().revision(), module.id(), "edit", "messageGlobal", 1, "");
            h.assertTrue(!apply(menu, player, broadcast) && module.value("messageGlobal") == 0, "Server accepted a global edit");
            // Imported settings may still carry an earlier global flag. Preserve it and enforce the audience at send time.
            module.edit("messageGlobal", 1, "");
            module.edit("messageRadius", 8, "");
            h.assertTrue(core.pasteConfiguration(player, core.copyConfiguration()), "Survival data copier rejected local messages");
            var restored = core.modules().all().getFirst();
            h.assertTrue(restored.value("messageGlobal") == 1 && restored.value("messageRadius") == 8, "Import lost saved settings");
            h.assertTrue(core.depositSurvivalTokens(player, new ItemStack(Items.COPPER_INGOT, 12)), "Could not fuel the core");
            core.tryActivate(player);
            h.assertTrue(core.operational(), "Survival core failed to activate");
            var near = new Receiver(level, center);
            var far = new Receiver(level, center.add(20, 0, 0));
            var remote = new Receiver(level.getServer().getLevel(Level.NETHER), center);
            var audience = new Audience(level, near, far, remote);
            try {
                setting.set(false);
                core.modules().tick(core);
                h.assertTrue(near.messages.isEmpty() && HeartModules.status(restored, core).equals("server_disabled"),
                        "Disabled saved module ran");
                setting.set(true);
                core.modules().tick(core);
                core.modules().tick(core);
                h.assertTrue(near.messages.equals(List.of("Stay:60", "Subtitle:", "Title:Local title"))
                        && far.messages.isEmpty() && remote.messages.isEmpty(), "Survival title escaped its local audience or repeated");
            } finally { audience.close(); }
            h.succeed();
        } finally { setting.set(previous); }
    }

    @GameTest(template = "combat", batch = "message_display_authoring")
    public static void editingValidatesPermissionRevisionAndPayload(GameTestHelper h) {
        var core = SlashGuardGameTests.core(h);
        var author = new Editor(h.getLevel(), 2);
        var ordinary = new Editor(h.getLevel(), 0);
        author.setPos(position(core));
        ordinary.setPos(position(core));
        var authorMenu = new HeartDashboardMenu(1, author.getInventory(), core);
        var ordinaryMenu = new HeartDashboardMenu(1, ordinary.getInventory(), core);
        var create = new ModuleNetworking.Action(1, core.modules().revision(), new UUID(0, 0), "add", TYPE, 0, "");
        h.assertTrue(!apply(ordinaryMenu, ordinary, create), "Non-operator created a Message Display module");
        h.assertTrue(apply(authorMenu, author, create), "Authorized operator could not create the module");
        var module = core.modules().all().getFirst();
        var row = module.messages().rows().getFirst();
        h.assertTrue(module.value("messageRadius") == 64 && module.value("messageGlobal") == 0
                        && module.value("messageInterval") == 20 && module.messages().rows().size() == 1
                        && row.mode() == 0 && row.text().isEmpty() && row.subtitle().isEmpty() && row.duration() == 60,
                "The new module did not default to one empty local Chat row with a one-second interval");

        String maximum = "警".repeat(MessageProgram.MAX_TEXT);
        var request = new ModuleNetworking.Action(1, core.modules().revision(), module.id(), "msg_row", "text", 0, maximum);
        h.assertTrue(!apply(ordinaryMenu, ordinary, request), "Non-operator changed a message");
        h.assertTrue(apply(authorMenu, author, request), "Valid maximum-length text was rejected");
        h.assertTrue(!apply(authorMenu, author, request), "A repeated request bypassed the dashboard revision");
        h.assertTrue(module.messages().rows().getFirst().text().equals(maximum), "The maximum-length text was truncated");
        h.assertTrue(!editRow(authorMenu, author, core, module, "mode", 0, "3")
                        && !editRow(authorMenu, author, core, module, "duration", 0, "1201")
                        && !editRow(authorMenu, author, core, module, "text", .5, "invalid row"),
                "The message editor accepted an invalid mode, duration or row index");

        var invalid = new ModuleNetworking.Action(1, core.modules().revision(), module.id(), "msg_row", "text", 0, maximum + "x");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            boolean failed = false;
            try {
                ModuleNetworking.Action.CODEC.encode(buffer, invalid);
            } catch (EncoderException expected) {
                failed = true;
            }
            h.assertTrue(failed, "The encoder accepted text above the message limit");
            buffer.clear();
            buffer.writeVarInt(invalid.container());
            buffer.writeVarInt(invalid.revision());
            buffer.writeUUID(invalid.module());
            buffer.writeUtf(invalid.operation(), 16);
            buffer.writeUtf(invalid.key(), 64);
            buffer.writeDouble(invalid.value());
            buffer.writeUtf(invalid.text(), MessageProgram.MAX_TEXT + 1);
            failed = false;
            try {
                ModuleNetworking.Action.CODEC.decode(buffer);
            } catch (DecoderException expected) {
                failed = true;
            }
            h.assertTrue(failed, "The decoder accepted text above the message limit");
        } finally {
            buffer.release();
        }

        h.assertTrue(editRow(authorMenu, author, core, module, "mode", 0, "1")
                        && editRow(authorMenu, author, core, module, "subtitle", 0, "&#FF5555Warning")
                        && editRow(authorMenu, author, core, module, "duration", 0, "110")
                        && editRow(authorMenu, author, core, module, "delay_after", 0, "1")
                        && editRow(authorMenu, author, core, module, "delay_after", 0, "1200")
                        && editRow(authorMenu, author, core, module, "add", 0, "")
                        && editRow(authorMenu, author, core, module, "mode", 1, "2"),
                "Could not author multiple modes through the dashboard");
        module.edit("messageInterval", 30, "");
        h.assertTrue(!editRow(authorMenu, author, core, module, "delay_after", 0, "1201")
                        && !editRow(authorMenu, author, core, module, "delay_after", 0, "-1"),
                "An out-of-range wait was accepted");
        module.edit("messageRadius", 123, "");
        module.edit("messageGlobal", 1, "");
        core.loadWithComponents(core.saveWithoutMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        var saved = core.modules().all().getFirst();
        h.assertTrue(saved.messages().rows().equals(module.messages().rows()) && saved.value("messageInterval") == 30
                        && saved.value("messageRadius") == 123 && saved.value("messageGlobal") == 1,
                "Core save/load lost rows or delivery settings");
        var oldMessageTag = saved.save();
        oldMessageTag.getCompound("Messages").getList("Rows", 10).getCompound(0).remove("DelayAfter");
        var legacyMessages = VirtualModule.load(oldMessageTag);
        var roundTrip = VirtualModule.load(legacyMessages.save());
        h.assertTrue(roundTrip.messages().rows().stream().allMatch(savedRow -> savedRow.delayAfterOr((int) roundTrip.value("messageInterval")) == 30),
                "A legacy module failed to keep its custom interval after reload and resave");
        var older = new VirtualModule("command");
        h.assertTrue(VirtualModule.load(older.save()).type().equals("command") && !older.save().contains("Messages"),
                "An older module gained Message Display data");
        for (int index = 2; index < MessageProgram.MAX_ROWS; index++) {
            h.assertTrue(editRow(authorMenu, author, core, saved, "add", 0, ""), "Could not reach the row limit");
        }
        h.assertTrue(!editRow(authorMenu, author, core, saved, "add", 0, ""), "Accepted a row above the row limit");
        h.assertTrue(editRow(authorMenu, author, core, saved, "remove", 1, "")
                        && saved.messages().rows().size() == MessageProgram.MAX_ROWS - 1,
                "Could not remove a message row");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "message_display_local_delivery", timeoutTicks = 35)
    public static void shortWirelessPulsesSendOrderedMessagesWithinTheCurrentRadius(GameTestHelper h) throws ReflectiveOperationException {
        var core = SlashGuardGameTests.core(h);
        var module = core.modules().add(TYPE);
        module.edit("messageInterval", 6, "");
        module.edit("trigger", 2, "");
        module.edit("channel", 1, "");
        var messages = module.messages();
        messages.edit("text", 0, "&c&lCore damaged&r!");
        messages.edit("add", 0, ""); // Blank row. It should not add an extra interval.
        messages.edit("add", 0, "");
        messages.edit("mode", 2, "1");
        messages.edit("text", 2, "Core overheating");
        messages.edit("subtitle", 2, "&oBack away");
        messages.edit("duration", 2, "40");
        messages.edit("delay_after", 2, "12");
        messages.edit("add", 0, "");
        messages.edit("mode", 3, "2");
        messages.edit("text", 3, "Cooling");
        core.wireless().add();
        core.wireless().channel(0).set(0, 1);
        core.wireless().channel(0).set(6, 3);
        var level = h.getLevel();
        var near = new Receiver(level, position(core).add(62, 0, 0));
        var far = new Receiver(level, position(core).add(66, 0, 0));
        var remoteLevel = level.getServer().getLevel(Level.NETHER);
        h.assertTrue(remoteLevel != null, "Missing second dimension");
        var remote = new Receiver(remoteLevel, position(core));
        var audience = new Audience(level, near, far, remote);
        var author = TestActors.creative(level);
        h.assertTrue(core.editCore(author, "name", 0, "&b&oMechanical Scorpion"), "Could not set a formatted creature name");
        audience.execute(() -> {
            core.tryActivate(author);
            h.assertTrue(!HeartModules.triggered(module, core), "Wireless trigger began already powered");
            core.modules().tick(core);
            h.assertTrue(near.messages.isEmpty(), "An unpowered module sent a message");
            core.wireless().programOutputs("test", Map.of(0, 15));
            core.modules().tick(core);
            h.assertTrue(near.messages.equals(List.of("Chat:[Mechanical Scorpion] Core damaged!"))
                            && far.messages.isEmpty() && remote.messages.isEmpty(),
                    "Chat used the wrong radius, dimension, prefix or initial timing");
            var nameStyle = styleOf(near.chatMessage, "Mechanical Scorpion");
            var bodyStyle = styleOf(near.chatMessage, "Core damaged");
            var bracketStyle = styleOf(near.chatMessage, "] ");
            h.assertTrue(nameStyle.isItalic() && nameStyle.getColor() != null && nameStyle.getColor().getValue() == 0x55FFFF,
                    "The Chat prefix lost the creature name formatting");
            h.assertTrue(bodyStyle.isBold() && !bodyStyle.isItalic()
                            && bodyStyle.getColor() != null && bodyStyle.getColor().getValue() == 0xFF5555,
                    "Chat lost the configured color or bold formatting");
            h.assertTrue(bracketStyle.getColor() == null && !bracketStyle.isItalic(), "Creature name formatting leaked into the brackets");
            core.wireless().programOutputs("test", Map.of());
            core.modules().tick(core);
            near.setPos(position(core).add(66, 0, 0));
            far.setPos(position(core).add(62, 0, 0));
        });
        h.runAfterDelay(2, () -> audience.execute(() -> {
            core.wireless().programOutputs("test", Map.of(0, 15));
            core.modules().tick(core);
            core.wireless().programOutputs("test", Map.of());
            core.modules().tick(core);
            h.assertTrue(far.messages.isEmpty(), "A second trigger interrupted the running sequence");
        }));
        h.runAfterDelay(6, () -> audience.execute(() -> {
            core.modules().tick(core);
            h.assertTrue(far.messages.equals(List.of("Stay:40", "Subtitle:Back away", "Title:Core overheating")),
                    "The Title row did not arrive after one interval with its duration and subtitle: " + far.messages);
        }));
        h.runAfterDelay(13, () -> audience.execute(() -> {
            core.modules().tick(core);
            h.assertTrue(far.messages.size() == 3, "Action Bar ignored the Title row's longer wait");
        }));
        h.runAfterDelay(19, () -> audience.execute(() -> {
            core.modules().tick(core);
            h.assertTrue(far.messages.equals(List.of("Stay:40", "Subtitle:Back away", "Title:Core overheating", "Action:Cooling"))
                            && near.messages.size() == 1 && remote.messages.isEmpty(),
                    "The sent rows lost order, skipped the action bar, or reused the first recipient list: " + far.messages);
            core.resetForCreative(author);
            audience.close();
            h.succeed();
        }));
    }

    @GameTest(template = "combat", batch = "message_display_global_delivery", timeoutTicks = 35)
    public static void globalDeliveryReachesOtherDimensionsAndReleaseCancelsPendingRows(GameTestHelper h) throws ReflectiveOperationException {
        var core = SlashGuardGameTests.core(h);
        var module = core.modules().add(TYPE);
        module.edit("messageGlobal", 1, "");
        module.edit("messageInterval", 6, "");
        module.messages().edit("text", 0, "Online");
        module.messages().edit("add", 0, "");
        module.messages().edit("text", 1, "Unexpected after release");
        var level = h.getLevel();
        var local = new Receiver(level, position(core).add(80, 0, 0));
        var remoteLevel = level.getServer().getLevel(Level.NETHER);
        h.assertTrue(remoteLevel != null, "Missing second dimension");
        var remote = new Receiver(remoteLevel, position(core));
        var audience = new Audience(level, local, remote);
        var author = TestActors.creative(level);
        var expected = List.of("Chat:[" + core.displayName().getString() + "] Online");
        audience.execute(() -> {
            core.tryActivate(author);
            core.modules().tick(core);
            core.modules().tick(core);
            h.assertTrue(local.messages.equals(expected) && remote.messages.equals(expected),
                    "A global message missed a receiver or resent while its trigger stayed on");
            h.assertTrue(local.chatMessage.getSiblings().stream().anyMatch(sibling ->
                            sibling.getContents() instanceof TranslatableContents translation
                                    && translation.getKey().equals("entity.sablelife.creature")),
                    "The unnamed core Chat prefix did not preserve the localized fallback");
            h.assertTrue(core.resetForCreative(author), "Could not release the core during a sequence");
        });
        h.runAfterDelay(10, () -> audience.execute(() -> {
            core.modules().tick(core);
            h.assertTrue(local.messages.equals(expected) && remote.messages.equals(expected)
                            && core.modules().all().getFirst().messages().rows().size() == 2,
                    "Release sent a queued message or discarded authored rows");
            audience.close();
            h.succeed();
        }));
    }

    private static Style styleOf(Component message, String segment) {
        return message.<Style>visit((style, text) -> text.equals(segment) ? Optional.of(style) : Optional.empty(), Style.EMPTY)
                .orElseThrow(() -> new AssertionError("Expected Chat text segment: " + segment));
    }

    @GameTest(template = "combat", batch = "message_display_own_death")
    public static void ownDeathSequenceOutlivesCoreAndUsesDeathLocation(GameTestHelper h) throws ReflectiveOperationException {
        var core = SlashGuardGameTests.core(h);
        core.configure(100, 2);
        int channel = core.wireless().add();
        var transmitter = core.wireless().channel(channel);
        transmitter.set(0, 1);
        transmitter.set(6, 4);
        transmitter.frequency(false, new ItemStack(Items.COPPER_INGOT));
        transmitter.frequency(true, new ItemStack(Items.PAPER));
        var event = core.modules().add("combat_event");
        for (var row : event.signalRules().rows()) event.signalRules().remove(row.id());
        for (int i = 0; i < 2; i++) {
            var rule = event.signalRules().add();
            rule.edit("eventKind", 3);
            rule.edit("eventChannel", channel + 1);
            rule.edit("eventPulse", 1);
        }

        var module = core.modules().add(TYPE);
        module.edit("trigger", 2, "");
        module.edit("channel", channel + 1, "");
        module.edit("messageRadius", 8, "");
        var rows = module.messages();
        rows.edit("text", 0, "Core offline");
        rows.edit("delay_after", 0, "20");
        rows.edit("add", 0, "");
        rows.edit("mode", 1, "2");
        rows.edit("text", 1, "Area clear");

        var mismatch = core.modules().add(TYPE);
        mismatch.edit("trigger", 2, "");
        mismatch.edit("channel", channel + 2, "");
        mismatch.messages().edit("text", 0, "Wrong channel");

        var level = h.getLevel();
        var author = TestActors.creative(level);
        core.tryActivate(author);
        var center = position(core);
        var near = new Receiver(level, center);
        var approaching = new Receiver(level, center.add(16, 0, 0));
        var audience = new Audience(level, near, approaching);
        String first = "Chat:[" + core.displayName().getString() + "] Core offline";
        audience.execute(() -> {
            h.assertTrue(core.operational(), "Core failed to activate");
            h.assertTrue(core.forceDeath() && !core.forceDeath(), "Death should transition once");
            h.assertTrue(near.messages.equals(List.of(first)) && approaching.messages.isEmpty(),
                    "Death announcement was duplicated, missed, or sent to a distant player");
        });
        h.runAfterDelay(12, () -> audience.execute(() -> {
            h.assertTrue(!dev.sablelife.ownership.BodyOwnership.get(level).hasOwned(core.creatureId()),
                    "Core structure was not cleaned up before the second message");
            h.assertTrue(near.messages.equals(List.of(first)), "The second row ignored its configured delay");
            approaching.setPos(center);
        }));
        h.runAfterDelay(24, () -> audience.execute(() -> {
            h.assertTrue(near.messages.equals(List.of(first, "Action:Area clear"))
                            && approaching.messages.equals(List.of("Action:Area clear")),
                    "Pending row did not use the death location and current recipients");
            audience.close();
            h.succeed();
        }));
    }

    @GameTest(template = "combat", batch = "message_display_blueprint_approval")
    public static void blueprintKeepsRowsButRequiresApprovalAfterPlacement(GameTestHelper h) {
        var core = SlashGuardGameTests.core(h);
        var authored = core.modules().add(TYPE);
        authored.messages().edit("text", 0, "&6Blueprint ready");
        authored.edit("messageGlobal", 1, "");
        var blueprint = PhotomancyRespawnBridge.capture(core, position(core));
        var placement = PhotomancyRespawnBridge.createSpawn(blueprint, new CompoundTag(),
                h.absoluteVec(new Vec3(3, 40, 9)), new Quaterniond());
        var level = h.getLevel();
        h.assertTrue(placement.step(level).equals("DONE"), "Photomancy did not complete placement");
        var copy = placement.heart(level);
        h.assertTrue(copy != null && copy != core, "Photomancy did not place a new core");
        var restored = copy.modules().all().getFirst();
        h.assertTrue(restored.messages().rows().equals(authored.messages().rows()) && restored.value("messageGlobal") == 1,
                "Photomancy discarded authored message rows or delivery scope");
        h.assertTrue(restored.commandApprovalRequired() && HeartModules.status(restored, copy).equals("command_locked"),
                "The newly placed blueprint ran a message without approval");
        placement.restoreApprovals(copy);
        h.assertTrue(!restored.commandApprovalRequired(), "Trusted respawn did not restore prior approval");
        h.succeed();
    }

    private static Vec3 position(HeartBlockEntity core) {
        return SableBodyProbe.toWorld(core.getLevel(), core.getBlockPos().getCenter());
    }

    private static boolean editRow(HeartDashboardMenu menu, ServerPlayer player, HeartBlockEntity core,
                                   VirtualModule module, String key, double index, String text) {
        return apply(menu, player, new ModuleNetworking.Action(menu.containerId, core.modules().revision(), module.id(),
                "msg_row", key, index, text));
    }

    private static boolean apply(HeartDashboardMenu menu, ServerPlayer player, ModuleNetworking.Action request) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.level().registryAccess());
        try {
            ModuleNetworking.Action.CODEC.encode(buffer, request);
            var decoded = ModuleNetworking.Action.CODEC.decode(buffer);
            if (!request.equals(decoded) || buffer.isReadable()) throw new IllegalStateException("Message action changed on the wire");
            return menu.action(player, decoded.revision(), decoded.module(), decoded.operation(), decoded.key(), decoded.value(), decoded.text());
        } finally {
            buffer.release();
        }
    }

    private static final class Editor extends ServerPlayer {
        private final int permission;

        private Editor(ServerLevel level, int permission) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "MessageEditor"), ClientInformation.createDefault());
            this.permission = permission;
            connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), this,
                    CommonListenerCookie.createInitial(getGameProfile(), false)) {
                @Override public void send(Packet<?> packet, PacketSendListener callback) {}
            };
            setGameMode(GameType.CREATIVE);
        }

        @Override protected int getPermissionLevel() { return permission; }
    }

    private static final class Receiver extends ServerPlayer {
        private final List<String> messages = new ArrayList<>();
        private final EmbeddedChannel channel;
        private Component chatMessage;

        private Receiver(ServerLevel level, Vec3 position) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "MessageReceiver"), ClientInformation.createDefault());
            setPos(position);
            var transport = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(transport);
            connection = new ServerGamePacketListenerImpl(level.getServer(), transport, this,
                    CommonListenerCookie.createInitial(getGameProfile(), false)) {
                @Override public void send(Packet<?> packet, PacketSendListener callback) {
                    switch (packet) {
                        case ClientboundSystemChatPacket chat -> {
                            messages.add((chat.overlay() ? "Action:" : "Chat:") + chat.content().getString());
                            if (!chat.overlay()) chatMessage = chat.content();
                        }
                        case ClientboundSetTitlesAnimationPacket animation -> messages.add("Stay:" + animation.getStay());
                        case ClientboundSetSubtitleTextPacket subtitle -> messages.add("Subtitle:" + subtitle.text().getString());
                        case ClientboundSetTitleTextPacket title -> messages.add("Title:" + title.text().getString());
                        case ClientboundSetActionBarTextPacket action -> messages.add("Action:" + action.text().getString());
                        default -> {}
                    }
                }
            };
        }
    }

    private static final class Audience {
        private final List<ServerPlayer> online;
        private final List<Receiver> receivers;

        @SuppressWarnings("unchecked")
        private Audience(ServerLevel level, Receiver... receivers) throws ReflectiveOperationException {
            var players = PlayerList.class.getDeclaredField("players");
            players.setAccessible(true);
            online = (List<ServerPlayer>) players.get(level.getServer().getPlayerList());
            this.receivers = List.of(receivers);
            for (var receiver : receivers) {
                online.add(receiver);
                receiver.serverLevel().players().add(receiver);
            }
        }

        private void execute(Runnable action) {
            try {
                action.run();
            } catch (RuntimeException | Error exception) {
                close();
                throw exception;
            }
        }

        private void close() {
            for (var receiver : receivers) {
                online.remove(receiver);
                receiver.serverLevel().players().remove(receiver);
                receiver.channel.finishAndReleaseAll();
            }
        }
    }
}
