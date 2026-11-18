package dev.sablelife.testing;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.sablelife.SableLife;
import dev.sablelife.api.BodyCombatApi;
import dev.sablelife.combat.BodyCombat;
import dev.sablelife.combat.BodyDamageBridge;
import dev.sablelife.combat.BodySphereQuery;
import dev.sablelife.combat.EntityHitbox;
import dev.sablelife.compat.letitwalk.WalkerLegDamage;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.ModuleAnchor;
import dev.sablelife.ownership.BodyOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder("sablelife_hitbox_tests")
@PrefixGameTestTemplate(false)
public final class WalkerLegDamageGameTests {
    @GameTest(template = "combat", batch = "walker_arm_damage", timeoutTicks = 100)
    public static void WalkerArmModeRoutesDamageThroughAttachedJoints(GameTestHelper test) throws ReflectiveOperationException {
        if (!ModList.get().getModContainerById("let_it_walk")
                .map(mod -> mod.getModInfo().getVersion().toString().equals("0.1.5")).orElse(false)
                || Boolean.getBoolean("sablelife.walkerExpectDisabled")) {
            test.succeed();
            return;
        }
        var level = test.getLevel();
        var origin = test.absolutePos(new BlockPos(8, 34, 8));
        var type = Class.forName("dev.letitwalk.demo.DemoWalkers$Type");
        var demo = Class.forName("dev.letitwalk.demo.DemoWalkers");
        var corePos = (BlockPos) demo.getMethod("place", ServerLevel.class, BlockPos.class, Direction.class, type, int.class)
                .invoke(null, level, origin, Direction.NORTH, type.getField("BUILT_SPIDER").get(null), origin.getY());
        var hingeType = Class.forName("dev.letitwalk.content.LegJointBlockEntity");
        var armMode = (Enum<?>) Class.forName("dev.letitwalk.content.LimbMode").getField("ARM").get(null);
        // The spider demo places its hull hinges two blocks below the Walker Core.
        var rootHinge = level.getBlockEntity(corePos.offset(2, -2, -2));
        test.assertTrue(hingeType.isInstance(rootHinge), "Could not find the spider demo's root hinge");
        var modeField = hingeType.getDeclaredField("limbMode");
        modeField.setAccessible(true);
        var behavior = (com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour)
                modeField.get(rootHinge);
        test.assertTrue(behavior != null, "The demo hinge has no mode option");
        behavior.setValue(armMode.ordinal());
        test.assertTrue(hingeType.getMethod("limbMode").invoke(rootHinge) == armMode, "The demo hinge did not enter Arm mode");
        var assembler = Class.forName("dev.letitwalk.walker.WalkerAssembler");
        assembler.getMethod("assemble", ServerLevel.class, BlockPos.class).invoke(null, level, corePos);
        var isArm = hingeType.getMethod("inArm");
        test.startSequence().thenIdle(12).thenExecute(() -> {
            var fixture = walker(test, origin);
            var heart = fixture.heart();
            var hinge = blockEntities(fixture.hull()).stream().filter(hingeType::isInstance).filter(be -> {
                try {
                    return (boolean) isArm.invoke(be);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Could not inspect the assembled arm", failure);
                }
            }).findFirst().orElseThrow(() -> new IllegalStateException("No assembled Arm-mode hinge on the hull"));
            var arm = SubLevelContainer.getContainer(level).getSubLevel(linkedChild(hinge));
            test.assertTrue(arm != null, "Arm mode did not assemble a child structure");
            var armBlock = solidBlock(level, arm);
            test.assertTrue(BodyCombat.findDamageHeart(level, armBlock) == heart, "Arm hit did not resolve to the core");
            var projectile = arrow(level);
            BodyCombat.onProjectileImpact(new ProjectileImpactEvent(projectile, hit(armBlock)));
            BodyCombat.onProjectileImpact(new ProjectileImpactEvent(projectile, hit(heart.getBlockPos())));
            health(test, heart, 994);
            test.assertTrue(BodyDamageBridge.melee(TestActors.creative(level), heart, armBlock,
                    armBlock.getCenter(), 7), "Melee did not damage the arm");
            health(test, heart, 987);
            test.assertTrue(areaDamage(test, heart, worldCenter(level, armBlock), 2) == 1, "Area hit missed the arm");
            health(test, heart, 978);
            test.assertTrue(BodyOwnership.get(level).breakMarkedBlock(level, hinge.getBlockPos(), heart.creatureId(), fixture.hull()),
                    "Could not cut the Arm-mode hinge");
            test.assertTrue(BodyCombat.findDamageHeart(level, armBlock) == null, "Detached arm still routed damage");
        }).thenSucceed();
    }

