package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.HeartModules;
import dev.sablelife.module.ModuleAnchor;
import dev.sablelife.module.ModuleGyroscope;
import dev.sablelife.module.ModuleSignals;
import dev.sablelife.module.VirtualModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.sablelife.testing.KillHealCapacityGameTests.author;
import static dev.sablelife.testing.KillHealCapacityGameTests.fixture;

@GameTestHolder("sablelife_module_capacity_perf_tests")
@PrefixGameTestTemplate(false)
public final class ModuleCapacityPerformanceGameTests {
 @GameTest(template="combat", batch="turn_and_gyro_mspt", timeoutTicks=700)
 public static void CompareGyroscopeWithAndWithoutSimultaneousTurning(GameTestHelper h) {
  var level = h.getLevel();
  var server = level.getServer();
  var pipeline = SubLevelPhysicsSystem.get(level).getPipeline();
  var cores = List.of(fixture(h, 2), fixture(h, 7), fixture(h, 12), fixture(h, 17));
  var turns = new ArrayList<VirtualModule>();
  var bodies = new ArrayList<ServerSubLevel>();
  var positions = new ArrayList<Vector3d>();
  for (var core : cores) {
   var marker = ModuleAnchor.create(core, core.getBlockPos(), Direction.EAST);
   var gyro = core.modules().add("gyroscope");
   gyro.anchors(List.of(marker));
   gyro.gyroDown(ModuleGyroscope.DOWN);
   gyro.edit("gyroSpeed", 40, "");
   var turn = core.modules().add("signal_turn");
   turn.anchors(List.of(marker));
   var body = (ServerSubLevel) marker.body(core);
   int channel = core.wireless().add();
   core.wireless().channel(channel).set(0, 1);
   core.wireless().channel(channel).set(6, 3);
   turn.edit("turnLeftChannel", channel + 1, "");
   core.tryActivate(author(h, core));
   core.wireless().programOutputs("turn_gyro_probe", Map.of(channel, 15));
   turns.add(turn);
   bodies.add(body);
   positions.add(new Vector3d(body.logicalPose().position()));
  }
  boolean[] combined = {false, true, true, false};
  var samples = new ArrayList<Double>();
  h.onEachTick(() -> {
   int phase = (int) h.getTick() / 160;
   int phaseTick = (int) h.getTick() % 160;
   if (phase == combined.length) {
    cores.forEach(core -> core.resetForCreative(author(h, core)));
    h.succeed();
    return;
   }
   if (phaseTick == 1) {
    samples.clear();
    for (var turn : turns) turn.edit("turnWithGyro", combined[phase] ? 1 : 0, "");
   }
   // Reintroduce a tilt in both phases and keep the free structures clear of terrain.
   if (phaseTick % 8 == 0) {
    for (int i = 0; i < bodies.size(); i++) {
     pipeline.resetVelocity(bodies.get(i));
     pipeline.teleport(bodies.get(i), positions.get(i), new Quaterniond().rotateZ(Math.toRadians(40)));
    }
   }
   if (phaseTick >= 60) {
    samples.add(server.getTickTimesNanos()[Math.floorMod(server.getTickCount() - 1, 100)] / 1e6);
   }
   if (phaseTick != 159) return;
   for (var core : cores) {
    var rows = core.modules().snapshot(core, true).getList("Modules", 10);
    h.assertTrue(rows.getCompound(0).getString("GyroState").equals("recovering")
            && rows.getCompound(1).getString("TurnState").equals(combined[phase] ? "turning" : "busy"),
            "Benchmark controller was not in the expected state: " + rows.getCompound(1).getString("TurnState"));
   }
   samples.sort(Double::compare);
   h.assertTrue(samples.get(0) > 0, "Missing server tick measurements");
   System.out.printf(Locale.ROOT,
           "TURN_GYRO_MSPT combined=%s cores=%d samples=%d mean=%.3f p95=%.3f max=%.3f%n",
           combined[phase], cores.size(), samples.size(), samples.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
           samples.get(94), samples.getLast());
  });
 }

