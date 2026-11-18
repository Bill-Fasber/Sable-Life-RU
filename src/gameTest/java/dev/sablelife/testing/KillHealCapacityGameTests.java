package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.attack.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_combat_tests") @PrefixGameTestTemplate(false)
public final class KillHealCapacityGameTests {
 static HeartBlockEntity fixture(GameTestHelper h,int x){var l=h.getLevel();var p=h.absolutePos(new BlockPos(x,30,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());}
 static net.neoforged.neoforge.common.util.FakePlayer author(GameTestHelper h,HeartBlockEntity c){var p=TestActors.creative(h.getLevel());p.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()));return p;}
 private static void activate(GameTestHelper h,HeartBlockEntity c){c.tryActivate(author(h,c));dev.sablelife.ownership.LoadedCoreIndex.observe(c);c.damage(20);}
 private static ServerPlayer victim(GameTestHelper h){var l=h.getLevel();var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"KillHealVictim");var p=new ServerPlayer(l.getServer(),l,profile,ClientInformation.createDefault());p.connection=new ServerGamePacketListenerImpl(l.getServer(),new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(profile,false)){@Override public void send(Packet<?> packet){}};try{var protection=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");protection.setAccessible(true);protection.setInt(p,0);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}p.setGameMode(GameType.SURVIVAL);p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(5,30,6)));return p;}
 @GameTest(template="combat",batch="kill_heal_direct")
 public static void DirectModuleKillHealsOnlyItsEnabledCoreAndDoesNotReplaceMeleeType(GameTestHelper h){
  var a=fixture(h,2);var b=fixture(h,7);a.editCore(author(h,a),"killHeal",1,"");activate(h,a);activate(h,b);var m=a.modules().add("melee");var target=victim(h);var source=new BodyMeleeSource(h.getLevel(),target.position(),a.creatureName());
  ModuleDamage.hurt(target,source,1,a,m);h.assertTrue(target.getHealth()<20&&target.isAlive()&&a.health().current()==80,"Nonlethal fixture did not take damage or hit healed");ModuleDamage.hurt(target,source,10000,a,m);h.assertTrue(target.getHealth()<=0&&a.health().current()==a.health().maximum()&&b.health().current()==80,"Kill healed wrong core or failed");h.assertTrue(ModuleDamage.named(source,a) instanceof BodyMeleeSource,"Attribution broke Epic Fight melee source type");
  var other=victim(h);ModuleDamage.hurt(other,ModuleDamage.physical(h.getLevel()),10000,b,b.modules().add("melee"));h.assertTrue(b.health().current()==80,"Default-off core healed");h.succeed();
 }
 @GameTest(template="combat",batch="kill_heal_exclusions")
 public static void TotemCanceledDeathMobAndUnrelatedKillDoNotHeal(GameTestHelper h){
  var c=fixture(h,3);c.editCore(author(h,c),"killHeal",1,"");activate(h,c);var m=c.modules().add("melee");var p=victim(h);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.TOTEM_OF_UNDYING));ModuleDamage.hurt(p,ModuleDamage.physical(h.getLevel()),10000,c,m);h.assertTrue(p.isAlive()&&p.getMainHandItem().isEmpty()&&c.health().current()==80,"Totem was not consumed or counted as kill");
  var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setPos(p.position());h.getLevel().addFreshEntity(cow);ModuleDamage.hurt(cow,ModuleDamage.physical(h.getLevel()),10000,c,m);h.assertTrue(c.health().current()==80,"Mob kill healed core");var unrelated=victim(h);ModuleDamage.hurt(unrelated,ModuleDamage.physical(h.getLevel()),10000);h.assertTrue(c.health().current()==80,"Unrelated death healed core");
  var canceled=new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(victim(h),ModuleDamage.named(ModuleDamage.physical(h.getLevel()),c));canceled.setCanceled(true);CreatureKillHealing.died(canceled);h.assertTrue(c.health().current()==80,"Canceled death healed core");h.succeed();
 }
 @GameTest(template="combat",batch="kill_heal_projectile")
 public static void ProjectileIdentityCreditsKillButNameOnlyAndDeadCoreDoNot(GameTestHelper h){
  var c=fixture(h,3);c.editCore(author(h,c),"killHeal",1,"");activate(h,c);var module=c.modules().add("arrow");var arrow=net.minecraft.world.entity.EntityType.ARROW.create(h.getLevel());AttackHitCredit.attach(arrow,c,module);var p=victim(h);ModuleDamage.hurt(p,h.getLevel().damageSources().arrow(arrow,null),10000);h.assertTrue(c.health().current()==100,"Owned projectile did not credit kill");
  c.damage(20);var cosmetic=net.minecraft.world.entity.EntityType.ARROW.create(h.getLevel());ModuleDamage.nameProjectile(cosmetic,c);ModuleDamage.hurt(victim(h),h.getLevel().damageSources().arrow(cosmetic,null),10000);h.assertTrue(c.health().current()==80,"Cosmetic name granted kill credit");c.damage(1000000);ModuleDamage.hurt(victim(h),h.getLevel().damageSources().arrow(arrow,null),10000);h.assertTrue(c.health().current()==0,"Posthumous projectile revived core");h.succeed();
 }
 @GameTest(template="combat",batch="kill_heal_settings")
 public static void KillHealSettingsValidateSaveAndConfigurationCopy(GameTestHelper h){
  var a=fixture(h,2);var b=fixture(h,7);var p=author(h,a);var menu=new HeartSettingsMenu(1,p.getInventory(),a);h.assertTrue(!a.killHeal()&&menu.action(p,"killHeal",1,"")&&menu.snapshot().getBoolean("killHeal"),"Core toggle missing");h.assertTrue(!menu.action(p,"killHeal",2,"")&&!menu.action(p,"killHeal",Double.NaN,""),"Invalid toggle accepted");
  var saved=a.saveWithoutMetadata(h.getLevel().registryAccess());var copy=new HeartBlockEntity(a.getBlockPos(),a.getBlockState());copy.loadWithComponents(saved,h.getLevel().registryAccess());h.assertTrue(copy.killHeal(),"Kill heal setting did not save");p.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),b.getBlockPos().getCenter()));h.assertTrue(b.pasteConfiguration(p,a.copyConfiguration())&&b.killHeal(),"Configuration copy lost toggle");saved.remove("KillHeal");copy.loadWithComponents(saved,h.getLevel().registryAccess());h.assertTrue(!copy.killHeal(),"Legacy save enabled kill healing");p.setGameMode(GameType.SURVIVAL);h.assertTrue(!b.editCore(p,"killHeal",0,"")&&b.killHeal(),"Survival changed kill-heal setting");h.succeed();
 }
 @GameTest(template="combat",batch="expanded_module_capacity")
 public static void ModuleCapacityPreservesLegacyAndLargerSavedLists(GameTestHelper h){
  var legacy=new net.minecraft.nbt.CompoundTag();
  var entries=new net.minecraft.nbt.ListTag();
  for(int i=0;i<128;i++)entries.add(new VirtualModule("item_display").save());
  legacy.put("Modules",entries);

  var core=fixture(h,2);
  var modules=core.modules();
  modules.load(legacy);
  var originalIds=modules.all().stream().map(VirtualModule::id).toList();
  h.assertTrue(originalIds.size()==128,"Legacy module count changed");
  int limit=HeartModules.authoringLimit();
  h.assertTrue(limit==256,"Unexpected default authoring capacity");
  for(int i=128;i<limit-1;i++)h.assertTrue(modules.add("item_display")!=null,"Module add stopped before configured limit");

  var player=author(h,core);
  var menu=new HeartDashboardMenu(1,player.getInventory(),core);
  var firstId=originalIds.getFirst();
  h.assertTrue(menu.snapshot().getInt("ModuleLimit")==limit,"Dashboard received the wrong capacity");
  h.assertTrue(menu.action(player,modules.revision(),firstId,"add","item_display",0,""),"Dashboard rejected the last slot");
  h.assertTrue(menu.snapshot().getList("Modules",10).size()==limit,"Dashboard lost modules at capacity");
  h.assertTrue(!menu.action(player,modules.revision(),firstId,"add","item_display",0,""),"Dashboard exceeded the limit");
  h.assertTrue(!menu.action(player,modules.revision(),firstId,"copy","",0,"")&&modules.addPreset(new VirtualModule("particles").save())==null,"Copy or preset exceeded the limit");

  var saved=modules.save();
  var loaded=new HeartModules();
  loaded.load(saved);
  h.assertTrue(loaded.all().size()==limit&&loaded.all().subList(0,128).stream().map(VirtualModule::id).toList().equals(originalIds),"Save and reload lost legacy modules or changed their IDs");
  var list=saved.getList("Modules",10);
  for(int i=limit;i<HeartModules.LIMIT;i++)list.add(new VirtualModule("item_display").save());
  loaded.load(saved);
  h.assertTrue(loaded.all().size()==HeartModules.LIMIT&&loaded.add("item_display")==null,"Saved modules truncated to authoring limit or excess new module allowed");
  var counter=new VirtualModule("hit_counter");
  counter.edit("hitSource",HeartModules.LIMIT,"");
  h.assertTrue(counter.value("hitSource")==HeartModules.LIMIT,"Hit counter cannot reference the last saved module");
  h.succeed();
 }

 @GameTest(template="combat",batch="module_capacity_1024")
 public static void FullCoreDashboardAndGroupingRemainEditable(GameTestHelper h){
  var option=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)dev.sablelife.SableLifeServerConfig.SPEC.getValues().get("maximumModulesPerCreature");
  int previous=option.get();
  try {
   option.set(HeartModules.LIMIT);
   var core=fixture(h,2);
   var modules=core.modules();
   for(int i=0;i<HeartModules.LIMIT-1;i++){
    var module=modules.add("item_display");
    h.assertTrue(module!=null,"Server rejected module "+i);
    module.edit("name",0,"Display "+i);
    module.edit("displaySize",0.75,"");
   }
   var player=author(h,core);
   var menu=new HeartDashboardMenu(1,player.getInventory(),core);
   var first=modules.all().getFirst().id();
   h.assertTrue(menu.action(player,modules.revision(),first,"add","item_display",0,""),"Dashboard rejected module 1024");
   var last=modules.all().getLast().id();
   h.assertTrue(!menu.action(player,modules.revision(),last,"copy","",0,""),"Copy created module 1025");
   var ids=modules.all().stream().map(VirtualModule::id).toList();
   var batch=ModuleNetworking.Action.groupBatch(1,modules.revision(),ids,"All displays");
   var codecBuffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
   try {
    ModuleNetworking.Action.CODEC.encode(codecBuffer,batch);
    batch=ModuleNetworking.Action.CODEC.decode(codecBuffer);
   } finally {
    codecBuffer.release();
   }
   var beforeGroup=menu.snapshot();
   h.assertTrue(menu.stillValid(player),"Dashboard became invalid before group edit");
   h.assertTrue(core.canEdit(player),"Player lost edit permission before group edit");
   h.assertTrue(batch.revision()==modules.revision(),"Group packet lost the module revision");
   h.assertTrue(beforeGroup.getBoolean("Editable"),"Dashboard is read-only before group edit. Snapshot size="+beforeGroup.sizeInBytes()+" partial="+beforeGroup.getBoolean("PartialAuthoring"));
   h.assertTrue(menu.action(player,batch.revision(),batch.module(),batch.operation(),batch.key(),batch.value(),batch.text()),"Max-size group operation rejected for "+batch.text().length()+" characters");
   h.assertTrue(modules.all().stream().allMatch(module->module.group().equals("All displays")),"Group change was incomplete");
   h.assertTrue(menu.action(player,modules.revision(),last,"select","",0,""),"Could not select module 1024");
   h.assertTrue(menu.action(player,modules.revision(),last,"edit","displaySize",0.75,""),"Could not edit module 1024");
   var snapshotBuffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
   try {
    ItemStack.OPTIONAL_STREAM_CODEC.encode(snapshotBuffer,menu.getSlot(0).getItem());
    System.out.println("MODULE_CAPACITY_DASHBOARD_BYTES="+snapshotBuffer.readableBytes());
    var stack=ItemStack.OPTIONAL_STREAM_CODEC.decode(snapshotBuffer);
    var snapshot=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag();
    h.assertTrue(snapshot.getBoolean("Editable")&&snapshot.getList("Modules",10).size()==HeartModules.LIMIT,"Full snapshot lost edit access or module rows");
    h.assertTrue(snapshot.getInt("ModuleLimit")==HeartModules.LIMIT&&snapshot.getUUID("Selected").equals(last),"Snapshot lost configured limit or selection");
    h.assertTrue(snapshot.getList("Modules",10).getCompound(HeartModules.LIMIT-1).getCompound("Settings").getDouble("displaySize")==0.75,"Packet lost selected module detail");
   } finally {
    snapshotBuffer.release();
   }
  } finally {
   option.set(previous);
  }
  h.succeed();
 }

}