    @GameTest(template = "combat", batch = "walker_leg_damage", timeoutTicks = 180)
    public static void WalkerDamageUsesOneCorePerAttack(GameTestHelper test) throws ReflectiveOperationException {
        var level = test.getLevel();
        var origin = test.absolutePos(new BlockPos(8, 34, 8));
        var installed = ModList.get().getModContainerById("let_it_walk");
        if (installed.isEmpty()) {
            nativeFixture(test, origin);
            return;
        }
        boolean supported = installed.orElseThrow().getModInfo().getVersion().toString().equals("0.1.5")
                && !Boolean.getBoolean("sablelife.walkerExpectDisabled");
        buildWalker(level, origin);
        test.startSequence().thenIdle(12).thenExecute(() -> {
            var fixture = walker(test, origin);
            var hull = fixture.hull();
            var heart = fixture.heart();
            var corePos = heart.getBlockPos();
            var legs = fixture.legs();
            var targets = fixture.targets();
            var first = targets.getFirst();
            var other = targets.getLast();
            var hullHit = hit(corePos);
            if (!supported) {
                test.assertTrue(WalkerLegDamage.owner(level, first) == null, "Unverified version opened the leg route");
                test.assertTrue(BodyCombatApi.projectile(arrow(level), hullHit, 6), "Unverified version blocked native damage");
                health(test, heart, 994);
                return;
            }
            for (var target : targets) {
                test.assertTrue(BodyCombat.findDamageHeart(level, target) == heart, "Attached segment missed: " + target);
            }
            var melee = TestActors.creative(level);
            test.assertTrue(BodyDamageBridge.melee(melee, heart, first, first.getCenter(), 7), "Melee could not hurt the leg");
            health(test, heart, 993);

            var arrow = arrow(level);
            BodyCombat.onProjectileImpact(new ProjectileImpactEvent(arrow, hit(first)));
            for (var leg : targets) BodyCombat.onProjectileImpact(new ProjectileImpactEvent(arrow, hit(leg)));
            BodyCombat.onProjectileImpact(new ProjectileImpactEvent(arrow, hullHit));
            health(test, heart, 987);

            var firstWorld = worldCenter(level, first);
            var otherWorld = worldCenter(level, other);
            var center = firstWorld.lerp(otherWorld, .5);
            var area = areaDamage(test, heart, center, Math.max(12, center.distanceTo(firstWorld) + 1));
            test.assertTrue(area == 1, "Area returned more than one contact for the walker: " + area);
            health(test, heart, 978);
            level.explode(null, firstWorld.x, firstWorld.y, firstWorld.z, 3, Level.ExplosionInteraction.NONE);
            test.assertTrue(heart.health().current() < 978, "A real explosion by the foot caused no damage");
            var blastCenter = worldCenter(level, corePos).add(0, 2, 0);
            float beforeBodyBlast = heart.health().current();
            level.explode(null, blastCenter.x, blastCenter.y, blastCenter.z, 6, Level.ExplosionInteraction.NONE);
            float bodyBlast = beforeBodyBlast - heart.health().current();
            test.assertTrue(bodyBlast > 0, "Blast fixture missed the walker body");

            for (int i = 0; i < 2; i++) {
                var module = heart.modules().add("entity_hitbox");
                module.anchors(List.of(ModuleAnchor.create(heart, corePos, Direction.UP)));
                module.edit("hitboxDamageMode", 1, "");
            }
            heart.modules().damageBoxes.tick(heart);
            var receivers = heart.modules().damageBoxes.entities();
            test.assertTrue(receivers.size() == 2, "Hybrid fixture did not create two receivers");
            float before = heart.health().current();
            var hybridArrow = arrow(level);
            for (var receiver : receivers) receiver.hurt(level.damageSources().arrow(hybridArrow, null), 18);
            BodyCombat.onProjectileImpact(new ProjectileImpactEvent(hybridArrow, hit(other)));
            BodyCombat.onProjectileImpact(new ProjectileImpactEvent(hybridArrow, hit(first)));
            BodyCombat.onProjectileImpact(new ProjectileImpactEvent(hybridArrow, hullHit));
            heart.modules().damageBoxes.flush(heart);
            health(test, heart, before - 6);
            var nextArrow = arrow(level);
            BodyCombat.onProjectileImpact(new ProjectileImpactEvent(nextArrow, hit(first)));
            health(test, heart, before - 12);

            float beforeHybridBlast = heart.health().current();
            var receiverDamage = new ArrayList<Float>();
            Consumer<LivingDamageEvent.Pre> capture = event -> {
                if (event.getEntity() instanceof EntityHitbox box && box.core() == heart) receiverDamage.add(event.getNewDamage());
            };
            NeoForge.EVENT_BUS.addListener(capture);
            try {
                level.explode(null, blastCenter.x, blastCenter.y, blastCenter.z, 6, Level.ExplosionInteraction.NONE);
            } finally {
                NeoForge.EVENT_BUS.unregister(capture);
            }
            heart.modules().damageBoxes.flush(heart);
            test.assertTrue(receiverDamage.size() == 2, "The real blast missed one or both Hybrid receivers");
            health(test, heart, beforeHybridBlast - Math.max(bodyBlast, Math.max(receiverDamage.getFirst(), receiverDamage.getLast())));

            var hinge = blockEntities(hull).stream()
                    .filter(be -> legs.stream().anyMatch(leg -> leg.getUniqueId().equals(linkedChild(be))))
                    .findFirst().orElseThrow();
            var detached = legs.stream().filter(leg -> leg.getUniqueId().equals(linkedChild(hinge))).findFirst().orElseThrow();
            var detachedTarget = solidBlock(level, detached);
            test.assertTrue(BodyOwnership.get(level).breakMarkedBlock(level, hinge.getBlockPos(), heart.creatureId(), hull),
                    "The marked hinge was not cut");
            test.assertTrue(BodyCombat.findDamageHeart(level, detachedTarget) == null,
                    "Cut hinge still transferred damage through the cached leg graph");
            test.assertTrue(BodyCombat.findDamageHeart(level, corePos) == heart, "Cut hinge invalidated the hull");
        }).thenSucceed();
    }

