package dev.sablelife.testing;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.attack.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class SummonedWeaponGameTests {
 private static void cleanup(HeartBlockEntity c){c.modules().summonedWeapons.clear();var l=(net.minecraft.server.level.ServerLevel)c.getLevel();for(var e:l.getEntitiesOfClass(Entity.class,new AABB(ModuleArena.corePosition(c),ModuleArena.corePosition(c)).inflate(256)))if(e.getPersistentData().hasUUID("SableLifeAttackOwner")&&e.getPersistentData().getUUID("SableLifeAttackOwner").equals(c.creatureId()))e.discard();}

 private static VirtualModule summon(HeartBlockEntity c,VirtualModule attack){var m=c.modules().add("summoned_weapon");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.EAST)));m.projectileItem(new ItemStack(Items.DIAMOND_SWORD),c.getLevel().registryAccess());m.summonAttack(attack.id());m.edit("startDistance",3,"");m.edit("summonMuzzleZ",2,"");m.edit("summonDuration",40,"");return m;}
 private static VirtualModule attack(HeartBlockEntity c,String type){var m=c.modules().add(type);m.edit("enabled",0,"");m.edit("startDistance",32,"");m.edit("shotAim",1,"");m.edit("laserAim",1,"");m.edit("chargeTime",0,"");m.edit("beamHits",3,"");m.edit("hitInterval",1,"");m.edit("interval",100,"");m.edit("damage",2,"");m.edit("speed",4,"");m.edit("flameSpeed",4,"");return m;}
 private static net.minecraft.world.entity.animal.Cow cow(GameTestHelper h,Vec3 center){var e=EntityType.COW.create(h.getLevel());e.setNoAi(true);e.setNoGravity(true);e.setPos(center.subtract(0,e.getBbHeight()/2,0));h.getLevel().addFreshEntity(e);return e;}
 private static void route(GameTestHelper h,String type){var c=SlashGuardGameTests.core(h);var anchor=c.modules().add("hover_anchor");anchor.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));anchor.edit("hoverRotation",1,"");var a=attack(c,type);var m=summon(c,a);var counter=c.modules().add("hit_counter");counter.edit("hitThreshold",100,"");counter.hitSource(a.id(),1);c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);var frame=c.modules().summonedWeapons.frame(m.id());h.assertTrue(frame!=null&&ModuleCombat.direction(c,m).distanceTo(frame.direction())<1e-9,"Summon did not start from its own aim");var victim=cow(h,frame.muzzle().add(frame.direction().scale(3)));
  h.runAfterDelay(7,()->{h.assertTrue(victim.getHealth()<10,"Summoned "+type+" did not damage target");h.assertTrue(c.modules().hitCounter.snapshot(counter.id()).getInt("Count")>0,"Summoned "+type+" lost original source hit credit");h.assertTrue(a.value("startDistance")==32&&!a.enabled()&&a.anchors().isEmpty(),"Template authoring mutated");cleanup(c);victim.discard();h.succeed();});}
 @GameTest(template="combat",batch="summon_gunuses",timeoutTicks=30) public static void GunUsesSummonMuzzleAndCreditsDisabledUnmarkedTemplate(GameTestHelper h){route(h,"gun");}
 @GameTest(template="combat",batch="summon_arrowuses",timeoutTicks=30) public static void ArrowUsesSummonMuzzleAndCreditsDisabledUnmarkedTemplate(GameTestHelper h){route(h,"arrow");}
 @GameTest(template="combat",batch="summon_flameuses",timeoutTicks=30) public static void FlameUsesSummonMuzzleAndCreditsDisabledUnmarkedTemplate(GameTestHelper h){route(h,"flame");}
 @GameTest(template="combat",batch="summon_laseruses",timeoutTicks=30) public static void LaserUsesSummonMuzzleAndCreditsDisabledUnmarkedTemplate(GameTestHelper h){route(h,"laser");}
 @GameTest(template="combat",batch="summon_modelrotation") public static void ModelRotationDoesNotRotateMuzzleAndAimTurnsStayFinite(GameTestHelper h){var m=new VirtualModule("summoned_weapon");m.edit("summonMuzzleZ",2,"");m.edit("displayRotZ",90,"");var f=ModuleSummonedWeapon.frame(m,Vec3.ZERO,new Vec3(1,0,0));h.assertTrue(f.muzzle().distanceToSqr(new Vec3(2,0,0))<1e-9&&f.direction().distanceToSqr(new Vec3(1,0,0))<1e-9,"Model rotation changed muzzle or fire direction");var opposite=ModuleSummonedWeapon.turn(new Vec3(0,0,1),new Vec3(0,0,-1),15);h.assertTrue(Double.isFinite(opposite.x+opposite.y+opposite.z)&&Math.abs(Math.acos(opposite.z)-Math.toRadians(15))<1e-6,"Opposite aim snapped or became nonfinite");m.edit("summonYaw",90,"");h.assertTrue(ModuleSummonedWeapon.frame(m,Vec3.ZERO,new Vec3(0,0,1)).direction().distanceToSqr(new Vec3(1,0,0))<1e-9,"Aim yaw offset ignored");h.succeed();}
 @GameTest(template="combat",batch="summon_expirydoes",timeoutTicks=35) public static void ExpiryDoesNotRespawnHeldTriggerAndRetriggerCreatesFreshSummon(GameTestHelper h){var c=SlashGuardGameTests.core(h);var a=attack(c,"gun");var m=summon(c,a);m.edit("summonDuration",4,"");c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);var position=c.modules().summonedWeapons.frame(m.id()).item();h.assertTrue(!h.getLevel().getEntitiesOfClass(Display.ItemDisplay.class,new AABB(position,position).inflate(1)).isEmpty(),"Native display missing");
  h.runAfterDelay(6,()->{h.assertTrue(c.modules().summonedWeapons.snapshot(m.id(),c).getBoolean("Finished")&&h.getLevel().getEntitiesOfClass(Display.ItemDisplay.class,new AABB(position,position).inflate(1)).isEmpty(),"Expired summon respawned or left display");m.edit("enabled",0,"");});
  h.runAfterDelay(8,()->m.edit("enabled",1,""));h.runAfterDelay(10,()->{h.assertTrue(c.modules().summonedWeapons.snapshot(m.id(),c).getBoolean("Active"),"Fresh trigger did not rearm");c.forceDeath();h.assertTrue(h.getLevel().getEntitiesOfClass(Display.ItemDisplay.class,new AABB(position,position).inflate(1)).isEmpty(),"Death left native display");h.succeed();});}
 @GameTest(template="combat",batch="summon_originalgun") public static void OriginalGunAndTwoSummonsFireIndependently(GameTestHelper h){var c=SlashGuardGameTests.core(h);var a=attack(c,"gun");a.edit("enabled",1,"");a.edit("shotAim",0,"");a.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.NORTH)));a.edit("startDistance",1,"");var one=summon(c,a);var two=summon(c,a);two.edit("displayZ",4,"");c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);var f1=c.modules().summonedWeapons.frame(one.id());var f2=c.modules().summonedWeapons.frame(two.id());var all=h.getLevel().getEntitiesOfClass(ModuleShot.class,new AABB(ModuleArena.corePosition(c),ModuleArena.corePosition(c)).inflate(12)).stream().filter(e->e.getPersistentData().hasUUID("SableLifeAttackOwner")&&e.getPersistentData().getUUID("SableLifeAttackOwner").equals(c.creatureId())).toList();h.assertTrue(all.size()==3&&all.stream().anyMatch(s->s.position().distanceToSqr(f1.muzzle())<1e-8)&&all.stream().anyMatch(s->s.position().distanceToSqr(f2.muzzle())<1e-8),"Summons shared cooldown or moved original muzzle: "+all.stream().map(Entity::position).toList()+" expected "+f1.muzzle()+" / "+f2.muzzle());all.forEach(Entity::discard);c.modules().summonedWeapons.clear();h.succeed();}
 @GameTest(template="combat",batch="summon_worldposition",timeoutTicks=30) public static void WorldPositionStaysFixedAndFollowRecomputesPosition(GameTestHelper h){var c=SlashGuardGameTests.core(h);var a=attack(c,"gun");var follow=summon(c,a);var fixed=summon(c,a);fixed.edit("summonPosition",1,"");c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);var before=c.modules().summonedWeapons.frame(follow.id()).item();follow.edit("displayY",5,"");fixed.edit("displayY",5,"");h.runAfterDelay(3,()->{var f=c.modules().summonedWeapons.frame(follow.id());var w=c.modules().summonedWeapons.frame(fixed.id());h.assertTrue(f.item().distanceToSqr(ModuleSummonedWeapon.position(c,follow))<1e-8&&w.item().distanceToSqr(before)<1e-8&&Math.abs(f.item().y-w.item().y)>4,"World/follow placement not independent");var saved=follow.save();h.assertTrue(VirtualModule.load(saved).summonAttackId().equals(a.id())&&ModulePreset.create(saved).summonAttackId()==null,"Binding lost on save or leaked into portable preset");c.modules().summonedWeapons.clear();h.succeed();});}

 @GameTest(template="combat", batch="summon_line_of_sight", timeoutTicks=30)
 public static void SummonedWeaponDropsCoveredLockAndKeepsSharedSearchCadence(GameTestHelper h) {
    var core = SlashGuardGameTests.core(h);
    var pin = core.modules().add("hover_anchor");
    pin.anchors(List.of(ModuleAnchor.create(core, core.getBlockPos(), Direction.UP)));
    pin.edit("hoverRotation", 1, "");
    var weapon = summon(core, attack(core, "gun"));
    weapon.edit("summonAim", 1, "");
    weapon.edit("target", 1, "");
    weapon.edit("targetRange", 8, "");
    weapon.edit("summonTurnSpeed", 180, "");
    h.assertTrue(weapon.value("lineOfSight") == 1, "New tracking summon ignored cover by default");
    var saved = weapon.save();
    h.assertTrue(VirtualModule.load(saved).value("lineOfSight") == 1, "Summon sight setting did not persist");
    saved.getCompound("Settings").remove("lineOfSight");
    h.assertTrue(VirtualModule.load(saved).value("lineOfSight") == 0, "Legacy summon acquired a new restriction");

    var origin = ModuleSummonedWeapon.position(core, weapon);
    var hidden = cow(h, origin.add(0, -4, 2));
    var exposed = cow(h, origin.add(0, 0, 6));
    var roof = BlockPos.containing(origin.add(0, -2, 1));
    var wall = BlockPos.containing(origin.add(3, 0, 0));
    h.getLevel().setBlockAndUpdate(roof, Blocks.STONE.defaultBlockState());
    core.tryActivate(TestActors.creative(h.getLevel()));
    core.modules().tick(core);
    var runtime = core.modules().summonedWeapons;
    h.assertTrue(runtime.frame(weapon.id()).direction().z > .999 && runtime.targetQueries(weapon.id()) == 1,
            "Initial tracking chose the nearer target under the roof");

    h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
    exposed.setPos(origin.add(6, -exposed.getBbHeight() / 2, 0));
    h.runAfterDelay(2, () -> {
        h.assertTrue(runtime.frame(weapon.id()).direction().z > .999 && runtime.targetQueries(weapon.id()) == 1,
                "Summon tracked a covered target or searched before the scheduled refresh");
        h.getLevel().removeBlock(wall, false);
    });
    h.runAfterDelay(6, () -> {
        h.assertTrue(runtime.frame(weapon.id()).direction().x > .999 && runtime.targetQueries(weapon.id()) == 2,
                "Summon did not reacquire the exposed target at the scheduled refresh");
        h.assertTrue(weapon.edit("lineOfSight", 0, ""), "Summon sight option could not be disabled");
    });
    h.runAfterDelay(12, () -> {
        try {
            h.assertTrue(runtime.frame(weapon.id()).direction().y < -.8,
                    "Disabling sight did not restore the nearer covered target");
        } finally {
            hidden.discard();
            exposed.discard();
            h.getLevel().removeBlock(roof, false);
            cleanup(core);
        }
        h.succeed();
    });
 }

 @GameTest(template="combat", batch="summon_move_target", timeoutTicks=30)
 public static void SummonedWeaponKeepsItsMoveTargetThroughWindup(GameTestHelper h) {
    var core = SlashGuardGameTests.core(h);
    var pin = core.modules().add("hover_anchor");
    pin.anchors(List.of(ModuleAnchor.create(core, core.getBlockPos(), Direction.UP)));
    pin.edit("hoverRotation", 1, "");
    var weapon = summon(core, attack(core, "gun"));
    weapon.edit("summonPosition", 1, "");
    weapon.edit("summonAim", 1, "");
    weapon.edit("summonTurnSpeed", 180, "");
    weapon.edit("summonWindup", 5, "");
    var origin = ModuleSummonedWeapon.position(core, weapon);
    var moveTarget = cow(h, origin.add(0, 0, 8));
    var other = cow(h, origin.add(3, 0, 0));

    var moves = core.modules().add("move_set").moveSet();
    moves.edit("add", 0, "", core.modules());
    moves.edit("action_add", 0, "0|" + weapon.id(), core.modules());
    moves.edit("dispatchNear", 8, "0", core.modules());
    moves.edit("action_duration", .05, "0|0", core.modules());
    core.tryActivate(TestActors.creative(h.getLevel()));
    core.modules().tick(core);
    var runtime = core.modules().summonedWeapons;
    var first = runtime.frame(weapon.id());
    h.assertTrue(first != null && first.direction().z > .999 && runtime.targetQueries(weapon.id()) == 0,
            "Summon aimed at the closer mob or searched the formation while using the inherited target");

    var wall = BlockPos.containing(origin.add(0, 0, 4));
    h.runAfterDelay(2, () -> {
        h.assertTrue(core.modules().moveTarget(weapon.id()) == null, "Move action stayed active past its window");
        h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
    });
    h.runAfterDelay(8, () -> {
        h.assertTrue(moveTarget.getHealth() == moveTarget.getMaxHealth() && other.getHealth() == other.getMaxHealth()
                && runtime.frame(weapon.id()).direction().z > .999, "Blocked inherited target caused fire or fallback");
        h.getLevel().removeBlock(wall, false);
    });
    h.runAfterDelay(15, () -> {
        try {
            h.assertTrue(moveTarget.getHealth() < moveTarget.getMaxHealth() && other.getHealth() == other.getMaxHealth(),
                    "Summon did not resume against the original Move Set target after cover was removed");
        } finally {
            cleanup(core);
            moveTarget.discard();
            other.discard();
        }
        h.succeed();
    });
 }
}
