package dev.sablelife.testing;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.sablelife.combat.BodyCombat;
import dev.sablelife.combat.EntityHitboxes;
import dev.sablelife.compat.letitwalk.WalkerLegDamage;
import dev.sablelife.heart.HeartState;
import dev.sablelife.module.ModuleAnchor;
import dev.sablelife.module.VirtualModule;
import io.redspace.ironsspellbooks.entity.spells.fire_arrow.FireArrowProjectile;
import io.redspace.ironsspellbooks.entity.spells.magic_missile.MagicMissileProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@GameTestHolder("sablelife_hitbox_tests")
@PrefixGameTestTemplate(false)
public final class WalkerWeaponGameTests {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    @GameTest(template = "combat", batch = "walker_optional_weapons", timeoutTicks = 100)
    public static void OptionalWeaponImpactsAgainstAttachedLegs(GameTestHelper test) throws Exception {
        if (!ModList.get().isLoaded("let_it_walk") || !ModList.get().isLoaded("tacz")
                && !ModList.get().isLoaded("irons_artifice") && !ModList.get().isLoaded("irons_spellbooks")) {
            test.succeed();
            return;
        }
        var level = test.getLevel();
        var origin = test.absolutePos(new BlockPos(8, 34, 8));
        WalkerLegDamageGameTests.buildWalker(level, origin);
        test.startSequence().thenIdle(12).thenExecute(() -> {
            var fixture = WalkerLegDamageGameTests.walker(test, origin);
            var heart = fixture.heart();
            var first = fixture.targets().getFirst();
            var second = fixture.targets().getLast();
            test.assertTrue(WalkerLegDamage.owner(level, first) == heart, "Weapon fixture leg has no adapter owner");
            try {
                boolean breaching = ModList.get().isLoaded("tacz_tactical_breaching");
                if (ModList.get().isLoaded("tacz") && !breaching) {
                    var bullet = tacz(level, world(level, first));
                    taczImpact(level, bullet, first);
                    taczImpact(level, bullet, second);
                    taczImpact(level, bullet, heart.getBlockPos());
                    expect(test, fixture, "TaCZ M4A1 projectile on multiple legs and hull", 17);

                    var aim = clearAim(test, first);
                    var flying = tacz(level, aim.start());
                    flying.setPos(aim.start());
                    flying.setDeltaMovement(aim.velocity());
                    tickTacz(flying);
                    expect(test, fixture, "TaCZ M4A1 flight into a leg", 17);
                }
                if (ModList.get().isLoaded("createbigcannons")) {
                    var registry = Class.forName("rbasamoyai.createbigcannons.index.CBCEntityTypes");
                    var entry = registry.getField("SHOT").get(null);
                    var entityType = (EntityType<?>) entry.getClass().getMethod("get").invoke(entry);
                    var projectile = (Projectile) entityType.create(level);
                    field(projectile, "damage", 23F);
                    projectile.setDeltaMovement(0, -1, 0);
                    var base = Class.forName("rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile");
                    var grief = Class.forName("rbasamoyai.createbigcannons.config.CBCCfgMunitions$GriefState");
                    var contextType = Class.forName("rbasamoyai.createbigcannons.munitions.ProjectileContext");
                    var context = contextType.getConstructor(base, grief).newInstance(projectile, grief.getField("NO_DAMAGE").get(null));
                    var cls = Class.forName("rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile");
                    var penetration = cls.getDeclaredMethod("calculateBlockPenetration", contextType,
                            net.minecraft.world.level.block.state.BlockState.class, BlockHitResult.class);
                    penetration.setAccessible(true);
                    penetration.invoke(projectile, context, level.getBlockState(first), hit(first));
                    float initialLoss = 1000 - heart.health().current();
                    test.assertTrue(initialLoss >= 23 && initialLoss <= 60, "CBC leg impact loss " + initialLoss);
                    dev.sablelife.combat.BodyDamageBridge.cannon(projectile, hit(second));
                    dev.sablelife.combat.BodyDamageBridge.cannon(projectile, hit(heart.getBlockPos()));
                    expect(test, fixture, "CBC shot and repeated contacts", initialLoss);
                }
                if (ModList.get().isLoaded("irons_artifice")) {
                    var bullet = ArtificeBodyGameTests.bullet(test, 2, 4);
                    ArtificeBodyGameTests.impact(bullet, hit(first));
                    ArtificeBodyGameTests.impact(bullet, hit(second));
                    expect(test, fixture, "Iron's Arms 'n Artifice bullet on two legs", 10);
                }
                if (ModList.get().isLoaded("irons_spellbooks")) {
                    var aim = clearAim(test, first);
                    var caster = TestActors.creative(level);
                    caster.setPos(aim.start().subtract(0, 1, 0));
                    var missile = new MagicMissileProjectile(level, caster);
                    missile.setPos(aim.start());
                    missile.setDeltaMovement(aim.velocity());
                    missile.setNoGravity(true);
                    missile.setDamage(10);
                    missile.tick();
                    expect(test, fixture, "Iron's Spells Magic Missile flight", 10);

                    var fire = new FireArrowProjectile(level, caster);
                    fire.setPos(aim.start());
                    fire.setDeltaMovement(aim.velocity());
                    fire.setNoGravity(true);
                    fire.setDamage(20);
                    fire.setExplosionRadius(3);
                    fire.tick();
                    expect(test, fixture, "Iron's Spells Fire Arrow direct leg hit without extra splash", 20);
                }
                if (breaching) {
                    var glassHull = heart.getBlockPos().above();
                    level.setBlockAndUpdate(glassHull, Blocks.GLASS.defaultBlockState());
                    dev.sablelife.ownership.BodyOwnership.get(level).bind(glassHull, heart.creatureId());
                    level.setBlockAndUpdate(first, Blocks.GLASS.defaultBlockState());
                    breachingImpact(test, glassHull);
                    expect(test, fixture, "TaCZ Tactical Breaching canceled hull impact", 17);
                    var bullet = breachingImpact(test, first);
                    taczImpact(level, bullet, second);
                    taczImpact(level, bullet, heart.getBlockPos());
                    expect(test, fixture, "TaCZ Tactical Breaching canceled leg impact", 17);
                    verifyBreachingReceivers(test, fixture, first);
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Optional weapon fixture could not call the native attack", e);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).thenSucceed();
    }

    private static void verifyBreachingReceivers(GameTestHelper test, WalkerLegDamageGameTests.WalkerFixture fixture,
                                                 BlockPos leg) throws ReflectiveOperationException {
        var level = test.getLevel();
        var heart = fixture.heart();
        var anchor = ModuleAnchor.create(heart, heart.getBlockPos(), Direction.UP);
        var boxes = new ArrayList<VirtualModule>();
        for (int count = 1; count <= 2; count++) {
            var module = heart.modules().add("entity_hitbox");
            boxes.add(module);
            module.anchors(List.of(anchor));
            module.edit("hitboxDamageMode", 0, "");
            module.edit("hitboxOffsetY", 4, "");
            heart.modules().damageBoxes.tick(heart);
            test.assertTrue(heart.modules().damageBoxes.entities().size() == count && EntityHitboxes.blocksStructure(heart),
                    "Expected " + count + " active entity-only receivers");
            level.setBlockAndUpdate(leg, Blocks.GLASS.defaultBlockState());
            breachingImpact(test, leg);
            expect(test, fixture, "TaCZ leg respects entity-only mode with " + count + " receivers", 0);
            hitReceivers(level, fixture);
            expect(test, fixture, "TaCZ native damage with " + count + " receivers", 17);
        }

        for (var box : boxes) box.edit("hitboxDamageMode", 1, "");
        heart.modules().damageBoxes.tick(heart);
        test.assertTrue(EntityHitboxes.hybrid(heart), "Expected active Hybrid receivers");
        level.setBlockAndUpdate(leg, Blocks.GLASS.defaultBlockState());
        var shot = breachingImpact(test, leg);
        var alias = level.damageSources().thrown(shot, shot.getOwner());
        for (var receiver : heart.modules().damageBoxes.entities()) receiver.hurt(alias, 17);
        heart.modules().damageBoxes.flush(heart);
        expect(test, fixture, "TaCZ leg and overlapping Hybrid receivers share one hit", 17);
    }

    private static void hitReceivers(ServerLevel level, WalkerLegDamageGameTests.WalkerFixture fixture)
            throws ReflectiveOperationException {
        var heart = fixture.heart();
        var receivers = heart.modules().damageBoxes.entities();
        var target = receivers.getFirst().getBoundingBox().getCenter();
        var shot = tacz(level, target);
        shot.setPos(target.add(0, 1, 0));
        var resultType = Class.forName("com.tacz.guns.entity.EntityKineticBullet$EntityResult");
        var hitType = Class.forName("com.tacz.guns.util.TacHitResult");
        var resultConstructor = resultType.getConstructor(Entity.class, Vec3.class, boolean.class);
        var hitConstructor = hitType.getConstructor(resultType);
        var callback = shot.getClass().getDeclaredMethod("onHitEntity", hitType, Vec3.class, Vec3.class);
        callback.setAccessible(true);
        for (var receiver : receivers) {
            var hit = hitConstructor.newInstance(resultConstructor.newInstance(receiver, target, false));
            callback.invoke(shot, hit, shot.position(), target);
        }
        heart.modules().damageBoxes.flush(heart);
    }

    private static void tickTacz(Projectile bullet) throws ReflectiveOperationException {
        var method = bullet.getClass().getDeclaredMethod("onBulletTick");
        method.setAccessible(true);
        method.invoke(bullet);
    }

    @GameTest(template = "combat", batch = "walker_part_break_cleanup", timeoutTicks = 155)
    public static void PartBreakLeavesWalkerLegOutsideDetachedCleanup(GameTestHelper test) throws Exception {
        if (!ModList.get().isLoaded("let_it_walk") || Boolean.getBoolean("sablelife.walkerExpectDisabled")
                || !ModList.get().getModContainerById("let_it_walk").orElseThrow().getModInfo().getVersion().toString().equals("0.1.5")) {
            test.succeed();
            return;
        }
        var level = test.getLevel();
        var origin = test.absolutePos(new BlockPos(8, 34, 8));
        WalkerLegDamageGameTests.buildWalker(level, origin);
        var seq = test.startSequence().thenIdle(12);
        seq.thenExecute(() -> {
            var fixture = WalkerLegDamageGameTests.walker(test, origin);
            var heart = fixture.heart();
            var fixtureIds = fixture.legs().stream().map(body -> body.getUniqueId()).toList();
            var hullBlocks = WalkerLegDamageGameTests.blockEntities(fixture.hull());
            var hinge = hullBlocks.stream().filter(be -> fixtureIds.contains(WalkerLegDamageGameTests.linkedChild(be)))
                    .findFirst().orElseThrow();
            int legIndex = fixtureIds.indexOf(WalkerLegDamageGameTests.linkedChild(hinge));
            var body = fixture.legs().get(legIndex);
            var foot = fixture.targets().get(legIndex);
            var ownership = dev.sablelife.ownership.BodyOwnership.get(level);
            test.assertTrue(heart.limbCleanup().enabled() && heart.limbCleanup().delay() == 3, "Cleanup is not enabled at 3 seconds");
            test.assertTrue(ownership.owner(foot) == null && WalkerLegDamage.owner(level, foot) == heart,
                    "Foot should route damage while remaining outside ownership");
            var cut = heart.modules().add("part_break");
            cut.anchors(List.of(ModuleAnchor.create(heart, hinge.getBlockPos(), Direction.UP)));
            heart.modules().partBreak.tick(heart);
            test.assertTrue(level.getBlockState(hinge.getBlockPos()).isAir(), "Part Break did not remove the hull hinge");
            test.assertTrue(BodyCombat.findDamageHeart(level, foot) == null, "Cut leg still routes damage");
            test.runAfterDelay(100, () -> {
                test.assertTrue(heart.health().state() == HeartState.ALIVE, "Walker core stopped running during cleanup test");
                test.assertTrue(heart.limbCleanup().save().getList("Pending", 10).isEmpty(),
                        "Cleanup scheduled a Walker-owned leg");
                var survivingLeg = SubLevelContainer.getContainer(level).getSubLevel(body.getUniqueId());
                test.assertTrue(survivingLeg == body && !body.isRemoved() && level.hasChunkAt(foot) && !level.getBlockState(foot).isAir(),
                        "Cut Walker leg did not remain for the duration of the cleanup test");
                LOG.info("Walker Part Break: original leg still present after 100 ticks, with no Sable Life cleanup job");
                test.succeed();
            });
        });
    }

    private static Projectile tacz(ServerLevel level, Vec3 target) throws ReflectiveOperationException {
        var type = Class.forName("com.tacz.guns.entity.EntityKineticBullet");
        var bullet = (Projectile) type.getConstructor(EntityType.class, Level.class).newInstance(type.getField("TYPE").get(null), level);
        var pair = Class.forName("com.tacz.guns.resource.pojo.data.gun.ExtraDamage$DistanceDamagePair")
                .getConstructor(float.class, float.class).newInstance(100F, 17F);
        field(bullet, "damageAmount", new LinkedList<>(List.of(pair)));
        field(bullet, "startPos", target.add(0, 1, 0));
        field(bullet, "distanceAmount", 100F);
        field(bullet, "damageModifier", 1F);
        field(bullet, "shotDamageMultiplier", 1F);
        for (String key : List.of("ammoId", "gunId", "gunDisplayId")) {
            field(bullet, key, net.minecraft.resources.ResourceLocation.parse(key.equals("ammoId") ? "tacz:556x45" : "tacz:m4a1"));
        }
        bullet.setDeltaMovement(0, -2, 0);
        return bullet;
    }

    private static void taczImpact(ServerLevel level, Projectile bullet, BlockPos pos) throws ReflectiveOperationException {
        Method method = bullet.getClass().getDeclaredMethod("onHitBlock", BlockHitResult.class, Vec3.class, Vec3.class);
        method.setAccessible(true);
        var point = world(level, pos);
        method.invoke(bullet, hit(pos), point.add(0, 1, 0), point);
    }

    private static Projectile breachingImpact(GameTestHelper test, BlockPos pos) throws ReflectiveOperationException {
        var level = test.getLevel();
        var location = world(level, pos);
        var bullet = tacz(level, location);
        bullet.setPos(location.add(0, 1, 0));
        var type = Class.forName("com.tacz.guns.api.event.server.AmmoHitBlockEvent");
        var ctor = type.getConstructor(Level.class, BlockHitResult.class, net.minecraft.world.level.block.state.BlockState.class,
                bullet.getClass());
        var event = (net.neoforged.bus.api.Event) ctor.newInstance(level, hit(pos), level.getBlockState(pos), bullet);
        NeoForge.EVENT_BUS.post(event);
        test.assertTrue(((ICancellableEvent) event).isCanceled(), "Breaching did not cancel the glass hit at " + pos);
        return bullet;
    }

    private static void field(Object instance, String name, Object value) throws ReflectiveOperationException {
        for (var type = instance.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(instance, value);
                return;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    record Aim(Vec3 start, Vec3 velocity) {}
    private static Aim clearAim(GameTestHelper test, BlockPos target) {
        var aim = aimAt(test, target);
        if (aim == null) throw new IllegalStateException("No clear shot to the test leg");
        return aim;
    }

    static Aim aimAt(GameTestHelper test, BlockPos target) {
        var level = test.getLevel();
        var center = world(level, target);
        for (var side : Direction.values()) {
            var start = center.add(Vec3.atLowerCornerOf(side.getNormal()).scale(2));
            var motion = center.subtract(start).scale(1.2);
            var result = level.clip(new ClipContext(start, start.add(motion), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null));
            if (result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(target)) return new Aim(start, motion);
        }
        return null;
    }

    private static Vec3 world(ServerLevel level, BlockPos pos) {
        return Sable.HELPER.getContaining(level, pos).logicalPose().transformPosition(pos.getCenter());
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(pos.getCenter(), Direction.UP, pos, false);
    }

    private static void expect(GameTestHelper test, WalkerLegDamageGameTests.WalkerFixture fixture, String weapon, float expected) {
        float loss = 1000 - fixture.heart().health().current();
        LOG.info("Walker weapon test: {} HP loss={}", weapon, loss);
        test.assertTrue(Math.abs(loss - expected) < .001F, weapon + " expected " + expected + " HP loss, got " + loss);
        fixture.heart().restoreFullHealth();
    }
}
