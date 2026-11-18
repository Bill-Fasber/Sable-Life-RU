package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.client.HeartDashboardScreen;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.HeartDashboardMenu;
import dev.sablelife.module.HeartModules;
import dev.sablelife.module.ModuleNetworking;
import dev.sablelife.module.VirtualModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ModuleCapacityNetworkCheck {
    private static final BlockPos CORE = new BlockPos(8, 100, 8);
    private static final String ORIGINAL = "Capacity final module";
    private static final String EDITED = "Capacity last module edited";
    private static final String GROUP = "Capacity group";

    private static boolean verified(HeartBlockEntity heart) {
        var modules = heart.modules().all();
        return modules.size() == HeartModules.LIMIT && modules.getLast().name().equals(EDITED)
                && modules.stream().allMatch(module -> module.group().equals(GROUP));
    }

    @EventBusSubscriber(modid = "sablelife_tests")
    public static final class Server {
        private static final String MODE = System.getProperty("sablelife.capacityNetworkServer", "");
        private static boolean opened;
        private static int stopAt;

        @SubscribeEvent
        public static void tick(ServerTickEvent.Post event) {
            if (MODE.isEmpty()) return;
            var server = event.getServer();
            var level = server.overworld();
            if (MODE.equals("reload")) {
                if (server.getTickCount() == 100) {
                    if (!(level.getBlockEntity(CORE) instanceof HeartBlockEntity heart) || !verified(heart))
                        throw new IllegalStateException("1024 modules, group membership or edit missing after restart");
                    System.out.println("CAPACITY_RESTART_PASS");
                    server.halt(false);
                }
                return;
            }
            if (stopAt != 0) {
                if (server.getTickCount() >= stopAt) server.halt(false);
                return;
            }
            var players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) return;
            var player = players.getFirst();
            if (!opened) {
                level.setChunkForced(0, 0, true);
                level.setBlockAndUpdate(CORE.west().below(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(CORE, SableLife.HEART.get().defaultBlockState());
                var heart = (HeartBlockEntity) level.getBlockEntity(CORE);
                var list = new ListTag();
                for (int i = 1; i <= HeartModules.LIMIT; i++) {
                    var module = new VirtualModule("item_display");
                    module.edit("name", 0, i == HeartModules.LIMIT ? ORIGINAL : "Capacity module " + i);
                    list.add(module.save());
                }
                var data = new CompoundTag();
                data.put("Modules", list);
                heart.modules().load(data);
                heart.setChanged();
                player.setGameMode(GameType.CREATIVE);
                player.connection.teleport(CORE.getX() - 0.5, CORE.getY(), CORE.getZ() + 0.5, -90, 0);
                heart.openDashboard(player);
                opened = true;
            }
            if (level.getBlockEntity(CORE) instanceof HeartBlockEntity heart && verified(heart)
                    && player.containerMenu instanceof HeartDashboardMenu menu
                    && menu.snapshot().getUUID("Selected").equals(heart.modules().all().getLast().id())) {
                System.out.println("CAPACITY_NETWORK_SERVER_PASS");
                stopAt = server.getTickCount() + 40;
            }
        }
    }

    @EventBusSubscriber(modid = "sablelife_tests", value = Dist.CLIENT)
    public static final class Client {
        private static final int PORT = Integer.getInteger("sablelife.capacityNetworkPort", 0);
        private static int phase;

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            if (PORT == 0 || phase >= 6) return;
            var mc = Minecraft.getInstance();
            if (phase == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                phase = 1;
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                String address = "127.0.0.1:" + PORT;
                ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(address),
                        new ServerData("Module capacity test", address, ServerData.Type.OTHER), false, null);
                return;
            }
            if (!(mc.screen instanceof HeartDashboardScreen dashboard) || mc.player == null
                    || !(mc.player.containerMenu instanceof HeartDashboardMenu menu)) return;
            var snapshot = menu.snapshot();
            var modules = snapshot.getList("Modules", 10);
            if (modules.size() != HeartModules.LIMIT || !snapshot.getBoolean("Editable")) return;
            var last = modules.getCompound(modules.size() - 1);
            if (phase == 1) {
                var ids = modules.stream().map(value -> ((CompoundTag) value).getUUID("Id")).toList();
                PacketDistributor.sendToServer(ModuleNetworking.Action.groupBatch(menu.containerId, snapshot.getInt("Revision"), ids, GROUP));
                phase = 2;
            } else if (phase == 2 && last.getString("Group").equals(GROUP)) {
                var search = dashboard.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                        .filter(EditBox::isVisible).min(java.util.Comparator.comparingInt(EditBox::getX)).orElseThrow();
                search.setValue(ORIGINAL);
                phase = 3;
            } else if (phase == 3) {
                var button = dashboard.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(row -> row.visible && row.active && row.getMessage().getString().contains(ORIGINAL))
                        .findFirst().orElseThrow();
                button.onPress();
                phase = 4;
            } else if (phase == 4 && snapshot.hasUUID("Selected") && last.getUUID("Id").equals(snapshot.getUUID("Selected"))) {
                var name = dashboard.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                        .filter(box -> box.isVisible() && box.getValue().equals(ORIGINAL) && box.getX() > 100)
                        .max(java.util.Comparator.comparingInt(EditBox::getX)).orElse(null);
                if (name == null) return;
                dashboard.setFocused(name);
                name.setFocused(true);
                name.setValue(EDITED);
                dashboard.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                phase = 5;
            } else if (phase == 5 && last.getString("Name").equals(EDITED)) {
                phase = 6;
                System.out.println("CAPACITY_NETWORK_CLIENT_PASS");
                mc.stop();
            }
        }
    }
}