    record WalkerFixture(SubLevel hull, HeartBlockEntity heart, List<SubLevel> legs, List<BlockPos> targets) {}

    static void buildWalker(ServerLevel level, BlockPos origin) throws ReflectiveOperationException {
        var type = Class.forName("dev.letitwalk.demo.DemoWalkers$Type");
        var demo = Class.forName("dev.letitwalk.demo.DemoWalkers");
        demo.getMethod("build", ServerLevel.class, BlockPos.class, Direction.class, type, int.class)
                .invoke(null, level, origin, Direction.NORTH, type.getField("BUILT_SPIDER").get(null), origin.getY());
    }

    static WalkerFixture walker(GameTestHelper test, BlockPos origin) {
        var level = test.getLevel();
        var container = SubLevelContainer.getContainer(level);
        var walker = container.getAllSubLevels().stream()
                .flatMap(body -> blockEntities(body).stream())
                .filter(be -> be.getClass().getName().equals("dev.letitwalk.content.WalkerCoreBlockEntity"))
                .min(Comparator.comparingDouble(be -> worldCenter(level, be.getBlockPos()).distanceToSqr(origin.getCenter())))
                .orElseThrow();
        var hull = Sable.HELPER.getContaining(level, walker.getBlockPos());
        var corePos = walker.getBlockPos().above();
        level.setBlockAndUpdate(corePos, SableLife.HEART.get().defaultBlockState());
        var heart = (HeartBlockEntity) level.getBlockEntity(corePos);
        test.assertTrue(Sable.HELPER.getContaining(level, corePos) == hull, "Heart fixture is outside the walker hull");
        activate(heart, level);
        var legs = new ArrayList<SubLevel>();
        for (var leg : ((BlockEntitySubLevelActor) walker).sable$getLoadingDependencies()) legs.add(leg);
        var targets = legs.stream().map(leg -> solidBlock(level, leg)).distinct().toList();
        test.assertTrue(targets.size() >= 2, "Demo walker did not assemble its legs");
        return new WalkerFixture(hull, heart, List.copyOf(legs), targets);
    }

