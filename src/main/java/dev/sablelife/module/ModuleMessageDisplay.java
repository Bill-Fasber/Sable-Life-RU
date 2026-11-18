package dev.sablelife.module;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.heart.CreatureNames;
import dev.sablelife.heart.DeathSignals;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

/** Runs a message sequence to completion after a trigger, even if the signal was a pulse. */
public final class ModuleMessageDisplay {
    private record Message(int mode, Component text, Component subtitle, int duration, int delayAfter) {
        static Message from(MessageProgram.Row row, int previousInterval) {
            return new Message(row.mode(), CreatureNames.formatted(row.text()),
                    row.mode() == MessageProgram.TITLE ? CreatureNames.formatted(row.subtitle()) : Component.empty(),
                    row.duration(), row.delayAfterOr(previousInterval));
        }
    }

    private static final class Playback {
        boolean powered;
        List<Message> pending = List.of();
        int next;
        long due;
    }

    private record Audience(ServerLevel level, Vec3 center, double radiusSquared, boolean global, boolean survival, Component name) {
        static Audience from(ServerLevel level, HeartBlockEntity heart, VirtualModule module) {
            boolean global = !heart.isSurvivalCore() && !heart.isControlCore() && module.value("messageGlobal") != 0;
            var center = global ? Vec3.ZERO : SableBodyProbe.toWorld(level, heart.getBlockPos().getCenter());
            double radius = module.value("messageRadius");
            return new Audience(level, center, radius * radius, global, heart.isSurvivalCore(), heart.displayName());
        }

        boolean enabled() {
            return survival ? SableLifeServerConfig.survivalModuleEnabled("message_display")
                    : SableLifeServerConfig.moduleEnabled(false, "message_display");
        }
    }

    private record DeathPlayback(Audience audience, List<Message> messages, int next) {
        void sendNext(MinecraftServer server, long now) {
            if (!audience.enabled()) return;
            var message = messages.get(next);
            send(audience, message);
            if (next + 1 < messages.size()) {
                deathPlaybacks.computeIfAbsent(server, ignored -> new TreeMap<>())
                        .computeIfAbsent(now + message.delayAfter(), ignored -> new ArrayList<>())
                        .add(new DeathPlayback(audience, messages, next + 1));
            }
        }
    }

    private static final Map<MinecraftServer, NavigableMap<Long, List<DeathPlayback>>> deathPlaybacks = new HashMap<>();
    private final Map<UUID, Playback> playback = new HashMap<>();

    public static void onOwnDeath(HeartBlockEntity heart, ServerLevel level, List<DeathSignals.Pulse> pulses) {
        if (pulses.isEmpty()) return;
        var server = level.getServer();
        long now = server.overworld().getGameTime();
        for (var module : heart.modules().all()) {
            if (!module.type().equals("message_display") || module.value("trigger") != 2
                    || !HeartModules.status(module, heart).equals("valid")
                    || pulses.stream().noneMatch(pulse -> pulse.channel() == (int) module.value("channel"))) continue;
            var messages = messages(module);
            if (!messages.isEmpty()) new DeathPlayback(Audience.from(level, heart, module), messages, 0).sendNext(server, now);
        }
    }

    public static void tickDeathPlaybacks(ServerTickEvent.Post event) {
        var server = event.getServer();
        var pending = deathPlaybacks.get(server);
        if (pending == null) return;
        long now = server.overworld().getGameTime();
        while (!pending.isEmpty() && pending.firstKey() <= now) {
            for (var playback : pending.pollFirstEntry().getValue()) playback.sendNext(server, now);
        }
        if (pending.isEmpty()) deathPlaybacks.remove(server);
    }

    public static void stopped(ServerStoppedEvent event) {
        deathPlaybacks.remove(event.getServer());
    }

    void clear() { playback.clear(); }
    void reset(UUID id) { playback.remove(id); }

    void tick(HeartBlockEntity heart, List<VirtualModule> modules) {
        if (modules.isEmpty() || !(heart.getLevel() instanceof ServerLevel level)
                || !heart.wireless().master() || !heart.operational()) {
            clear();
            return;
        }
        long now = level.getGameTime();
        for (VirtualModule module : modules) {
            if (!HeartModules.status(module, heart).equals("valid")) {
                reset(module.id());
                continue;
            }
            var run = playback.computeIfAbsent(module.id(), id -> new Playback());
            boolean powered = HeartModules.controlPowered(module, heart);
            boolean start = powered && (ModuleHurt.once(module) ? heart.modules().hurtTriggers.start(module) : !run.powered);
            run.powered = powered;
            if (start) {
                if (ModuleHurt.once(module)) heart.modules().hurtTriggers.finish(module.id());
                if (run.next >= run.pending.size()) {
                    run.pending = messages(module);
                    run.next = 0;
                    run.due = now;
                }
            }
            if (run.next < run.pending.size() && now >= run.due) {
                var message = run.pending.get(run.next++);
                send(Audience.from(level, heart, module), message);
                if (run.next == run.pending.size()) {
                    run.pending = List.of();
                    run.next = 0;
                } else {
                    run.due = now + message.delayAfter();
                }
            }
        }
    }

    private static List<Message> messages(VirtualModule module) {
        int previousInterval = (int) module.value("messageInterval");
        return module.messages().rows().stream().map(row -> Message.from(row, previousInterval))
                .filter(message -> !message.text().getString().isBlank()).toList();
    }

    private static void send(Audience audience, Message message) {
        var level = audience.level();
        var players = audience.global() ? level.getServer().getPlayerList().getPlayers() : level.players();
        var displayText = message.mode() == MessageProgram.CHAT
                ? Component.literal("[").append(audience.name()).append("] ").append(message.text())
                : message.text();
        for (var player : players) {
            if (!audience.global() && player.distanceToSqr(audience.center()) > audience.radiusSquared()) continue;
            if (message.mode() == MessageProgram.TITLE) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(0, message.duration(), 0));
                player.connection.send(new ClientboundSetSubtitleTextPacket(message.subtitle()));
                player.connection.send(new ClientboundSetTitleTextPacket(displayText));
            } else {
                player.displayClientMessage(displayText, message.mode() == MessageProgram.ACTION_BAR);
            }
        }
    }
}