 @GameTest(template="combat",batch="wireless_output_faces_mspt",timeoutTicks=1300)
 public static void CompareWirelessRedstoneOutputFaceCounts(GameTestHelper h) {
  var level = h.getLevel();
  var server = level.getServer();
  var cores = List.of(wirelessFixture(h, 2), wirelessFixture(h, 7), wirelessFixture(h, 12), wirelessFixture(h, 17));
  var markers = cores.stream().map(core -> {
   var list = new ArrayList<ModuleAnchor>();
   var origin = core.getBlockPos();
   for (var side : Direction.values()) list.add(ModuleAnchor.create(core, origin, side));
   list.add(ModuleAnchor.create(core, origin.east(), Direction.UP));
   list.add(ModuleAnchor.create(core, origin.east(), Direction.EAST));
   for (int i = 1; i <= 10; i++) {
    for (var side : Direction.values()) list.add(ModuleAnchor.create(core, origin.south(i), side));
   }
   return List.copyOf(list);
  }).toList();
  for (var core : cores) {
   var hover = core.modules().add("hover_anchor");
   hover.anchors(List.of(ModuleAnchor.create(core, core.getBlockPos(), Direction.UP)));
   for (int i = 0; i < 64; i++) {
    var module = core.modules().add("wireless_redstone");
    module.edit("trigger", 0, "");
   }
   core.tryActivate(author(h, core));
   h.assertTrue(core.operational(), "Wireless benchmark core did not activate");
  }
  int[] counts = Boolean.getBoolean("sablelife.wirelessPerf64") ? new int[]{8, 64, 64, 8} : new int[]{1, 8, 8, 1};
  var samples = new ArrayList<Double>();
  h.onEachTick(() -> {
   int tick = (int) h.getTick();
   int phase = tick / 280;
   int phaseTick = tick % 280;
   if (phase >= counts.length) { h.succeed(); return; }
   int faceCount = counts[phase];
   if (phaseTick == 1) {
    samples.clear();
    for (int c = 0; c < cores.size(); c++) {
     var selected = markers.get(c).subList(0, faceCount);
     cores.get(c).modules().all().stream().filter(m -> m.type().equals("wireless_redstone"))
       .forEach(m -> m.anchors(selected));
    }
   }
   if (phaseTick >= 100) samples.add(server.getTickTimesNanos()[Math.floorMod(server.getTickCount() - 1, 100)] / 1e6);
   if (phaseTick != 279) return;
   for (int c = 0; c < cores.size(); c++) {
    var core = cores.get(c);
    h.assertTrue(core.operational() && core.modules().all().size() == 65, "Wireless benchmark lost a core or module");
    for (var anchor : markers.get(c).subList(0, faceCount)) {
     var output = anchor.position(anchor.body(core)).relative(anchor.face());
     h.assertTrue(ModuleSignals.signal(level, output, anchor.face()) == 15, "Wireless benchmark output was inactive");
    }
   }
   samples.sort(Double::compare);
   System.out.printf(Locale.ROOT,"WIRELESS_FACES_MSPT faces=%d cores=4 modules=256 samples=%d mean=%.2f p95=%.2f max=%.2f%n",
     faceCount, samples.size(), samples.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
     samples.get((int) Math.ceil(samples.size() * .95) - 1), samples.getLast());
  });
 }

