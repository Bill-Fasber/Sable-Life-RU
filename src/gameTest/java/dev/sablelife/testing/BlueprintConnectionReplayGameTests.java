package dev.sablelife.testing;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.sablelife.compat.photomancy.SpawnTemplateBridge;
import dev.sablelife.heart.FactionRegistry;
import dev.sablelife.heart.HeartState;
import dev.sablelife.module.HeartModules;
import dev.sablelife.module.VirtualModule;
import dev.sablelife.ownership.BlueprintSpawnPlacement;
import dev.sablelife.ownership.LoadedCoreIndex;
import dev.sablelife.ownership.WildEncounters;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Local replay with a copy of core.nbt and its original addon dependencies. */
@GameTestHolder("sablelife_blueprint_connection_replay_tests")
@PrefixGameTestTemplate(false)
public final class BlueprintConnectionReplayGameTests {
    private static final Set<String> ARM_MODULES = Set.of(
            "right punch", "groundstrike", "enter 2nd sword effect", "2nd groundstirke effect",
            "sword melee", "3rd sword particle", "3rd sword particle 2");

    @GameTestGenerator
    public static List<TestFunction> localReplay() {
        if (!Boolean.getBoolean("sablelife.blueprintConnectionReplay")) return List.of();
        return List.of(new TestFunction("blueprint_connection_replay", "delayed_arm_connection_preserves_marked_modules",
                "sablelife_blueprint_connection_replay_tests:combat", 100, 0, true, helper -> {
            try {
                restoredArmModulesSurviveDelayedJoint(helper);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }));
    }

    private static void restoredArmModulesSurviveDelayedJoint(GameTestHelper helper) throws IOException {
        var level = helper.getLevel();
        var server = level.getServer();
        var template = SpawnTemplateBridge.readWild(server, "core");
        helper.assertTrue(template.parts() == 11 && template.canActivate(), "Expected the original articulated core blueprint");
        var bounds = SpawnTemplateBridge.geometry(template.template()).bounds(
                new BlueprintSpawnPlacement.Destination(level.dimension().location(), Vec3.ZERO, null));
        var forward = Math.max(24, Math.ceil(Math.hypot(bounds.getXsize(), bounds.getZsize()) / 2 + 8));
        var center = helper.absoluteVec(new Vec3(4, 90, 4));
        for (int x = ((int) center.x >> 4) - 3; x <= ((int) center.x >> 4) + 3; x++) {
            for (int z = ((int) center.z >> 4) - 3; z <= ((int) center.z >> 4) + 3; z++) level.getChunk(x, z);
        }
        var viewer = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "JointReplay"));
        viewer.setGameMode(GameType.CREATIVE);
        viewer.setPos(center.x, center.y, center.z - forward);
        viewer.setYRot(0);
        viewer.setXRot(0);
        var encounters = WildEncounters.get(server);
        encounters.loadBlueprint(level, "", "core", true);
        encounters.configure(level, "core", 16, 0, 10, false, false, FactionRegistry.WILDS,
                new WildEncounters.Conditions(false, true, false, Set.of(), 0), 1);
        var attempt = encounters.attemptPreviewOrSpawn(viewer, "core", false);
        helper.assertTrue(attempt.success(), "The natural-spawn Test Spawn path failed: " + attempt.reason());
        var container = SubLevelContainer.getContainer(level);
        var core = LoadedCoreIndex.all(level).stream()
                .filter(heart -> heart.modules().all().stream().anyMatch(module -> module.name().equals("right punch")))
                .findFirst().orElseThrow(() -> new GameTestAssertException("Placed core was unavailable"));
        helper.assertTrue(core.health().state() == HeartState.DORMANT,
                "Natural Test Spawn activated the core before its joints were restored");
        var root = Sable.HELPER.getContaining(level, core.getBlockPos());
        var punch = core.modules().all().stream().filter(module -> module.type().equals("melee")
                && module.name().equals("right punch") && !module.anchors().isEmpty())
                .findFirst().orElseThrow(() -> new GameTestAssertException("Marked right punch module was unavailable"));
        var arm = punch.anchors().getFirst().body(core);
        helper.assertTrue(root != null && arm != null, "The root or marked arm was lost");
        var modules = core.modules().all().stream().filter(module -> ARM_MODULES.contains(module.name())
                && module.anchors().stream().anyMatch(anchor -> arm.getUniqueId().equals(anchor.bodyId()))).toList();
        helper.assertTrue(container.getAllSubLevels().size() == template.parts() && modules.size() == ARM_MODULES.size()
                && modules.stream().allMatch(module -> module.anchors().stream()
                .allMatch(anchor -> arm.getUniqueId().equals(anchor.bodyId()))),
                "Photomancy failed to place all parts or remap their anchors");
        helper.assertTrue(!SubLevelHelper.getConnectedChain(root).contains(arm) && modules.stream().noneMatch(VirtualModule::detached),
                "Replay no longer covers an initially unconnected arm");

        helper.runAfterDelay(20, () -> {
            var connected = SubLevelHelper.getConnectedChain(root);
            helper.assertTrue(core.health().state() == HeartState.ALIVE, "Restored joints did not activate the core");
            helper.assertTrue(connected.size() == template.parts() && modules.stream()
                    .allMatch(module -> !module.detached() && HeartModules.status(module, core).equals("valid")),
                    "Restored joint left marked modules permanently detached");
            encounters.clearCurrent(level, "core");
            helper.succeed();
        });
    }
}
