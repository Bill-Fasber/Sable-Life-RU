package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import dev.sablelife.ownership.LoadedCoreIndex;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder("sablelife_sensor_facing_tests")
@PrefixGameTestTemplate(false)
public final class SensorFacingGameTests {
    @GameTest(template = "combat", batch = "sensor_facing_life")
    public static void OrdinaryCoreFollowsFarSensorTargetInsteadOfNearRearMob(GameTestHelper h) {
        sensorFollow(h, SableLife.HEART.get());
    }

    @GameTest(template = "combat", batch = "sensor_facing_control")
    public static void RedstoneCoreFollowsFarSensorTargetInsteadOfNearRearMob(GameTestHelper h) {
        sensorFollow(h, SableLife.REDSTONE_CORE.get());
    }

    @GameTest(template = "combat", batch = "sensor_facing_survival")
    public static void SurvivalCoreFollowsFarSensorTargetInsteadOfNearRearMob(GameTestHelper h) {
        sensorFollow(h, SableLife.SURVIVAL_LIFE_CORE.get());
    }

    private static void sensorFollow(GameTestHelper h, Block block) {
        var heart = fixture(h, block);
        var facing = heart.modules().add("auto_facing");
        facing.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos(), Direction.NORTH)));
        facing.edit("trigger", 0, "");
        facing.edit("facingSource", 1, "");
        facing.edit("facingRange", 1, "");
        var front = sensor(heart, Direction.NORTH);
        var rear = sensor(heart, Direction.SOUTH);
        facing.facingSensor(front.id());
        var author = TestActors.creative(h.getLevel());
        author.setPos(SableBodyProbe.toWorld(h.getLevel(), heart.getBlockPos().getCenter()));
        if (heart.isSurvivalCore()) heart.depositSurvivalTokens(author,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 10));
        heart.tryActivate(author);
        h.assertTrue(heart.operational(), "Core did not activate");
        var pivot = SableBodyProbe.toWorld(h.getLevel(), heart.getBlockPos().getCenter());
        var far = EntityType.COW.create(h.getLevel());
        far.setNoAi(true); far.setNoGravity(true); far.setPos(pivot.add(0, -far.getBbHeight() / 2, -20)); h.getLevel().addFreshEntity(far);
        var near = EntityType.COW.create(h.getLevel());
        near.setNoAi(true); near.setNoGravity(true); near.setPos(pivot.add(0, -near.getBbHeight() / 2, 3)); h.getLevel().addFreshEntity(near);
        try {
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == far,
                    "Auto facing ignored its sensor or applied its independent type/range");
            facing.facingSensor(rear.id());
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == near, "Wrong sensor was followed");
            facing.facingSensor(front.id());
            front.edit("sensorInvert", 1, ""); heart.modules().changed();
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == far, "Inversion discarded a detected target");
            front.edit("enabled", 0, "");
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == null, "Disabled sensor gave a target");
            front.edit("enabled", 1, ""); front.edit("trigger", 2, ""); heart.modules().changed();
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == null, "Untriggered sensor gave a target");
            front.edit("trigger", 0, ""); far.discard(); heart.modules().changed();
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == null, "Lost target fell back to the rear mob");
            heart.modules().remove(front.id());
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == null, "Deleted sensor redirected the binding");
            facing.facingSensor(UUID.randomUUID());
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == null, "Foreign sensor ID was accepted");
            h.succeed();
        } finally { far.discard(); near.discard(); }
    }

    @GameTest(template = "combat", batch = "sensor_facing_binding")
    public static void BindingUsesCodecAndSurvivesRenameReorderSaveCopyAndPreset(GameTestHelper h) {
        var heart = fixture(h, SableLife.SURVIVAL_LIFE_CORE.get());
        var player = TestActors.creative(h.getLevel());
        player.setPos(SableBodyProbe.toWorld(h.getLevel(), heart.getBlockPos().getCenter()));
        var facing = heart.modules().add("auto_facing");
        var sensor = sensor(heart, Direction.NORTH);
        facing.anchors(sensor.anchors());
        var unrelated = heart.modules().add("auto_facing");
        var menu = new HeartDashboardMenu(1, player.getInventory(), heart);
        var request = new ModuleNetworking.Action(1, heart.modules().revision(), facing.id(), "edit", "facingSensor", 2, "");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ModuleNetworking.Action.CODEC.encode(buffer, request);
            var decoded = ModuleNetworking.Action.CODEC.decode(buffer);
            h.assertTrue(decoded.equals(request) && menu.action(player, decoded.revision(), decoded.module(), decoded.operation(), decoded.key(),
                    decoded.value(), decoded.text()), "Encoded binding request failed");
        } finally { buffer.release(); }
        h.assertTrue(sensor.id().equals(facing.facingSensorId()), "Binding used a different sensor");
        h.assertTrue(!menu.action(player, request.revision(), facing.id(), "edit", "facingSensor", 0, ""), "Repeated stale request changed binding");
        for (double value : new double[]{-1, .5, 4, Double.NaN, Double.POSITIVE_INFINITY})
            h.assertTrue(!menu.action(player, heart.modules().revision(), facing.id(), "edit", "facingSensor", value, ""), "Invalid index accepted");
        h.assertTrue(!menu.action(player, heart.modules().revision(), facing.id(), "edit", "facingSensor", 3, ""), "Non-sensor was accepted");
        h.assertTrue(!menu.action(player, heart.modules().revision(), unrelated.id(), "edit", "facingSensor", 2, "unexpected"), "Unexpected text accepted");
        sensor.edit("name", 0, "Front lookout"); heart.modules().reorder(sensor.id(), -1);
        facing.edit("facingSource", 1, "");
        var saved = VirtualModule.load(facing.save());
        h.assertTrue(sensor.id().equals(saved.facingSensorId()) && saved.value("facingSource") == 1, "Binding changed on reload/reorder");
        var preview = InspectionSnapshot.capture(UUID.randomUUID(), heart, false).rows().stream()
                .filter(row -> row.id().equals(facing.id())).findFirst().orElseThrow();
        h.assertTrue(preview.pieces().stream().noneMatch(piece -> piece.shape() == InspectionSnapshot.Shape.RING),
                "Sensor mode preview retained the unused self-targeting range");
        h.assertTrue(sensor.id().equals(heart.modules().copy(facing.id()).facingSensorId()), "Local duplication lost sensor binding");
        var preset = ModulePreset.create(facing.save());
        h.assertTrue(preset.facingSensorId() == null && preset.value("facingSource") == 1, "Portable preset retained foreign sensor ID");
        var old = facing.save(); old.getCompound("Settings").remove("facingSource"); old.getCompound("Settings").remove("FacingSensorId");
        var legacy = VirtualModule.load(old);
        h.assertTrue(legacy.value("facingSource") == 0 && legacy.facingSensorId() == null, "Old modules changed targeting defaults");
        var configuration = heart.copyConfiguration();
        h.assertTrue(heart.pasteConfiguration(player, configuration), "Configuration paste failed");
        var copiedSensor = heart.modules().all().stream().filter(m -> m.type().equals("sensor")).findFirst().orElseThrow();
        h.assertTrue(!copiedSensor.id().equals(sensor.id()) && heart.modules().all().stream().filter(m -> m.type().equals("auto_facing")
                && m.value("facingSource") == 1).allMatch(m -> copiedSensor.id().equals(m.facingSensorId())), "Configuration copy did not remap sensor binding");
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var permitted = new HeartDashboardMenu(2, player.getInventory(), heart);
        var copiedFacing = heart.modules().all().stream().filter(m -> m.type().equals("auto_facing")).findFirst().orElseThrow();
        h.assertTrue(permitted.action(player, heart.modules().revision(), copiedFacing.id(), "edit", "facingSensor", 1, ""), "Survival player could not bind a Survival Core sensor");
        h.succeed();
    }

    private static VirtualModule sensor(HeartBlockEntity heart, Direction direction) {
        var sensor = heart.modules().add("sensor");
        sensor.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos(), direction)));
        sensor.edit("trigger", 0, ""); sensor.edit("target", 1, ""); sensor.edit("shape", 1, "");
        sensor.edit("radius", 64, ""); sensor.edit("frontWidth", 4, ""); sensor.edit("lineOfSight", 1, "");
        return sensor;
    }

    @GameTest(template = "combat", batch = "sensor_facing_blueprint")
    public static void BlueprintRemapsBoundSensorAndOrdinaryCoreRejectsSurvivalEdits(GameTestHelper h) {
        var heart = fixture(h, SableLife.HEART.get());
        var sensor = sensor(heart, Direction.NORTH);
        var facing = heart.modules().add("auto_facing");
        facing.anchors(sensor.anchors()); facing.edit("facingSource", 1, ""); facing.facingSensor(sensor.id());
        var player = TestActors.creative(h.getLevel());
        var center = SableBodyProbe.toWorld(h.getLevel(), heart.getBlockPos().getCenter());
        player.setPos(center);
        var menu = new HeartDashboardMenu(1, player.getInventory(), heart);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        h.assertTrue(!menu.action(player, heart.modules().revision(), facing.id(), "edit", "facingSensor", 0, ""),
                "Survival player edited an ordinary creative core");
        h.assertTrue(sensor.id().equals(facing.facingSensorId()), "Rejected request changed the binding");
        var template = dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.capture(heart, center);
        var build = dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.createSpawn(template,
                new net.minecraft.nbt.CompoundTag(), center.add(0, 10, 40), new org.joml.Quaterniond());
        h.assertTrue(build.step(h.getLevel()).equals("DONE"), "Blueprint placement failed");
        var copy = build.heart(h.getLevel());
        h.assertTrue(copy != null, "Placed blueprint core missing");
        var copiedSensor = copy.modules().find(build.module(sensor.id()));
        var copiedFacing = copy.modules().find(build.module(facing.id()));
        h.assertTrue(copiedSensor != null && copiedFacing != null && !copiedSensor.id().equals(sensor.id())
                && copiedSensor.id().equals(copiedFacing.facingSensorId()), "Blueprint retained the original core's sensor ID");
        h.assertTrue(copiedFacing.anchors().getFirst().body(copy) != null, "Blueprint lost facing mark");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "sensor_facing_sight")
    public static void SensorSightBlocksHiddenTargetsAndRestoresAfterWallRemoval(GameTestHelper h) {
        var heart = fixture(h, SableLife.HEART.get());
        var sensor = sensor(heart, Direction.NORTH);
        sensor.edit("lineOfSight", 0, "");
        var facing = heart.modules().add("auto_facing");
        facing.anchors(sensor.anchors()); facing.edit("facingSource", 1, ""); facing.facingSensor(sensor.id());
        heart.tryActivate(TestActors.creative(h.getLevel()));
        var pivot = SableBodyProbe.toWorld(h.getLevel(), heart.getBlockPos().getCenter());
        var cow = EntityType.COW.create(h.getLevel()); cow.setNoAi(true); cow.setNoGravity(true);
        cow.setPos(pivot.add(0, -cow.getBbHeight() / 2, -12)); h.getLevel().addFreshEntity(cow);
        var wall = BlockPos.containing(pivot).north(6);
        try {
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == cow, "Visible sensor target missing");
            for (var pos : BlockPos.betweenClosed(wall.offset(-2, -1, 0), wall.offset(2, 2, 0)))
                h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            heart.modules().changed();
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == null, "Sensor selected target through wall");
            for (var pos : BlockPos.betweenClosed(wall.offset(-2, -1, 0), wall.offset(2, 2, 0)))
                h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            heart.modules().changed();
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == cow, "Removing wall did not restore target");
            h.succeed();
        } finally { cow.discard(); }
    }

    private static HeartBlockEntity fixture(GameTestHelper h, Block block) {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(3, 40, 3));
        level.setBlockAndUpdate(origin, block.defaultBlockState());
        level.setBlockAndUpdate(origin.north(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, List.of(origin, origin.north()),
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ() - 1, origin.getX(), origin.getY(), origin.getZ()));
        var heart = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
        LoadedCoreIndex.observe(heart);
        return heart;
    }
}
