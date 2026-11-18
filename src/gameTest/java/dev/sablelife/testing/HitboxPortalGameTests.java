package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.ModuleAnchor;
import dev.sablelife.ownership.LoadedCoreIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder("sablelife_hitbox_portal_tests")
@PrefixGameTestTemplate(false)
public final class HitboxPortalGameTests {
    @GameTest(template = "combat", batch = "hitbox_portal_nether", timeoutTicks = 100)
    public static void NetherPortalKeepsHybridReceiverBound(GameTestHelper helper) {
        checkPortal(helper, Level.NETHER, 1);
    }

    @GameTest(template = "combat", batch = "hitbox_portal_end", timeoutTicks = 200)
    public static void EndPortalKeepsEntityOnlyReceiverBound(GameTestHelper helper) {
        checkPortal(helper, Level.END, 0);
    }

    private static void checkPortal(GameTestHelper helper, ResourceKey<Level> destinationKey, int damageMode) {
        ServerLevel level = helper.getLevel();
        ServerLevel destination = level.getServer().getLevel(destinationKey);
        helper.assertTrue(destination != null, "Destination dimension is missing");
        var endArrival = new ChunkPos(ServerLevel.END_SPAWN_POINT);
        boolean forcedArrival = destinationKey == Level.END && destination.setChunkForced(endArrival.x, endArrival.z, true);
        var core = createCore(helper, damageMode);

        helper.runAfterDelay(15, () -> {
            var receiver = core.modules().damageBoxes.entities().getFirst();
            var originalId = receiver.getUUID();
            var portalPos = BlockPos.containing(receiver.getBoundingBox().getCenter());
            if (destinationKey == Level.NETHER) {
                createNetherPortal(helper, level, portalPos);
            } else {
                level.setBlockAndUpdate(portalPos, Blocks.END_PORTAL.defaultBlockState());
            }
            var shape = level.getBlockState(portalPos).getShape(level, portalPos);
            var portalBounds = shape.bounds().move(portalPos);

            var control = EntityType.PIG.create(level);
            control.setNoAi(true);
            control.setNoGravity(true);
            control.setPos(Vec3.atBottomCenterOf(portalPos).add(0, 0.1, 0));
            helper.assertTrue(level.addFreshEntity(control), "Failed to spawn the control pig");
            control.move(MoverType.SELF, Vec3.ZERO);
            helper.assertTrue(control.portalProcess != null, "Control pig is not registering portal contact");
            var controlId = control.getUUID();

            // Observe the original receiver throughout contact so a silent replacement cannot pass.
            helper.startSequence()
                .thenExecuteFor(35, () -> {
                    helper.assertTrue(level.getEntity(originalId) == receiver && !receiver.isRemoved(), "Original receiver disappeared");
                    helper.assertTrue(destination.getEntity(originalId) == null, "Receiver entered the destination dimension");
                    helper.assertTrue(receiver.getBoundingBox().intersects(portalBounds),
                            "Receiver left portal " + portalPos + " at " + receiver.position());
                    if (!control.isRemoved()) control.move(MoverType.SELF, Vec3.ZERO);
                })
                .thenWaitUntil(() -> helper.assertTrue(destination.getEntity(controlId) != null && level.getEntity(controlId) == null,
                        "Native portal did not transport the control pig. Removal reason: " + control.getRemovalReason()))
                .thenExecute(() -> {
                    destination.getEntity(controlId).discard();
                    if (forcedArrival) destination.setChunkForced(endArrival.x, endArrival.z, false);
                    var receivers = core.modules().damageBoxes.entities();
                    helper.assertTrue(receivers.size() == 1 && receivers.getFirst() == receiver && receiver.core() == core,
                            "Portal contact changed the core's receiver binding");
                    helper.assertTrue(core.health().current() == 1000, "Portal contact damaged the core");

                    helper.assertTrue(receiver.hurt(level.damageSources().magic(), 12), "Receiver rejected a direct hit after portal contact");
                    core.modules().damageBoxes.flush(core);
                    helper.assertTrue(core.health().current() == 988, "Receiver did not apply exactly one hit to the core");
                })
                .thenSucceed();
        });
    }

    private static HeartBlockEntity createCore(GameTestHelper helper, int damageMode) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 123, 4));
        level.setBlockAndUpdate(pos, SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos, pos.east()),
                new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY(), pos.getZ()));
        var core = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
        core.configure(1000, 100);
        var pin = core.modules().add("hover_anchor");
        pin.anchors(List.of(ModuleAnchor.create(core, core.getBlockPos(), Direction.DOWN)));
        pin.edit("hoverRotation", 1, "");
        var settings = core.modules().add("entity_hitbox");
        settings.anchors(List.of(ModuleAnchor.create(core, core.getBlockPos().east(), Direction.UP)));
        settings.edit("hitboxDamageMode", damageMode, "");
        settings.edit("hitboxOffsetX", 5, "");
        settings.edit("hitboxWidth", 2, "");
        settings.edit("hitboxHeight", 3, "");
        settings.edit("hitboxDepth", 2, "");
        core.tryActivate(TestActors.creative(level));
        LoadedCoreIndex.observe(core);
        core.modules().tick(core);
        return core;
    }

    private static void createNetherPortal(GameTestHelper helper, ServerLevel level, BlockPos entrance) {
        for (BlockPos edge : BlockPos.betweenClosed(entrance.offset(-1, -1, 0), entrance.offset(2, 3, 0))) {
            if (edge.getX() == entrance.getX() - 1 || edge.getX() == entrance.getX() + 2
                    || edge.getY() == entrance.getY() - 1 || edge.getY() == entrance.getY() + 3) {
                level.setBlockAndUpdate(edge, Blocks.OBSIDIAN.defaultBlockState());
            } else {
                level.setBlockAndUpdate(edge, Blocks.AIR.defaultBlockState());
            }
        }
        var portal = PortalShape.findEmptyPortalShape(level, entrance, Direction.Axis.X);
        helper.assertTrue(portal.isPresent(), "Failed to light the obsidian portal at " + entrance);
        portal.orElseThrow().createPortalBlocks();
    }
}
