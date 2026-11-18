package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.sablelife.SableLife;
import dev.sablelife.attack.ModuleArrow;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.VirtualModule;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;

@GameTestHolder("sablelife_arrow_coordinate_tests")
@PrefixGameTestTemplate(false)
public final class ArrowCoordinatesGameTests {
    private record Target(ServerSubLevel body, HeartBlockEntity core, Vec3 center, Vec3 front) {}

    private static Target target(GameTestHelper h, double yaw) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(5, 40, 5));
        level.setBlockAndUpdate(pos, SableLife.HEART.get().defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos),
                new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ()));
        var pose = SubLevelPhysicsSystem.get(level).getPipeline();
        pose.resetVelocity(body);
        pose.teleport(body, new Vector3d(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5),
                new Quaterniond().rotateY(yaw));
        var core = (HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
        core.configure(100, 60);
        var center = SableBodyProbe.toWorld(level, core.getBlockPos().getCenter());
        var front = body.logicalPose().transformNormal(new Vec3(0, 0, -1));
        return new Target(body, core, center, front);
    }

    private static Cow cow(GameTestHelper h, Vec3 center) {
        var cow = EntityType.COW.create(h.getLevel());
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        cow.setHealth(100);
        cow.setPos(center.add(0, -cow.getBbHeight() / 2., 0));
        h.getLevel().addFreshEntity(cow);
        return cow;
    }

    private static ModuleArrow arrow(GameTestHelper h, Vec3 start, float diameter) {
        var module = new VirtualModule("arrow");
        module.edit("arrowHitbox", diameter, "");
        module.edit("arrowResidue", 20, "");
        var arrow = new ModuleArrow(SableLife.MODULE_ARROW.get(), h.getLevel());
        arrow.configure(module);
        arrow.setNoGravity(true);
        arrow.setPos(start);
        arrow.setBaseDamage(2);
        return arrow;
    }

    private static EntityHitResult query(ModuleArrow arrow, Vec3 start, Vec3 end) throws Exception {
        var method = ModuleArrow.class.getDeclaredMethod("findHitEntity", Vec3.class, Vec3.class);
        method.setAccessible(true);
        return (EntityHitResult)method.invoke(arrow, start, end);
    }

    @GameTest(template="combat", batch="arrow_coordinate_query")
    public static void MixedBlockEndpointKeepsNearerWorldEntity(GameTestHelper h) throws Exception {
        var target = target(h, .7);
        var start = target.center().add(target.front().scale(4));
        var cow = cow(h, target.center().add(target.front().scale(2)));
        var arrow = arrow(h, start, .25F);
        var hit = query(arrow, start, target.core().getBlockPos().getCenter());
        h.assertTrue(hit != null && hit.getEntity() == cow, "Plot-space block endpoint skipped the nearer world entity");
        cow.discard();
        h.succeed();
    }

    @GameTest(template="combat", batch="arrow_coordinate_query")
    public static void PlotStartAndEndUseTheSameWorldQuery(GameTestHelper h) throws Exception {
        var target = target(h, -.8);
        var plotEnd = target.core().getBlockPos().getCenter();
        var plotStart = plotEnd.add(0, 0, -4);
        var start = SableBodyProbe.toWorld(h.getLevel(), plotStart);
        var cow = cow(h, target.center().add(target.front().scale(2)));
        var hit = query(arrow(h, start, .25F), plotStart, plotEnd);
        h.assertTrue(hit != null && hit.getEntity() == cow, "Plot-space start/end missed world entity");
        cow.discard();
        h.succeed();
    }

    @GameTest(template="combat", batch="arrow_coordinate_query")
    public static void PlotStartAndWorldEndUseTheSameWorldQuery(GameTestHelper h) throws Exception {
        var target = target(h, 1.1);
        var plotStart = target.core().getBlockPos().getCenter().add(0, 0, -4);
        var start = SableBodyProbe.toWorld(h.getLevel(), plotStart);
        var cow = cow(h, target.center().add(target.front().scale(2)));
        var hit = query(arrow(h, start, .25F), plotStart, target.center());
        h.assertTrue(hit != null && hit.getEntity() == cow, "Mixed plot start/world end missed entity");
        cow.discard();
        h.succeed();
    }

    @GameTest(template="combat", batch="arrow_coordinate_query")
    public static void ConvertedBlockEndpointDoesNotReachBehindTheBlock(GameTestHelper h) throws Exception {
        var target = target(h, .6);
        var start = target.center().add(target.front().scale(4));
        var cow = cow(h, target.center().subtract(target.front().scale(3)));
        h.assertTrue(query(arrow(h, start, .25F), start, target.core().getBlockPos().getCenter()) == null,
                "Query extended through the clipped block endpoint");
        cow.discard();
        h.succeed();
    }

    @GameTest(template="combat", batch="arrow_coordinate_query")
    public static void WorldQueryPreservesNearestTargetAndWideRadius(GameTestHelper h) throws Exception {
        var center = h.absolutePos(new BlockPos(4, 40, 4)).getCenter();
        var near = cow(h, center.add(0, 0, 2));
        var far = cow(h, center.add(0, 0, 4));
        var arrow = arrow(h, center, .25F);
        var hit = query(arrow, center, center.add(0, 0, 6));
        h.assertTrue(hit != null && hit.getEntity() == near, "Normal world query lost nearest target");
        near.discard(); far.discard();
        var side = cow(h, center.add(1.1, 0, 2));
        h.assertTrue(query(arrow, center, center.add(0, 0, 4)) == null, "Narrow arrow unexpectedly hit off-axis cow");
        var wideHit = query(arrow(h, center, 2F), center, center.add(0, 0, 4));
        h.assertTrue(wideHit != null && wideHit.getEntity() == side, "Coordinate fix lost wide projectile radius");
        side.discard(); h.succeed();
    }

    private static void liveEntityBeforeBody(GameTestHelper h, double yaw) {
        var target = target(h, yaw);
        var core = target.core();
        core.tryActivate(TestActors.creative(h.getLevel()));
        h.assertTrue(core.operational(), "Fixture core did not activate");
        h.runAfterDelay(3, () -> {
            var center = SableBodyProbe.toWorld(h.getLevel(), core.getBlockPos().getCenter());
            var front = target.body().logicalPose().transformNormal(new Vec3(0, 0, -1));
            var start = center.add(front.scale(4));
            var cow = cow(h, center.add(front.scale(2)));
            var arrow = arrow(h, start, .05F);
            arrow.setDeltaMovement(front.scale(-5));
            var clip = h.getLevel().clip(new net.minecraft.world.level.ClipContext(start, start.add(arrow.getDeltaMovement()),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, arrow));
            h.assertTrue(clip.getType() == HitResult.Type.BLOCK && clip.getBlockPos().equals(core.getBlockPos()), "Fixture did not clip the Sable block");
            h.getLevel().addFreshEntity(arrow);
            arrow.tick();
            h.assertTrue(cow.getHealth() < 100, "Actual vanilla arrow tick skipped entity in front of Sable block");
            h.assertTrue(core.health().current() == 100, "Arrow damaged the structure through the nearer entity");
            arrow.discard(); cow.discard(); core.resetForCreative(TestActors.creative(h.getLevel())); h.succeed();
        });
    }

    @GameTest(template="combat", batch="arrow_coordinate_live")
    public static void LiveArrowHitsEntityBeforeStaticStructure(GameTestHelper h) { liveEntityBeforeBody(h, 0); }

    @GameTest(template="combat", batch="arrow_coordinate_live")
    public static void LiveArrowHitsEntityBeforeRotatedStructure(GameTestHelper h) { liveEntityBeforeBody(h, 1.15); }

    @GameTest(template="combat", batch="arrow_coordinate_live")
    public static void LiveArrowStillDamagesSableBlockWithoutAnEarlierEntity(GameTestHelper h) {
        var target = target(h, .9);
        target.core().tryActivate(TestActors.creative(h.getLevel()));
        h.assertTrue(target.core().operational(), "Fixture core did not activate");
        h.runAfterDelay(3, () -> {
            var center = SableBodyProbe.toWorld(h.getLevel(), target.core().getBlockPos().getCenter());
            var front = target.body().logicalPose().transformNormal(new Vec3(0, 0, -1));
            var start = center.add(front.scale(4));
            var arrow = arrow(h, start, .05F);
            arrow.setDeltaMovement(front.scale(-5));
            var clip = h.getLevel().clip(new net.minecraft.world.level.ClipContext(start, start.add(arrow.getDeltaMovement()),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, arrow));
            h.assertTrue(clip.getType() == HitResult.Type.BLOCK && clip.getBlockPos().equals(target.core().getBlockPos()), "Fixture did not clip the Sable block");
            h.getLevel().addFreshEntity(arrow); arrow.tick();
            h.assertTrue(target.core().health().current() < 100, "Normal arrow-to-Sable block hit stopped damaging: state="
                    + target.core().health().state() + " hitCore=" + (dev.sablelife.combat.BodyCombat.findDamageHeart(h.getLevel(), clip.getBlockPos()) == target.core())
                    + " arrow=" + arrow.position() + " nativeGround=" + arrow.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getBoolean("inGround")
                    + " marked=" + arrow.getPersistentData());
            arrow.discard(); target.core().resetForCreative(TestActors.creative(h.getLevel())); h.succeed();
        });
    }

    @GameTest(template="combat", batch="arrow_coordinate_terrain")
    public static void WideArrowStillStopsOnGrazingTerrainWhileNarrowArrowPasses(GameTestHelper h) {
        var block = h.absolutePos(new BlockPos(8, 40, 8));
        h.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
        var start = new Vec3(block.getX() - 3, block.getY() + .5, block.getZ() + 1.2);
        var narrow = arrow(h, start, .05F); narrow.setDeltaMovement(5, 0, 0);
        h.getLevel().addFreshEntity(narrow); narrow.tick();
        h.assertTrue(!narrow.saveWithoutId(new net.minecraft.nbt.CompoundTag()).contains("ModuleEdgeBlock"), "Narrow arrow grazed terrain");
        narrow.discard();
        var wide = arrow(h, start, 1F); wide.setDeltaMovement(5, 0, 0);
        h.getLevel().addFreshEntity(wide); wide.tick();
        var saved = wide.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        h.assertTrue(saved.contains("ModuleEdgeBlock") && saved.getLong("ModuleEdgeBlock") == block.asLong(), "Wide arrow passed through terrain edge");
        h.assertTrue(wide.getDeltaMovement().lengthSqr() == 0, "Grazing arrow did not stop");
        wide.discard(); h.succeed();
    }
}