    private static int areaDamage(GameTestHelper test, HeartBlockEntity heart, net.minecraft.world.phys.Vec3 center, double radius) {
        var level = test.getLevel();
        test.assertTrue(BodySphereQuery.query(level, center, center, radius).stream().filter(hit -> hit.core() == heart).count() == 1,
                "The area geometry did not return exactly one walker");
        return BodyCombatApi.sphere(arrow(level), center, center, radius, distance -> 9);
    }

    private static void nativeFixture(GameTestHelper test, BlockPos origin) {
        var level = test.getLevel();
        level.setBlockAndUpdate(origin, SableLife.HEART.get().defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, List.of(origin),
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(), origin.getX(), origin.getY(), origin.getZ()));
        var heart = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
        activate(heart, level);
        test.assertTrue(WalkerLegDamage.owner(level, heart.getBlockPos()) == null, "Absent optional mod opened the adapter");
        test.assertTrue(BodyCombatApi.projectile(arrow(level), hit(heart.getBlockPos()), 6), "Native projectile stopped working");
        health(test, heart, 994);
        test.succeed();
    }

    private static void activate(HeartBlockEntity heart, ServerLevel level) {
        heart.configure(1000, 100);
        heart.tryActivate(TestActors.creative(level));
    }

    static List<BlockEntity> blockEntities(SubLevel body) {
        return body.getPlot().getLoadedChunks().stream().flatMap(chunk -> chunk.getChunk().getBlockEntities().values().stream()).toList();
    }

    static UUID linkedChild(BlockEntity blockEntity) {
        if (!blockEntity.getClass().getName().equals("dev.letitwalk.content.LegJointBlockEntity")) return null;
        try {
            return (UUID) blockEntity.getClass().getMethod("child").invoke(blockEntity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read the demo walker hinge", e);
        }
    }

    static BlockPos solidBlock(ServerLevel level, SubLevel body) {
        var b = body.getPlot().getBoundingBox();
        for (var pos : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
            if (level.hasChunkAt(pos) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return pos.immutable();
        }
        throw new IllegalStateException("No damageable block in walker segment");
    }

    private static net.minecraft.world.phys.Vec3 worldCenter(ServerLevel level, BlockPos pos) {
        return Sable.HELPER.getContaining(level, pos).logicalPose().transformPosition(pos.getCenter());
    }

    private static Arrow arrow(ServerLevel level) {
        var arrow = new Arrow(level, 0, 0, 0, new ItemStack(Items.ARROW), null);
        arrow.setBaseDamage(3);
        arrow.setDeltaMovement(2, 0, 0);
        return arrow;
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(pos.getCenter(), Direction.UP, pos, false);
    }

    private static void health(GameTestHelper test, HeartBlockEntity heart, float expected) {
        test.assertTrue(Math.abs(heart.health().current() - expected) < .001F,
                "Expected " + expected + " HP, got " + heart.health().current());
    }
}
