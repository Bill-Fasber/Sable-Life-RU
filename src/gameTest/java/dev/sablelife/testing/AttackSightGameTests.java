package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.attack.ModuleShot;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.HeartModules;
import dev.sablelife.module.MobGrab;
import dev.sablelife.module.ModuleAnchor;
import dev.sablelife.module.ModuleCombat;
import dev.sablelife.module.ModuleGrab;
import dev.sablelife.module.ModuleGroundStrike;
import dev.sablelife.module.ModuleHoming;
import dev.sablelife.module.ModuleLaser;
import dev.sablelife.module.ModuleQuake;
import dev.sablelife.module.ModuleTargets;
import dev.sablelife.module.PlayerFreeze;
import dev.sablelife.module.ProjectileAim;
import dev.sablelife.module.TargetCastOrigin;
import dev.sablelife.module.VirtualModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder("sablelife_blast_tests")
@PrefixGameTestTemplate(false)
public final class AttackSightGameTests {
    private static Pig pig(GameTestHelper h, Vec3 center) {
        var pig = EntityType.PIG.create(h.getLevel());
        pig.setNoAi(true);
        pig.setNoGravity(true);
        pig.setPos(center.subtract(0, pig.getBbHeight() / 2, 0));
        h.getLevel().addFreshEntity(pig);
        return pig;
    }

    private record Targets(Pig hidden, Pig visible) implements AutoCloseable {
        @Override public void close() {
            hidden.discard();
            visible.discard();
        }
    }

    private static Targets targets(GameTestHelper h, HeartBlockEntity core, Vec3 origin) {
        var roof = BlockPos.containing(origin.add(1, -1, 0));
        for (int x = 0; x < 4; x++) {
            h.getLevel().setBlockAndUpdate(roof.offset(x, 0, 0), Blocks.STONE.defaultBlockState());
        }
        var targets = new Targets(pig(h, origin.add(3, -2.75, 0)), pig(h, origin.add(-6, 0, 0)));
        h.assertTrue(!ModuleTargets.visible(h.getLevel(), core.creatureId(), origin, targets.hidden())
                        && ModuleTargets.visible(h.getLevel(), core.creatureId(), origin, targets.visible()),
                "Fixture must contain an obscured nearer target and a visible farther target");
        return targets;
    }

    private static VirtualModule module(HeartBlockEntity core, String type) {
        var module = core.modules().add(type);
        module.anchors(List.of(ModuleAnchor.create(core, core.getBlockPos(), Direction.UP)));
        module.edit("trigger", 0, "");
        module.edit("target", 1, "");
        return module;
    }