 private static HeartBlockEntity wirelessFixture(GameTestHelper h, int x) {
  var level = h.getLevel();
  var origin = h.absolutePos(new BlockPos(x, 30, 3));
  var blocks = new ArrayList<BlockPos>();
  blocks.add(origin);
  level.setBlockAndUpdate(origin, SableLife.HEART.get().defaultBlockState());
  blocks.add(origin.east());
  for (int i = 1; i <= 10; i++) blocks.add(origin.south(i));
  for (var pos : blocks.subList(1, blocks.size())) level.setBlockAndUpdate(pos, Blocks.IRON_BLOCK.defaultBlockState());
  var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, blocks,
    new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 1, origin.getY(), origin.getZ() + 10));
  return (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
 }

 @GameTest(template="combat",timeoutTicks=1450)
 public static void CompareActiveModuleCapacity(GameTestHelper h){
  var level=h.getLevel();
  var server=level.getServer();
  for (var floor : BlockPos.betweenClosed(new BlockPos(0,29,0), new BlockPos(20,29,7)))
   level.setBlockAndUpdate(h.absolutePos(floor), Blocks.STONE.defaultBlockState());
  var cores=List.of(fixture(h,2),fixture(h,7),fixture(h,12),fixture(h,17));
  var markers=new ArrayList<ModuleAnchor>();
  var targets=new ArrayList<Cow>();
  var sensors=new ArrayList<net.minecraft.nbt.ListTag>();
  for(var core:cores){
   var facing=ModuleAnchor.create(core,core.getBlockPos().east(),Direction.UP);
   markers.add(facing);
   var point=facing.world(core,false).add(0,1,0);
   var cow=net.minecraft.world.entity.EntityType.COW.create(level);
   cow.setPos(point);cow.setNoAi(true);cow.setNoGravity(true);level.addFreshEntity(cow);
   targets.add(cow);
   var rows=new net.minecraft.nbt.ListTag();
   for(int i=0;i<HeartModules.LIMIT;i++){
    var module=new VirtualModule("sensor");
    module.anchors(List.of(facing));
    module.edit("radius",6,"");
    module.edit("target",1,"");
    module.edit("lineOfSight",1,"");
    rows.add(module.save());
   }
   sensors.add(rows);
   core.tryActivate(author(h,core));
   h.assertTrue(core.operational(),"Benchmark core did not activate");
  }
  int[] counts={256,1024,1024,256};
  var samples=new ArrayList<Double>();
  h.onEachTick(()->{
   for(int c=0;c<cores.size();c++) targets.get(c).setPos(markers.get(c).world(cores.get(c),false).add(0,1,0));
   int tick=(int)h.getTick();
   int phase=tick/320;
   int phaseTick=tick%320;
   if(phase>=counts.length){h.succeed();return;}
   if(phaseTick==1){
    samples.clear();
    for(int c=0;c<cores.size();c++){
     var list=new net.minecraft.nbt.ListTag();
     for(int i=0;i<counts[phase];i++)list.add(sensors.get(c).get(i));
     var saved=new net.minecraft.nbt.CompoundTag();saved.put("Modules",list);
     cores.get(c).modules().load(saved);
    }
   }
   if(phaseTick>=120){
    int previousIndex=Math.floorMod(server.getTickCount()-1,100);
    samples.add(server.getTickTimesNanos()[previousIndex]/1e6);
   }
   if(phaseTick==319){
    for(var core:cores){
     var rows=core.modules().all();
     long detected = rows.stream().filter(module -> core.modules().events().sensorDetected(module.id())).count();
     h.assertTrue(rows.size()==counts[phase]&&detected==counts[phase],
             "Benchmark detected " + detected + "/" + rows.size() + " targets. Core " + core.health().state()
                     + ", first marker " + HeartModules.status(rows.getFirst(),core)
                     + ", triggered=" + HeartModules.triggered(rows.getFirst(),core));
    }
    samples.sort(Double::compare);
    double mean=samples.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    double p95=samples.get((int)Math.ceil(samples.size()*0.95)-1);
    System.out.printf(Locale.ROOT,"MODULE_CAPACITY_MSPT modules=%d cores=%d samples=%d mean=%.2f p95=%.2f max=%.2f%n",counts[phase],cores.size(),samples.size(),mean,p95,samples.getLast());
   }
  });
 }
}
