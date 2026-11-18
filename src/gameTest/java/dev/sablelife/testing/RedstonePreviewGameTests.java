package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import dev.sablelife.heart.*;
import dev.sablelife.client.ShockwaveMath;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.*;
import org.joml.Matrix4f;
import io.netty.buffer.Unpooled;
import java.util.*;
@GameTestHolder("sablelife_redstone_preview_tests") @PrefixGameTestTemplate(false)
public final class RedstonePreviewGameTests {
 private static HeartBlockEntity fixture(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,40,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.north(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.north()),new BoundingBox3i(p.getX(),p.getY(),p.getZ()-1,p.getX(),p.getY(),p.getZ()));var c=(HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());var hover=c.modules().add("hover_anchor");hover.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));return c;}
 @GameTest(template="combat",batch="redstone_disabled_preview")
 public static void CopiedDisabledRedstoneRetainsFacePreviewAfterRemarkAndMissingMarksDrawNothing(GameTestHelper h)throws Exception{
  var c=fixture(h);var m=c.modules().add("wireless_redstone");m.edit("redstoneFaces",0,"");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));m.edit("enabled",0,"");var p=TestActors.creative(h.getLevel());p.setPos(m.anchors().getFirst().world(c,false));int[] dust={0},number={0},other={0},actionBar={0};
  p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(p.getGameProfile(),false)){
   @Override public void send(Packet<?> packet){
    if(packet instanceof ClientboundCustomPayloadPacket cp&&cp.payload() instanceof MarkOutline outline){
     if(outline.kind()==MarkOutline.Kind.DETAIL&&outline.color()==0x33FF66)dust[0]+=outline.endpoints().size();
     else if(outline.kind()==MarkOutline.Kind.MARK&&outline.color()==MarkOutline.MARK_COLOR)number[0]+=outline.endpoints().size();
     else other[0]++;
    }
    else if(packet instanceof net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket)other[0]++;
    else if(packet instanceof ClientboundSetActionBarTextPacket)actionBar[0]++;
   }
  };
  var draw=MarkingSessions.class.getDeclaredMethod("previewModule",ServerPlayer.class,HeartBlockEntity.class,VirtualModule.class);draw.setAccessible(true);
  draw.invoke(null,p,c,m);h.assertTrue(dust[0]==102&&number[0]==6&&other[0]==0,"Disabled six-face preview lost stripes or number 1");
  m.edit("enabled",1,"");dust[0]=number[0]=0;draw.invoke(null,p,c,m);h.assertTrue(dust[0]==102&&number[0]==6&&other[0]==0,"Enabled preview differs");
  m.edit("enabled",0,"");var data=c.copyConfiguration();h.assertTrue(c.pasteConfiguration(p,data),"Copier paste failed");var copied=c.modules().all().stream().filter(x->x.type().equals("wireless_redstone")).findFirst().orElseThrow();
  h.assertTrue(!copied.enabled()&&copied.anchors().isEmpty(),"Copy changed enabled state or retained source mark");dust[0]=number[0]=0;draw.invoke(null,p,c,copied);h.assertTrue(dust[0]==0&&number[0]==0&&other[0]==0,"Unmarked copied module drew or crashed");
  copied.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));copied.edit("redstoneFaces",1,"");draw.invoke(null,p,c,copied);h.assertTrue(dust[0]==12&&number[0]==6&&other[0]==0,"Remarked copied module did not show numbered surface");
  copied.clearMarks();copied.edit("enabled",1,"");dust[0]=0;draw.invoke(null,p,c,copied);h.assertTrue(dust[0]==0&&other[0]==0,"Enabled unmarked redstone drew or crashed");
  h.assertTrue(actionBar[0]==0,"Wireless redstone preview sent an action-bar message");
  h.succeed();
 }

 @GameTest(template="combat",batch="channel_output_faces")
 public static void ChannelPublishesNineFacesAndReloadClearsRemovedOutput(GameTestHelper h) {
  var core=fixture(h);
  var root=core.getBlockPos();
  var player=TestActors.creative(h.getLevel());
  player.setPos(ModuleAnchor.create(core,root,Direction.EAST).world(core,false));
  int index=core.wireless().add();
  var channel=core.wireless().channel(index);
  channel.set(6,3);
  channel.setOutputFace(0,ModuleAnchor.create(core,root,Direction.EAST));
  for (Direction direction : List.of(Direction.WEST,Direction.UP,Direction.DOWN,Direction.SOUTH))
   channel.setOutputFace(channel.outputFaces().size(),ModuleAnchor.create(core,root,direction));
  for (Direction direction : List.of(Direction.NORTH,Direction.EAST,Direction.WEST,Direction.UP))
   channel.setOutputFace(channel.outputFaces().size(),ModuleAnchor.create(core,root.north(),direction));
  h.assertTrue(channel.outputFaces().size()==9,"Ninth face was not accepted");
  h.assertTrue(channel.renameOutputFace(8,"尾灯"),"Name rejected");
  var remark=ModuleAnchor.create(core,root.north(),Direction.DOWN);
  h.assertTrue(channel.setOutputFace(8,remark)&&channel.outputFaceName(8).equals("尾灯"),"Remark lost the name");
  h.assertTrue(!channel.renameOutputFace(8,"x".repeat(41)),"Oversized name was accepted");
  var data=core.wireless().save(h.getLevel().registryAccess());
  core.wireless().load(data,h.getLevel().registryAccess());
  channel=core.wireless().channel(index);
  h.assertTrue(channel.outputFaces().size()==9,"Faces did not round trip through world NBT");
  h.assertTrue(channel.outputFaceName(8).equals("尾灯")&&channel.outputFaceName(0).isEmpty(),
      "Renamed or legacy output face did not round trip");
  core.tryActivate(player);
  core.wireless().programOutputs("channel_face_test",Map.of(index,11));
  var faces=new HashMap<ModuleSignals.Face,Integer>();
  core.wireless().collectOutputFaces(faces);
  h.assertTrue(faces.size()==9&&faces.values().stream().allMatch(power->power==11),"Channel strength did not reach all nine faces");
  core.modules().tick(core);
  for (var face : faces.keySet())
   h.assertTrue(ModuleSignals.signal(h.getLevel(),face.pos(),face.outward().getOpposite())==11,
       "The channel output was not visible to the redstone signal hook");


  var menu=new HeartWirelessMenu(1,player.getInventory(),core,index);
  int revision=core.wireless().faceRevision();
  var request=new ModuleNetworking.Action(1,revision,new UUID(0,0),"wire_face","remove",8,"");
  var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
  try {
   ModuleNetworking.Action.CODEC.encode(buffer,request);
   var decoded=ModuleNetworking.Action.CODEC.decode(buffer);
   h.assertTrue(decoded.equals(request),"Face operation did not round trip");
   var rename=new ModuleNetworking.Action(1,revision,new UUID(0,0),"wire_face","rename",8,"😀".repeat(40));
   ModuleNetworking.Action.CODEC.encode(buffer,rename);
   var decodedName=ModuleNetworking.Action.CODEC.decode(buffer);
   h.assertTrue(decodedName.equals(rename),"Maximum-length Unicode output name did not round trip");
   h.assertTrue(core.resetForCreative(player),"Could not release life for editing");
   h.assertTrue(menu.outputAction(player,decodedName.revision(),decodedName.key(),decodedName.value(),decodedName.text())
       &&channel.outputFaceName(8).equals(rename.text()),"Renaming through the menu failed");
   h.assertTrue(menu.outputAction(player,decoded.revision(),decoded.key(),decoded.value()),"Ninth face could not be removed");
   h.assertTrue(!menu.outputAction(player,revision,"remove",0),"Stale request removed another face");
  } finally { buffer.release(); }
  h.assertTrue(channel.outputFaces().size()==8,"Removal did not affect exactly one face");
  faces.clear();
  core.wireless().master(false);
  core.wireless().collectOutputFaces(faces);
  h.assertTrue(faces.isEmpty(),"Master off left active channel faces");
  h.succeed();
 }
 @GameTest(template="combat",batch="redstone_output_faces")
 public static void OutputFacesShareSignalAndRetainLegacyMark(GameTestHelper h) {
  var core = fixture(h);
  var module = core.modules().add("wireless_redstone");
  var root = core.getBlockPos();
  var first = ModuleAnchor.create(core, root, Direction.EAST);
  module.anchors(List.of(first));
  module.edit("trigger", 0, "");
  module.edit("redstonePower", 9, "");
  module.edit("redstoneFaces", 1, "");
  var old = core.modules().save();
  core.modules().load(old);
  module = core.modules().find(module.id());
  h.assertTrue(module.anchors().equals(List.of(first)), "Legacy first mark did not survive loading");

  var player = TestActors.creative(h.getLevel());
  player.setPos(first.world(core, false));
  var outlines = new ArrayList<MarkOutline>();
  player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(),
    new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
   @Override public void send(Packet<?> packet) {
    if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof MarkOutline outline)
     outlines.add(outline);
   }
  };
  var menu = new HeartDashboardMenu(1, player.getInventory(), core);
  int revision = core.modules().revision();
  var request = new ModuleNetworking.Action(1, revision, module.id(), "mark", ModuleRedstone.OUTPUT_FACE, 1, "");
  var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
  try {
   ModuleNetworking.Action.CODEC.encode(buffer, request);
   var decoded = ModuleNetworking.Action.CODEC.decode(buffer);
   h.assertTrue(decoded.equals(request), "Output face action changed in transit");
   h.assertTrue(menu.action(player, decoded.revision(), decoded.module(), decoded.operation(), decoded.key(),
     decoded.value(), decoded.text()), "Dashboard refused valid add request");
  } finally {
   buffer.release();
  }
  h.assertTrue(!MarkingSessions.select(player, root, Direction.EAST), "Duplicate face was accepted");
  h.assertTrue(MarkingSessions.select(player, root.north(), Direction.WEST), "Second output could not be marked");
  var second = ModuleAnchor.create(core, root.north(), Direction.WEST);
  h.assertTrue(module.anchors().equals(List.of(first, second)), "Adding changed the first face");
  h.assertTrue(!menu.action(player, revision, module.id(), "face_remove", ModuleRedstone.OUTPUT_FACE, 1, ""),
    "Stale remove request changed the module");
  revision = core.modules().revision();
  for (double index : new double[]{-1, 1.5, 8, Double.NaN})
   h.assertTrue(!menu.action(player, revision, module.id(), "mark", ModuleRedstone.OUTPUT_FACE, index, ""),
     "Invalid output index was accepted: " + index);
  h.assertTrue(menu.action(player, revision, module.id(), "preview", ModuleRedstone.OUTPUT_FACE, 1, ""),
    "Second output could not be previewed");
  h.assertTrue(outlines.size() == 2 && outlines.getLast().kind() == MarkOutline.Kind.MARK
    && outlines.getLast().endpoints().size() == 10, "Second face did not draw the numeral 2");
  var middle = outlines.getLast().endpoints().stream().reduce(Vec3.ZERO, Vec3::add).scale(.1);
  h.assertTrue(middle.distanceTo(second.world(core, true)) < .08, "Digit 2 was drawn at another face");

  var saved = core.modules().save();
  core.modules().load(saved);
  module = core.modules().find(module.id());
  h.assertTrue(module.anchors().equals(List.of(first, second)) && module.value("redstonePower") == 9,
    "Saving and loading lost the outputs or the shared power");
  core.tryActivate(player);
  var redstone = new ModuleRedstone();
  var faces = new HashMap<ModuleSignals.Face, Integer>();
  var firstOutput = new ModuleSignals.Face(root.east(), Direction.WEST);
  var secondOutput = new ModuleSignals.Face(root.north().west(), Direction.EAST);
  try {
   redstone.tick(core, faces, Set.of());
   h.assertTrue(faces.getOrDefault(firstOutput, 0) == 9 && faces.getOrDefault(secondOutput, 0) == 9,
     "Both faces must publish the configured strength: " + faces);
   AnchorTransfers.replaced(h.getLevel(), root.north());
   faces.clear();
   redstone.tick(core, faces, Set.of());
   h.assertTrue(faces.getOrDefault(firstOutput, 0) == 9 && !faces.containsKey(secondOutput),
     "Damaging one face stopped or redirected the other");
   var loaded = VirtualModule.load(module.save());
   h.assertTrue(loaded.redstoneFaceInvalid(1) && !loaded.redstoneFaceInvalid(0), "Invalidation lost its face on reload");
  } finally {
   redstone.clear();
   core.resetForCreative(player);
  }
  h.assertTrue(menu.action(player, core.modules().revision(), module.id(), "face_remove", ModuleRedstone.OUTPUT_FACE, 0, ""),
    "Could not remove the first output");
  h.assertTrue(module.anchors().equals(List.of(second)) && module.redstoneFaceInvalid(0), "Removing face 1 misnumbered its successor");
  h.succeed();
 }

 @GameTest(template="combat", batch="redstone_output_face_limit")
 public static void WirelessRedstoneAccepts64FacesAndRetainsInvalidationAfterRemoval(GameTestHelper h) {
  var limit = (net.neoforged.neoforge.common.ModConfigSpec.IntValue) dev.sablelife.SableLifeServerConfig.SPEC.getValues().get("maximumRedstoneOutputFaces");
  int previousLimit = limit.get(); limit.set(64);
  try {
  var level = h.getLevel();
  var origin = h.absolutePos(new BlockPos(3, 40, 13));
  var blocks = new ArrayList<BlockPos>();
  for (int i = 0; i < 11; i++) {
   var pos = origin.north(i);
   level.setBlockAndUpdate(pos, i == 0 ? SableLife.HEART.get().defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState());
   blocks.add(pos);
  }
  var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, blocks,
    new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ() - 10, origin.getX(), origin.getY(), origin.getZ()));
  var core = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
  var root = core.getBlockPos();
  var module = core.modules().add("wireless_redstone");
  var anchors = new ArrayList<ModuleAnchor>();
  for (int i = 0; i < blocks.size(); i++) {
   for (var direction : Direction.values()) anchors.add(ModuleAnchor.create(core, root.north(i), direction));
  }
  var initial = List.copyOf(anchors.subList(0, 8));
  module.anchors(initial);
  module.edit("trigger", 0, "");
  module.edit("redstonePower", 9, "");
  module.edit("redstoneFaces", 1, "");
  core.modules().load(core.modules().save());
  module = core.modules().find(module.id());
  h.assertTrue(module.anchors().equals(initial), "Eight existing faces changed on read");

  var player = TestActors.creative(level);
  player.setPos(anchors.getFirst().world(core, false));
  var outlines = new ArrayList<MarkOutline>();
  player.connection = new ServerGamePacketListenerImpl(level.getServer(),
    new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
   @Override public void send(Packet<?> packet) {
    if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof MarkOutline outline)
     outlines.add(outline);
   }
  };
  var menu = new HeartDashboardMenu(1, player.getInventory(), core);
  module.anchors(anchors.subList(0, 63));
  var last = anchors.get(63);
  int revision = core.modules().revision();
  var request = new ModuleNetworking.Action(1, revision, module.id(), "mark", ModuleRedstone.OUTPUT_FACE, 63, "");
  var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
  try {
   ModuleNetworking.Action.CODEC.encode(buffer, request);
   var decoded = ModuleNetworking.Action.CODEC.decode(buffer);
   h.assertTrue(decoded.equals(request) && menu.action(player, decoded.revision(), decoded.module(), decoded.operation(),
     decoded.key(), decoded.value(), decoded.text()), "Dashboard refused the encoded 64th face request");
  } finally {
   buffer.release();
  }
  h.assertTrue(MarkingSessions.select(player, last.position(body), last.face()), "Could not mark face 64");
  h.assertTrue(module.anchors().equals(anchors.subList(0, 64)), "Marking face 64 changed the preceding markers");
  limit.set(16);
  revision = core.modules().revision();
  h.assertTrue(!menu.action(player, revision, module.id(), "mark", ModuleRedstone.OUTPUT_FACE, 64, ""), "Accepted face 65");
  h.assertTrue(menu.action(player, revision, module.id(), "preview", ModuleRedstone.OUTPUT_FACE, 63, ""), "Could not preview face 64");
  h.assertTrue(outlines.stream().anyMatch(p -> p.kind() == MarkOutline.Kind.MARK && p.endpoints().size() >= 14),
    "Preview did not draw a two-digit marker");

  core.tryActivate(player);
  var redstone = new ModuleRedstone();
  var faces = new HashMap<ModuleSignals.Face, Integer>();
  try {
   redstone.tick(core, faces, Set.of());
   h.assertTrue(faces.size() == 64 && faces.values().stream().allMatch(power -> power == 9), "Expected 64 powered faces");
   AnchorTransfers.replaced(level, root.north(5));
   AnchorTransfers.replaced(level, last.position(body));
   faces.clear();
   redstone.tick(core, faces, Set.of());
   h.assertTrue(faces.size() == 54, "Block replacement did not clear exactly the ten affected outputs");
   module = VirtualModule.load(module.save());
   h.assertTrue(!module.redstoneFaceInvalid(0) && module.redstoneFaceInvalid(31) && module.redstoneFaceInvalid(32)
     && !module.redstoneFaceInvalid(59) && module.redstoneFaceInvalid(63), "High face bits changed on reload");
  } finally {
   redstone.clear();
   core.resetForCreative(player);
  }
  var id = module.id();
  h.assertTrue(menu.action(player, core.modules().revision(), id, "face_remove", ModuleRedstone.OUTPUT_FACE, 32, ""),
    "Could not remove face 33");
  var shifted = VirtualModule.load(core.modules().find(id).save());
  h.assertTrue(shifted.redstoneFaceCount() == 63 && shifted.redstoneFaceInvalid(31) && !shifted.redstoneFaceInvalid(35)
    && shifted.redstoneFaceInvalid(62), "Removing face 33 shifted the wrong invalidation flags");
  h.succeed();
  } finally { limit.set(previousLimit); }
 }

 @GameTest(template="combat",batch="alignment_face_diagnostic")
 public static void AlignmentLeftCorrectionDecreasesTowardFrontOnActualBlockFaces(GameTestHelper h){
  var c=fixture(h);var m=c.modules().add("navigation");var anchor=ModuleAnchor.create(c,c.getBlockPos(),Direction.NORTH);m.anchors(List.of(anchor));m.edit("target",1,"");m.edit("lineOfSight",1,"");m.edit("deadZone",0,"");c.tryActivate(TestActors.creative(h.getLevel()));var center=anchor.world(c,false);var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);h.getLevel().addFreshEntity(cow);var control=new TargetNavigation();int previous=16;
  try{for(int angle:new int[]{90,60,30,10,0}){
   double rad=Math.toRadians(angle);cow.setPos(center.add(-8*Math.sin(rad),-cow.getBbHeight()/2.,-8*Math.cos(rad)));var signals=new HashMap<ModuleSignals.Face,Integer>();control.tick(h.getLevel(),c,m,signals);ModuleSignals.updateFaces(c,signals);
   int left=h.getLevel().getSignal(c.getBlockPos(),Direction.EAST),front=h.getLevel().getSignal(c.getBlockPos(),Direction.SOUTH);
   h.assertTrue(left<=previous&&left==control.left(m.id())&&control.right(m.id())==0,"Actual left face is not monotonic at "+angle+": "+left);previous=left;
   System.out.println("Alignment angle="+angle+" left="+left+" front="+front+" leftNeighbor="+h.getLevel().getBestNeighborSignal(c.getBlockPos().west()));
   if(angle==0)h.assertTrue(left==0&&front==15,"Aligned left correction/front bearing were confused");
  }h.succeed();}finally{cow.discard();ModuleSignals.clear(c);}
 }

 private static VirtualModule navigation(HeartBlockEntity c){var m=c.modules().add("navigation");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.NORTH)));m.edit("target",1,"");m.edit("lineOfSight",1,"");m.edit("deadZone",0,"");m.edit("pitchDeadZone",0,"");for(int i=0;i<TargetNavigation.DIRECTIONS.size();i++)m.edit("navigation"+TargetNavigation.DIRECTIONS.get(i)+"Channel",i+1,"");return m;}
 private static void channels(HeartBlockEntity c){for(int i=0;i<6;i++){int n=c.wireless().add();c.wireless().channel(n).set(0,1);c.wireless().channel(n).set(6,3);}}
 @GameTest(template="combat",batch="alignment_channels")
 public static void DirectionChannelsPreserveAnalogStrengthReverseAndFeedSignalTurning(GameTestHelper h){
  var c=fixture(h);channels(c);var m=navigation(c);var turn=c.modules().add("signal_turn");turn.anchors(m.anchors());for(int i=0;i<4;i++)turn.edit(ModuleSignalTurn.DIRECTIONS.get(i)+"Channel",i+1,"");var player=TestActors.creative(h.getLevel());c.tryActivate(player);var center=m.anchors().getFirst().world(c,false);var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);h.getLevel().addFreshEntity(cow);int previous=16;
  try{for(int angle:new int[]{90,60,30,10,0}){double rad=Math.toRadians(angle);cow.setPos(center.add(-8*Math.sin(rad),-cow.getBbHeight()/2.,-8*Math.cos(rad)));c.modules().events().tick(c);int power=c.wireless().output(0);h.assertTrue(power<=previous&&power==c.modules().events().navigation().left(m.id())&&ModuleSignalTurn.input(c,turn,"turnLeft")==power,"Analog left strength changed at "+angle);previous=power;if(angle==30)h.assertTrue(power>=4&&power<=6,"Intermediate signal lost analog strength: "+power);}
   h.assertTrue(c.wireless().output(0)==0&&c.wireless().output(4)==15,"Aligned correction/bearing mixed up");
   cow.setPos(center.add(-6,4,-6));c.modules().events().tick(c);int left=c.wireless().output(0),up=c.wireless().output(2),forward=c.wireless().output(4);h.assertTrue(left>0&&up>0&&ModuleSignalTurn.input(c,turn,"turnUp")==up,"Vertical analog channel missing");m.edit("navigationInvert",1,"");c.modules().events().tick(c);h.assertTrue(c.wireless().output(0)==0&&c.wireless().output(1)==left&&c.wireless().output(2)==0&&c.wireless().output(3)==up&&c.wireless().output(4)==0&&c.wireless().output(5)==forward,"Reverse did not swap all channel directions");
   cow.discard();c.modules().events().tick(c);for(int n=0;n<6;n++)h.assertTrue(c.wireless().output(n)==0,"Target loss retained direction output");h.succeed();
  }finally{cow.discard();c.modules().events().clear();}
 }
 @GameTest(template="combat",batch="alignment_channel_cleanup")
 public static void SharedChannelsKeepOtherSourcesAndGateOffMasterAndResetClearTheirOwnOutput(GameTestHelper h){
  var c=fixture(h);channels(c);var a=navigation(c);var b=navigation(c);b.edit("navigationLeftChannel",2,"");var p=TestActors.creative(h.getLevel());c.tryActivate(p);var center=a.anchors().getFirst().world(c,false);var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(center.add(-6,0,-6));h.getLevel().addFreshEntity(cow);
  try{c.modules().events().tick(c);h.assertTrue(c.wireless().output(0)>0&&c.wireless().output(1)>0,"Two modules did not publish independently");c.wireless().programOutputs("other",Map.of(0,3));a.edit("enabled",0,"");c.modules().events().tick(c);h.assertTrue(c.wireless().output(0)==3&&c.wireless().output(1)>0,"Disabling one source cleared others");b.edit("trigger",2,"");b.edit("channel",6,"");c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==0,"Trigger end left output");b.edit("trigger",0,"");c.modules().events().tick(c);c.wireless().master(false);h.assertTrue(c.wireless().output(0)==0&&c.wireless().output(1)==0,"Master off retained signal");c.wireless().master(true);c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)>0,"Master on did not resume");c.resetForCreative(p);for(int n=0;n<6;n++)h.assertTrue(c.wireless().output(n)==0,"Life release left output");h.succeed();}finally{cow.discard();c.modules().events().clear();}
 }
 @GameTest(template="combat",batch="alignment_channel_settings")
 public static void DirectionChannelDefaultsSavePresetIntegerStepsAndDeletedReferencesStaySafe(GameTestHelper h){
  var c=fixture(h);var m=navigation(c);for(String direction:TargetNavigation.DIRECTIONS)h.assertTrue(new VirtualModule("navigation").value("navigation"+direction+"Channel")==0,"Old config unexpectedly bound a channel");
  for(var saved:List.of(VirtualModule.load(m.save()),ModulePreset.create(m.save())))for(int n=0;n<6;n++)h.assertTrue(saved.value("navigation"+TargetNavigation.DIRECTIONS.get(n)+"Channel")==n+1,"Save/preset lost direction channel");
  channels(c);c.wireless().remove(1);h.assertTrue(m.value("navigationLeftChannel")==1&&m.value("navigationRightChannel")==0&&m.value("navigationUpChannel")==2&&m.value("navigationBackwardChannel")==5,"Channel deletion redirected reference");m.edit("navigationLeftChannel",2.7,"");h.assertTrue(m.value("navigationLeftChannel")==3&&dev.sablelife.client.NumericWheelSteps.module("navigation","navigationLeftChannel",1,0)==1,"Direction channel accepts fraction or wrong wheel step");h.succeed();
 }

 @GameTest(template="combat",batch="alignment_turn_integration",timeoutTicks=35)
 public static void AlignmentChannelsActuallySteerTheBodyTowardTheTarget(GameTestHelper h){
  var c=fixture(h);c.modules().all().getFirst().edit("enabled",0,"");channels(c);var m=navigation(c);m.edit("verticalTracking",0,"");var turn=c.modules().add("signal_turn");turn.anchors(m.anchors());turn.edit("turnLeftChannel",1,"");turn.edit("turnRightChannel",2,"");turn.edit("turnCollision",0,"");var center=m.anchors().getFirst().world(c,false);var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(center.add(-8,0,-8));h.getLevel().addFreshEntity(cow);c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);int initial=c.wireless().output(0);h.assertTrue(initial>0,"Alignment did not seed steering channel");
  h.runAfterDelay(15,()->{var front=TargetNavigation.worldFront(c,m);h.assertTrue(front.x<-.15&&c.wireless().output(0)<initial&&ModuleSignalTurn.input(c,turn,"turnLeft")==c.wireless().output(0),"Actual body did not steer or correction did not reduce: "+front+" / "+initial+" -> "+c.wireless().output(0));cow.discard();c.modules().events().clear();h.succeed();});
 }
}