    private static HeartBlockEntity core(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(9, 40, 9));
        level.setBlockAndUpdate(pos, SableLife.HEART.get().defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos),
                new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ()));
        var core = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
        var hover = module(core, "hover_anchor");
        hover.edit("hoverRotation", 1, "");
        return core;
    }

    private static void sight(GameTestHelper h, VirtualModule module, int value) {
        h.assertTrue(module.edit("lineOfSight", value, ""), "Missing sight control for " + module.type());
    }

    @GameTest(template = "combat", batch = "attack_sight_target_cast")
    public static void TargetPositionCastsFilterUndergroundTargets(GameTestHelper h) {
        var core = core(h);
        var module = module(core, "sphere_blast");
        core.tryActivate(TestActors.creative(h.getLevel()));
        var origin = TargetCastOrigin.searchOrigin(core, module);
        try (var targets = targets(h, core, origin)) {
            var visible = targets.visible().getBoundingBox().getCenter();
            for (String type : List.of("sphere_blast", "freeze", "pulse", "pull")) {
                var cast = type.equals(module.type()) ? module : module(core, type);
                cast.edit("releasePosition", 1, "");
                cast.edit("targetMultiple", 1, "");
                cast.edit("castTargetRange", 8, "");
                cast.edit("freezeTarget", 1, "");
                int on = type.equals("pull") ? 0 : 1;
                sight(h, cast, on);
                h.assertTrue(TargetCastOrigin.points(core, cast).equals(List.of(visible)),
                        type + " included an underground target in its cast points");
                sight(h, cast, 1 - on);
                h.assertTrue(TargetCastOrigin.points(core, cast).size() == 2,
                        type + " lost its explicitly unrestricted cast points");
            }
        }
        h.succeed();
    }

    @GameTest(template = "combat", batch = "attack_sight_ground_strike", timeoutTicks = 30)
    public static void GroundStrikeCastsAtVisibleGroundRatherThanTheUndergroundTarget(GameTestHelper h) {
        var core = core(h);
        var strike = module(core, "ground_strike");
        strike.edit("strikeMode", 2, "");
        strike.edit("strikeRange", 8, "");
        strike.edit("warningTime", 5, "");
        strike.edit("strikeHeight", 2, "");
        strike.edit("strikeSpeed", 8, "");
        strike.edit("damage", 3, "");
        core.tryActivate(TestActors.creative(h.getLevel()));
        var origin = ModuleCombat.baseOrigin(core, strike);
        var targets = targets(h, core, origin);
        var visible = targets.visible();
        var visibleGround = BlockPos.containing(visible.position()).below();
        h.getLevel().setBlockAndUpdate(visibleGround, Blocks.STONE.defaultBlockState());
        sight(h, strike, 0);
        var level = h.getLevel();
        var oldPattern = ModuleGroundStrike.pattern(level, core, strike);
        h.assertTrue(oldPattern.size() == 1 && oldPattern.getFirst().x > origin.x,
                "Legacy cast did not reproduce the nearer underground target");
        sight(h, strike, 1);
        var currentPattern = ModuleGroundStrike.pattern(level, core, strike);
        h.assertTrue(currentPattern.size() == 1 && currentPattern.getFirst().x < origin.x,
                "Targeted Ground Strike did not skip the obstructed ground column");
        core.modules().tick(core);
        h.runAfterDelay(8, () -> {
            try (targets) {
                h.assertTrue(visible.getHealth() == 7 && targets.hidden().getHealth() == 10,
                        "Ground Strike did not land on the visible target");
                strike.edit("enabled", 0, "");
            }
            h.succeed();
        });
    }

    @GameTest(template = "combat", batch = "attack_sight_quake")
    public static void TargetOriginQuakeWaitsForAnExposedTarget(GameTestHelper h) {
        var core = core(h);
        var quake = module(core, "quake");
        quake.edit("quakeOrigin", 1, "");
        quake.edit("quakeDetectRange", 8, "");
        core.tryActivate(TestActors.creative(h.getLevel()));
        var origin = ModuleCombat.baseOrigin(core, quake);
        try (var targets = targets(h, core, origin)) {
            for (var point : List.of(origin, targets.visible().position())) {
                h.getLevel().setBlockAndUpdate(BlockPos.containing(point).below(), Blocks.STONE.defaultBlockState());
            }
            sight(h, quake, 1);
            h.assertTrue(ModuleQuake.detectTargets(h.getLevel(), core, quake, origin).equals(List.of(targets.visible())),
                    "Quake included an obscured target in its target-origin selection");
            var engine = new ModuleQuake();
            try {
                engine.tick(core);
                h.assertTrue(engine.phase(quake.id()).equals("warning"), "Quake failed to start with an exposed target");
                engine.clear();
                targets.visible().discard();
                engine.tick(core);
                h.assertTrue(engine.phase(quake.id()).equals("waiting"), "Quake started with only an underground target");
                engine.clear();
                sight(h, quake, 0);
                engine.tick(core);
                h.assertTrue(engine.phase(quake.id()).equals("warning"), "Quake lost unrestricted target-origin mode");
            } finally {
                engine.clear();
            }
        }
        h.succeed();
    }

    @GameTest(template = "combat", batch = "attack_sight_grab")
    public static void GrabUsesAnExposedCaptureTarget(GameTestHelper h) {
        var core = core(h);
        var grab = module(core, "grab");
        grab.edit("grabRadius", 8, "");
        grab.edit("grabTarget", 1, "");
        sight(h, grab, 1);
        core.tryActivate(TestActors.creative(h.getLevel()));
        var level = h.getLevel();
        var point = ModuleGrab.holdPoint(core, grab);
        try (var targets = targets(h, core, point)) {
            try {
                h.assertTrue(HeartModules.triggered(grab, core) && ModuleGrab.capture(level, core, grab) == 1,
                        "Visible capture candidate was skipped");
                h.assertTrue(MobGrab.held(targets.visible()) && !MobGrab.held(targets.hidden()),
                        "Grab did not select the exposed target");
                targets.visible().discard();
                h.assertTrue(ModuleGrab.capture(level, core, grab) == 0, "Grab captured through terrain");
            } finally {
                MobGrab.release(level.getServer(), new PlayerFreeze.Source(core.creatureId(), grab.audioId()));
            }
        }
        h.succeed();
    }

    @GameTest(template = "combat", batch = "attack_sight_aim")
    public static void DirectAndTrackingAimRespectTheirSightToggle(GameTestHelper h) {
        var core = core(h);
        var source = module(core, "gun");
        core.tryActivate(TestActors.creative(h.getLevel()));
        var origin = ModuleCombat.nozzle(core, source);
        var level = h.getLevel();
        try (var targets = targets(h, core, origin)) {
            for (String type : List.of("arrow", "gun", "slash")) {
                var weapon = type.equals(source.type()) ? source : module(core, type);
                weapon.edit("shotAim", 1, "");
                weapon.edit("targetRange", 8, "");
                sight(h, weapon, 1);
                var directions = ProjectileAim.directions(core, weapon, origin);
                h.assertTrue(directions.size() == 1 && directions.getFirst().x < -.99,
                        type + " aimed at an underground target");
                sight(h, weapon, 0);
                directions = ProjectileAim.directions(core, weapon, origin);
                h.assertTrue(directions.size() == 1 && directions.getFirst().x > 0,
                        type + " did not honor its unrestricted aim mode");
                weapon.edit("shotAim", 0, "");
                sight(h, weapon, 1);
                h.assertTrue(ProjectileAim.directions(core, weapon, origin).equals(List.of(ModuleCombat.direction(core, weapon))),
                        type + " applied acquisition sight to a fixed shot");
            }
            var laser = module(core, "laser");
            laser.edit("laserAim", 1, "");
            laser.edit("beamLength", 8, "");
            sight(h, laser, 1);
            h.assertTrue(ModuleLaser.acquireTargets(level, core, laser, origin).equals(List.of(targets.visible())),
                    "Tracking laser aimed at the underground target");
            sight(h, laser, 0);
            h.assertTrue(ModuleLaser.acquireTargets(level, core, laser, origin).equals(List.of(targets.hidden())),
                    "Tracking laser lost its unrestricted aim mode");

            var homing = module(core, "homing");
            homing.edit("targetRange", 8, "");
            sight(h, homing, 1);
            h.assertTrue(ModuleHoming.targets(level, core, homing, origin).equals(List.of(targets.visible())),
                    "Homing shots acquired the underground target");
            sight(h, homing, 0);
            h.assertTrue(ModuleHoming.targets(level, core, homing, origin).equals(List.of(targets.hidden())),
                    "Homing lost its unrestricted aim mode");
        }
        h.succeed();
    }

    @GameTest(template = "combat", batch = "attack_sight_legacy")
    public static void MissingAndExplicitSightValuesKeepTheirIntendedMeaning(GameTestHelper h) {
        var legacyOn = List.of("arrow", "gun", "slash", "laser");
        for (String type : List.of("arrow", "gun", "slash", "laser", "ground_strike", "quake", "sphere_blast", "freeze", "pulse", "grab",
                "summoned_weapon", "homing", "mortar", "pull")) {
            var module = new VirtualModule(type);
            var old = module.save();
            old.getCompound("Settings").remove("lineOfSight");
            var restored = VirtualModule.load(old);
            h.assertTrue(restored.value("lineOfSight") == (legacyOn.contains(type) ? 1 : 0),
                    "Missing setting changed legacy behavior for " + type);
            h.assertTrue(module.value("lineOfSight") == (type.equals("pull") ? 0 : 1),
                    "Fresh module did not require sight for " + type);
            for (int value : List.of(0, 1)) {
                sight(h, module, value);
                h.assertTrue(VirtualModule.load(module.save()).value("lineOfSight") == value,
                        "Explicit sight setting was lost on reload for " + type);
            }
        }
        h.succeed();
    }

    @GameTest(template = "combat", batch = "attack_sight_tracking")
    public static void ActiveTrackingDropsAnObscuredTarget(GameTestHelper h) {
        var core = core(h);
        var homing = module(core, "homing");
        homing.edit("homingDeploy", 0, "");
        homing.edit("chargeTime", 0, "");
        homing.edit("targetRange", 8, "");
        sight(h, homing, 1);
        var laser = module(core, "laser");
        laser.edit("laserAim", 1, "");
        laser.edit("chargeTime", 0, "");
        laser.edit("beamLength", 8, "");
        sight(h, laser, 1);
        core.tryActivate(TestActors.creative(h.getLevel()));
        var level = h.getLevel();
        var origin = ModuleCombat.nozzle(core, laser);
        var target = pig(h, origin.add(-5, 0, 0));
        var shot = ModuleShot.homing(level, origin, origin, homing, core, 0);
        var beam = new ModuleLaser();
        try {
            shot.tick();
            h.assertTrue(target.getUUID().equals(shot.homingTarget()) && !shot.isRemoved(),
                    "Homing shot failed to lock on to a visible target");
            beam.tick(core);
            var aim = beam.aimPoint(laser.id());
            h.assertTrue(aim != null && beam.phase(laser.id()).equals("firing"),
                    "Tracking beam failed to start with a visible target");
            for (int y = -1; y <= 1; y++) {
                level.setBlockAndUpdate(BlockPos.containing(origin.add(-2, y, 0)), Blocks.STONE.defaultBlockState());
            }
            target.setPos(target.position().add(0, 0, .25));
            h.assertTrue(!ModuleTargets.visible(level, core.creatureId(), origin, target), "Wall did not obscure the target");
            shot.tick();
            h.assertTrue(shot.isRemoved(), "Homing shot retained its target through terrain");
            beam.tick(core);
            h.assertTrue(beam.aimPoint(laser.id()).equals(aim), "Emitted beam changed aim through terrain");
        } finally {
            shot.discard();
            beam.clear();
            target.discard();
        }
        h.succeed();
    }

    @GameTest(template = "combat", batch = "attack_sight_summon")
    public static void TargetedSummonTurnsTowardsAnExposedTarget(GameTestHelper h) {
        var core = core(h);
        var weapon = module(core, "gun");
        weapon.edit("enabled", 0, "");
        var summon = module(core, "summoned_weapon");
        summon.projectileItem(new ItemStack(Items.CROSSBOW), h.getLevel().registryAccess());
        summon.summonAttack(weapon.id());
        summon.edit("summonAim", 1, "");
        summon.edit("summonTurnSpeed", 180, "");
        summon.edit("summonWindup", 100, "");
        summon.edit("targetRange", 8, "");
        sight(h, summon, 1);
        core.tryActivate(TestActors.creative(h.getLevel()));
        var origin = dev.sablelife.module.ModuleSummonedWeapon.formation(core, summon).getFirst();
        try (var targets = targets(h, core, origin)) {
            var engine = core.modules().summonedWeapons;
            try {
                engine.tick(core);
                var frame = engine.frame(summon.id());
                h.assertTrue(frame != null && frame.direction().x < -.99,
                        "Summoned weapon failed to turn towards the exposed target");
            } finally {
                engine.clear();
            }
        }
        h.succeed();
    }
}
