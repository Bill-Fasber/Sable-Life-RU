package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

@GameTestHolder("sablelife_physical_motion_tests") @PrefixGameTestTemplate(false)
public final class PhysicalMotionGameTests {
    private record Fixture(HeartBlockEntity core,ServerSubLevel body) {
        RigidBodyHandle handle(){return RigidBodyHandle.of(body);}
        Vec3 velocity(){var v=handle().getLinearVelocity(new Vector3d());return new Vec3(v.x,v.y,v.z);}
        double mass(){return body.getMassTracker().getMass();}
    }
    private static Fixture fixture(GameTestHelper h,int kind,boolean arm){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,45,4));
        var block=kind==0?SableLife.HEART.get():kind==1?SableLife.SURVIVAL_LIFE_CORE.get():SableLife.REDSTONE_CORE.get();
        level.setBlockAndUpdate(pos,block.defaultBlockState());var points=new ArrayList<BlockPos>();points.add(pos);
        if(arm)for(int i=1;i<=2;i++){points.add(pos.east(i));level.setBlockAndUpdate(pos.east(i),Blocks.IRON_BLOCK.defaultBlockState());}
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,points,new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+(arm?2:0),pos.getY(),pos.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());var actor=TestActors.creative(level);
        if(kind==1)core.depositSurvivalTokens(actor,new ItemStack(Items.DIAMOND,64));
        if(kind!=2)core.tryActivate(actor);
        h.assertTrue(core.operational(),"Core did not become operational: "+kind);
        return new Fixture(core,body);
    }
    private static VirtualModule add(Fixture f,String type,Direction direction){
        var m=f.core.modules().add(type);if(m==null)throw new AssertionError("Missing module "+type);
        m.anchors(List.of(ModuleAnchor.create(f.core,f.core.getBlockPos(),direction)));m.edit("enabled",0,"");return m;
    }
    private static void start(Fixture f,VirtualModule m,Vec3 velocity){
        var physics=SubLevelPhysicsSystem.get(f.core.getLevel()).getPipeline();physics.resetVelocity(f.body);
        physics.addLinearAndAngularVelocity(f.body,new Vector3d(velocity.x,velocity.y,velocity.z),new Vector3d());
        m.edit("enabled",1,"");f.core.modules().clearRuntime();f.core.modules().tick(f.core);
    }
    private static void near(GameTestHelper h,double actual,double expected,String message){h.assertTrue(Math.abs(actual-expected)<1e-4,message+" actual="+actual+" expected="+expected);}
    @GameTest(template="combat",batch="physical_impulse") public static void SingleImpulseRetainsExternalVelocity(GameTestHelper h){
        var f=fixture(h,0,false);var m=add(f,"propulsion",Direction.EAST);
        h.runAfterDelay(4,()->{System.out.println("PHYSICS_FIXTURE_MASS "+f.mass());m.edit("propStrength",f.mass()*2,"");start(f,m,new Vec3(0,3,-4));var v=f.velocity();
            near(h,v.x,m.value("propStrength")/f.mass(),"Impulse mass response");near(h,v.y,3,"Upward velocity overwritten");near(h,v.z,-4,"Other mod velocity overwritten");m.edit("enabled",0,"");h.succeed();});
    }
    @GameTest(template="combat",batch="physical_impulse") public static void OffCenterImpulseProducesTorque(GameTestHelper h){
        var f=fixture(h,0,true);var m=add(f,"propulsion",Direction.UP);
        h.runAfterDelay(4,()->{m.edit("propStrength",f.mass()*2,"");start(f,m,Vec3.ZERO);var spin=f.handle().getAngularVelocity(new Vector3d());
            h.assertTrue(spin.length()>1e-4,"Off-center impulse did not produce torque");m.edit("enabled",0,"");h.succeed();});
    }
    @GameTest(template="combat",batch="physical_world") public static void WorldThrustKeepsAllSixDirectionsOnRotatedBody(GameTestHelper h){
        var f=fixture(h,0,false);var m=add(f,"propulsion",Direction.EAST);m.edit("propFrame",1,"");
        SubLevelPhysicsSystem.get(h.getLevel()).getPipeline().teleport(f.body,new Vector3d(f.body.logicalPose().position()),new Quaterniond().rotateYXZ(.7,.9,.4));
        h.runAfterDelay(4,()->{m.edit("propStrength",f.mass()*2,"");
            var directions=List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST,Direction.UP,Direction.DOWN);
            for(int i=0;i<directions.size();i++){
                m.edit("propWorldDirection",i,"");start(f,m,Vec3.ZERO);var expected=Vec3.atLowerCornerOf(directions.get(i).getNormal()).scale(2);var actual=f.velocity();
                near(h,actual.x,expected.x,"World X rotated with body "+i);near(h,actual.y,expected.y,"World Y rotated with body "+i);near(h,actual.z,expected.z,"World Z rotated with body "+i);
            }
            m.edit("propFrame",0,"");start(f,m,Vec3.ZERO);var expected=PhysicsModuleSupport.frame(f.core,m).forward().scale(2);
            h.assertTrue(expected.distanceTo(new Vec3(2,0,0))>.1,"Rotation fixture did not rotate local face");
            near(h,f.velocity().distanceTo(expected),0,"Switching back lost marked-face direction");m.edit("enabled",0,"");h.succeed();});
    }
    @GameTest(template="combat",batch="physical_world") public static void WorldAnglesSpeedLimitAndInspectorUseSameDirection(GameTestHelper h){
        var f=fixture(h,0,false);var m=add(f,"propulsion",Direction.WEST);m.edit("propFrame",1,"");m.edit("propWorldDirection",0,"");
        m.edit("propHorizontal",45,"");m.edit("propVertical",30,"");m.edit("propLimit",1,"");m.edit("propMaxSpeed",2,"");
        SubLevelPhysicsSystem.get(h.getLevel()).getPipeline().teleport(f.body,new Vector3d(f.body.logicalPose().position()),new Quaterniond().rotateZ(1.1));
        h.runAfterDelay(4,()->{m.edit("propStrength",f.mass()*20,"");start(f,m,Vec3.ZERO);
            var expected=new Vec3(Math.sqrt(3./8),.5,-Math.sqrt(3./8));near(h,f.velocity().distanceTo(expected.scale(2)),0,"World angle / limit mismatch");
            var row=InspectionSnapshot.capture(f.core.creatureId(),f.core,false).rows().stream().filter(r->r.id().equals(m.id())).findFirst().orElseThrow();
            var arrow=row.pieces().getFirst();h.assertTrue(arrow.worldAxes(),"Inspector rotates fixed world thrust with body");
            var shown=arrow.rotation().transform(new org.joml.Vector3f(0,0,1));near(h,new Vec3(shown.x,shown.y,shown.z).distanceTo(expected),0,"Inspector arrow differs from actual thrust");
            m.edit("enabled",0,"");h.succeed();});
    }
    @GameTest(template="combat",batch="physical_world") public static void OldPropulsionDefaultsAndWorldSettingsRoundTrip(GameTestHelper h){
        var f=fixture(h,0,false);var m=add(f,"propulsion",Direction.NORTH);m.edit("propHorizontal",27,"");m.edit("propVertical",-13,"");
        var legacy=m.save();legacy.remove("propFrame");legacy.remove("propWorldDirection");var old=VirtualModule.load(legacy);
        near(h,old.value("propFrame"),0,"Old thrust changed reference");near(h,old.value("propHorizontal"),27,"Old yaw lost");near(h,old.value("propVertical"),-13,"Old pitch lost");
        h.assertTrue(old.anchors().equals(m.anchors())&&!ModulePropulsion.visible(old,"propWorldDirection"),"Old mark or field visibility changed");
        m.edit("propFrame",1,"");m.edit("propWorldDirection",4,"");var restored=VirtualModule.load(m.save());
        near(h,restored.value("propFrame"),1,"Frame did not save");near(h,restored.value("propWorldDirection"),4,"World direction did not save");
        var copy=f.core.modules().copy(m.id());near(h,copy.value("propFrame"),1,"Copy lost frame");near(h,copy.value("propWorldDirection"),4,"Copy lost world direction");
        h.assertTrue(ModulePropulsion.visible(restored,"propWorldDirection"),"World selector hidden");h.succeed();
    }
    @GameTest(template="combat",batch="physical_damping") public static void DownDampingDoesNotAffectAscendingMotion(GameTestHelper h){
        var f=fixture(h,0,false);var m=add(f,"motion_damping",Direction.NORTH);m.edit("dampDown",100,"");m.edit("dampDownSpeed",2,"");
        h.runAfterDelay(4,()->{start(f,m,new Vec3(3,5,-4));var v=f.velocity();near(h,v.x,3,"Horizontal changed");near(h,v.y,5,"Ascent changed");near(h,v.z,-4,"Forward changed");m.edit("enabled",0,"");h.succeed();});
    }
    @GameTest(template="combat",batch="physical_damping") public static void DownDampingBrakesWithoutTorque(GameTestHelper h){
        var f=fixture(h,0,true);var m=add(f,"motion_damping",Direction.EAST);m.edit("dampDown",100,"");m.edit("dampDownSpeed",2,"");
        h.runAfterDelay(4,()->{start(f,m,new Vec3(3,-9,-4));var v=f.velocity();near(h,v.x,3,"Horizontal changed");near(h,v.z,-4,"Forward changed");
            h.assertTrue(v.y>-9&&v.y<-2,"Downward braking reversed or missed threshold");h.assertTrue(f.handle().getAngularVelocity(new Vector3d()).length()<1e-5,"Damping introduced mark-point torque");m.edit("enabled",0,"");h.succeed();});
    }
    @GameTest(template="combat",batch="physical_damping") public static void WorldAndLocalAxesDifferOnRotatedBody(GameTestHelper h){
        var f=fixture(h,0,false);var m=add(f,"motion_damping",Direction.NORTH);m.edit("dampForward",100,"");
        SubLevelPhysicsSystem.get(h.getLevel()).getPipeline().teleport(f.body,new Vector3d(f.body.logicalPose().position()),new Quaterniond().rotateY(Math.PI/2));
        h.runAfterDelay(4,()->{m.edit("dampFrame",0,"");start(f,m,new Vec3(0,0,-5));h.assertTrue(f.velocity().z>-5,"World north did not brake");
            m.edit("dampFrame",1,"");start(f,m,new Vec3(0,0,-5));near(h,f.velocity().z,-5,"Local frame used world north");m.edit("enabled",0,"");h.succeed();});
    }
    @GameTest(template="combat",batch="physical_speed") public static void DirectionalSpeedCeilingPreservesOtherAxes(GameTestHelper h){
        var f=fixture(h,0,false);var m=add(f,"propulsion",Direction.EAST);m.edit("propLimit",1,"");m.edit("propMaxSpeed",2,"");
        h.runAfterDelay(4,()->{m.edit("propStrength",f.mass()*20,"");start(f,m,new Vec3(1,3,4));near(h,f.velocity().x,2,"Ceiling overshot");near(h,f.velocity().y,3,"Other axis speed clamped");near(h,f.velocity().z,4,"Other axis speed clamped");m.edit("enabled",0,"");h.succeed();});
    }
    @GameTest(template="combat",batch="physical_speed") public static void OverspeedCoastAndBrakeAreDifferent(GameTestHelper h){
        var f=fixture(h,0,false);var m=add(f,"propulsion",Direction.EAST);m.edit("propLimit",1,"");m.edit("propMaxSpeed",2,"");
        h.runAfterDelay(4,()->{m.edit("propStrength",f.mass()*20,"");start(f,m,new Vec3(5,3,0));near(h,f.velocity().x,5,"Coast mode braked");
            m.edit("propOverspeed",1,"");start(f,m,new Vec3(5,3,0));near(h,f.velocity().x,2,"Soft brake crossed ceiling");near(h,f.velocity().y,3,"Brake affected other axis");m.edit("enabled",0,"");h.succeed();});
    }
    @GameTest(template="combat",batch="physical_combined") public static void BothModulesAndMultipleThrustersCompose(GameTestHelper h){
        var f=fixture(h,0,false);var p=add(f,"propulsion",Direction.EAST);var p2=add(f,"propulsion",Direction.EAST);var d=add(f,"motion_damping",Direction.NORTH);d.edit("dampRight",100,"");
        h.runAfterDelay(4,()->{p.edit("propStrength",f.mass(),"");p2.edit("propStrength",f.mass(),"");p2.edit("enabled",1,"");d.edit("enabled",1,"");start(f,p,Vec3.ZERO);
            h.assertTrue(f.velocity().x>0&&f.velocity().x<2,"Independent modules did not combine");for(var m:List.of(p,p2,d))m.edit("enabled",0,"");h.succeed();});
    }
    @GameTest(template="combat",batch="physical_survival") public static void SurvivalCoreRunsBothIndependentModules(GameTestHelper h){
        var f=fixture(h,1,false);var p=add(f,"propulsion",Direction.EAST);var d=add(f,"motion_damping",Direction.NORTH);
        h.runAfterDelay(4,()->{d.edit("dampDown",100,"");d.edit("enabled",1,"");start(f,p,new Vec3(0,-5,0));h.assertTrue(f.velocity().x>0&&f.velocity().y>-5,"Survival physical modules inactive");p.edit("enabled",0,"");d.edit("enabled",0,"");h.succeed();});
    }
    @GameTest(template="combat",batch="physical_redstone") public static void RedstoneCoreRunsBothWithoutLifeActivation(GameTestHelper h){
        var f=fixture(h,2,false);var p=add(f,"propulsion",Direction.EAST);var d=add(f,"motion_damping",Direction.NORTH);
        h.runAfterDelay(4,()->{d.edit("dampDown",100,"");d.edit("enabled",1,"");start(f,p,new Vec3(0,-5,0));h.assertTrue(f.velocity().x>0&&f.velocity().y>-5,"Redstone physical modules inactive");p.edit("enabled",0,"");d.edit("enabled",0,"");h.succeed();});
    }
    @GameTest(template="combat",batch="physical_persistence") public static void SavedCopiedAndPresetSettingsStayIndependent(GameTestHelper h){
        var f=fixture(h,0,false);var p=add(f,"propulsion",Direction.UP);var d=add(f,"motion_damping",Direction.NORTH);p.edit("propStrength",321.125,"");d.edit("dampDown",48.25,"");d.edit("dampDownSpeed",2.125,"");
        var restored=VirtualModule.load(d.save());near(h,restored.value("dampDownSpeed"),2.125,"Save precision lost");h.assertTrue(restored.anchors().equals(d.anchors()),"Mark did not persist");
        var copy=f.core.modules().copy(p.id());near(h,copy.value("propStrength"),321.125,"Copy lost strength");h.assertTrue(copy.anchors().equals(p.anchors()),"Copy lost Mark");
        var preset=ModulePreset.create(d.save());near(h,preset.value("dampDown"),48.25,"Preset lost strength");h.assertTrue(preset.anchors().isEmpty(),"Portable preset retained source body");
        var old=new VirtualModule("linear_movement");old.edit("travelSpeed",7.123,"");near(h,VirtualModule.load(old.save()).value("travelSpeed"),7.123,"Existing settings changed");h.succeed();
    }
    @GameTest(template="combat",batch="physical_network") public static void ExistingEditCodecAcceptsNewStableKeysAndBounds(GameTestHelper h){
        var f=fixture(h,0,false);var m=add(f,"motion_damping",Direction.NORTH);
        for(var type:List.of("motion_damping","propulsion"))for(var field:ModuleFields.forType(type)){
            var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            var action=new ModuleNetworking.Action(1,0,m.id(),"edit",field.key(),field.max(),"");ModuleNetworking.Action.CODEC.encode(buffer,action);h.assertTrue(action.equals(ModuleNetworking.Action.CODEC.decode(buffer)),"Codec mismatch "+field.key());buffer.release();
        }
        h.assertTrue(!m.edit("dampDown",Double.NaN,"")&&!m.edit("dampDownSpeed",Double.POSITIVE_INFINITY,""),"Nonfinite setting accepted");
        m.edit("dampDown",999,"");h.assertTrue(m.value("dampDown")<=100,"Strength escaped bounds");h.succeed();
    }
    @GameTest(template="combat",batch="physical_lifecycle",timeoutTicks=40) public static void ContinuousThrustEndsAndKeepsInertia(GameTestHelper h){
        var f=fixture(h,0,false);var m=add(f,"propulsion",Direction.EAST);m.edit("propMode",1,"");m.edit("propDuration",2,"");m.edit("interval",200,"");double[] initial={0};
        h.runAfterDelay(4,()->{m.edit("propStrength",f.mass()*10,"");start(f,m,Vec3.ZERO);initial[0]=f.velocity().x;h.assertTrue(initial[0]>0,"Continuous force inactive");});
        h.runAfterDelay(12,()->{h.assertTrue(f.velocity().x>initial[0]*.8&&f.velocity().x<initial[0]*2.2,"Continuous duration failed or end braked inertia: "+f.velocity());m.edit("enabled",0,"");h.succeed();});
    }
    @GameTest(template="combat",batch="physical_lifecycle",timeoutTicks=40) public static void DeletedContinuousModuleStopsAddingForce(GameTestHelper h){
        var f=fixture(h,0,false);var m=add(f,"propulsion",Direction.EAST);m.edit("propMode",1,"");m.edit("propDuration",100,"");double[] initial={0};
        h.runAfterDelay(4,()->{m.edit("propStrength",f.mass()*10,"");start(f,m,Vec3.ZERO);initial[0]=f.velocity().x;f.core.modules().remove(m.id());});
        h.runAfterDelay(12,()->{h.assertTrue(f.velocity().x>initial[0]*.8&&f.velocity().x<initial[0]*1.1,"Deleted module kept driving or reset velocity");h.succeed();});
    }
    @GameTest(template="combat",batch="physical_lifecycle",timeoutTicks=40) public static void TimedDampingExpiresAndDoesNotRearmWhileHeld(GameTestHelper h){
        var f=fixture(h,0,false);var m=add(f,"motion_damping",Direction.NORTH);m.edit("dampMode",1,"");m.edit("dampDuration",2,"");m.edit("dampRight",100,"");
        h.runAfterDelay(4,()->start(f,m,new Vec3(5,0,0)));
        h.runAfterDelay(10,()->{var physics=SubLevelPhysicsSystem.get(h.getLevel()).getPipeline();physics.resetVelocity(f.body);physics.addLinearAndAngularVelocity(f.body,new Vector3d(5,0,0),new Vector3d());});
        h.runAfterDelay(13,()->{h.assertTrue(f.velocity().x>4.8,"Timed module rearmed while signal remained held");m.edit("enabled",0,"");h.succeed();});
    }
    @GameTest(template="combat",batch="physical_move_set",timeoutTicks=50) public static void MoveSetStartsAndStopsBothModules(GameTestHelper h){
        var f=fixture(h,0,false);var p=add(f,"propulsion",Direction.EAST);var d=add(f,"motion_damping",Direction.NORTH);
        var table=f.core.modules().add("move_set");table.edit("enabled",0,"");table.edit("ignoreConditions",1,"");table.moveSet().edit("add",0,"",f.core.modules());
        for(var m:List.of(p,d)){table.moveSet().edit("action_add",0,"0|"+m.id(),f.core.modules());m.edit("enabled",1,"");}
        d.edit("dampDown",100,"");table.moveSet().edit("action_duration",.5,"0|0",f.core.modules());table.moveSet().edit("action_duration",.5,"0|1",f.core.modules());
        table.moveSet().edit("dispatchRest",200,"0",f.core.modules());
        h.runAfterDelay(4,()->{p.edit("propStrength",f.mass()*2,"");table.edit("enabled",1,"");});
        h.runAfterDelay(16,()->{h.assertTrue(f.velocity().x>0,"Move Set never activated propulsion");h.assertTrue(f.core.modules().moveActivation(p.id())!=0,"Move Set did not record activation");p.edit("enabled",0,"");d.edit("enabled",0,"");table.edit("enabled",0,"");h.succeed();});
    }
    @GameTest(template="combat",batch="physical_editor") public static void EditorRejectsUnauthorizedStaleAndOversizedRequests(GameTestHelper h){
        var f=fixture(h,0,false);var m=add(f,"propulsion",Direction.NORTH);var player=TestActors.creative(h.getLevel());
        f.core.resetForCreative(player);
        var center=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),f.core.getBlockPos().getCenter());player.setPos(center);
        var menu=new HeartDashboardMenu(83,player.getInventory(),f.core);int revision=f.core.modules().revision();
        h.assertTrue(menu.action(player,revision,m.id(),"edit","propStrength",99,""),"Authorized edit failed");
        h.assertTrue(!menu.action(player,revision,m.id(),"edit","propStrength",98,""),"Stale revision edited module");
        h.assertTrue(menu.action(player,f.core.modules().revision(),m.id(),"edit","propFrame",1,""),"World frame edit failed");
        h.assertTrue(menu.action(player,f.core.modules().revision(),m.id(),"edit","propWorldDirection",4,""),"World direction edit failed");
        var visitor=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.fromString("1272ce9b-0921-4585-b193-b6242ab6a134"),"PhysicsVisitor"));visitor.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);visitor.setPos(center);
        h.assertTrue(!menu.action(visitor,f.core.modules().revision(),m.id(),"edit","propStrength",50,""),"Unauthorized visitor edited ordinary core");near(h,m.value("propStrength"),99,"Rejected request changed strength");
        var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());boolean rejected=false;
        try{ModuleNetworking.Action.CODEC.encode(buffer,new ModuleNetworking.Action(83,0,m.id(),"edit","x".repeat(65),0,""));}catch(RuntimeException expected){rejected=true;}finally{buffer.release();}
        h.assertTrue(rejected,"Oversized field key was encoded");h.succeed();
    }
    @GameTest(template="combat",batch="physical_blueprint",timeoutTicks=100) public static void NativeBlueprintRemapsBothModuleMarks(GameTestHelper h){
        var f=fixture(h,0,true);f.core.resetForCreative(TestActors.creative(h.getLevel()));
        var p=add(f,"propulsion",Direction.UP);var d=add(f,"motion_damping",Direction.EAST);p.edit("propStrength",12.25,"");p.edit("propFrame",1,"");p.edit("propWorldDirection",4,"");d.edit("dampDown",80.25,"");d.edit("dampDownSpeed",2.125,"");
        var exported=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(h.getLevel(),dev.sablelife.module.ModuleArena.corePosition(f.core),List.of(f.body.getUniqueId())).save();
        var template=dev.sablelife.compat.photomancy.SpawnTemplateBridge.inspect(exported).template();
        var build=dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.createSpawn(template,new net.minecraft.nbt.CompoundTag(),h.absoluteVec(new Vec3(5,45,15)),new Quaterniond());
        h.assertTrue(build.step(h.getLevel()).equals("DONE"),"Native blueprint did not finish");
        h.succeedWhen(()->{var copy=build.heart(h.getLevel());h.assertTrue(copy!=null,"Spawned core missing");
            var cp=copy.modules().all().stream().filter(m->m.type().equals("propulsion")).findFirst().orElseThrow();
            var cd=copy.modules().all().stream().filter(m->m.type().equals("motion_damping")).findFirst().orElseThrow();
            h.assertTrue(cp.anchors().size()==1&&cd.anchors().size()==1&&cp.anchors().getFirst().status(copy).equals("valid")&&cd.anchors().getFirst().status(copy).equals("valid"),"Blueprint marks no longer resolve");
            h.assertTrue(!cp.anchors().getFirst().bodyId().equals(p.anchors().getFirst().bodyId()),"Blueprint retained source body ID");near(h,cp.value("propStrength"),12.25,"Blueprint strength changed");near(h,cp.value("propFrame"),1,"Blueprint frame changed");near(h,cp.value("propWorldDirection"),4,"Blueprint world direction changed");near(h,cd.value("dampDownSpeed"),2.125,"Blueprint damping changed");});
    }
    @GameTest(template="combat",batch="physical_linked",timeoutTicks=60) public static void LinkedPartRespondsThroughJointWithoutDirectVelocityRewrite(GameTestHelper h){
        var f=fixture(h,0,false);var pos=h.absolutePos(new BlockPos(6,45,4));h.getLevel().setBlockAndUpdate(pos,Blocks.IRON_BLOCK.defaultBlockState());
        var child=SubLevelAssemblyHelper.assembleBlocks(h.getLevel(),pos,List.of(pos),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ()));
        var physics=SubLevelPhysicsSystem.get(h.getLevel()).getPipeline();
        var a=f.body.getPlot().getCenterBlock().getCenter().add(.5,0,0);var b=child.getPlot().getCenterBlock().getCenter().add(-1.5,0,0);
        var joint=physics.addConstraint(f.body,child,new dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration(new Vector3d(a.x,a.y,a.z),new Vector3d(b.x,b.y,b.z),new Quaterniond(),new Quaterniond(),java.util.EnumSet.of(dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis.LINEAR_X,dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis.LINEAR_Y,dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis.LINEAR_Z)));
        var p=add(f,"propulsion",Direction.EAST);
        h.runAfterDelay(4,()->{p.edit("propStrength",f.mass()*2,"");physics.resetVelocity(child);start(f,p,Vec3.ZERO);p.edit("enabled",0,"");});
        h.runAfterDelay(10,()->{h.assertTrue(joint.isValid()&&!child.isRemoved(),"Linked structure lost its native joint");h.assertTrue(RigidBodyHandle.of(child).getLinearVelocity(new Vector3d()).x>.01,"Connected part did not receive motion through its joint");joint.remove();h.succeed();});
    }
}
