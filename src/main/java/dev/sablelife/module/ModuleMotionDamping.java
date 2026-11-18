package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Independent, center-of-mass linear braking. Six directional settings retain all other axes. */
public final class ModuleMotionDamping {
    public static final List<String> DIRECTIONS=List.of("Forward","Backward","Right","Left","Up","Down");
    private static final class State{boolean powered;long end,activation=Long.MIN_VALUE;}
    private final Map<UUID,State> states=new HashMap<>();
    private final Set<UUID> failed=new HashSet<>();
    private int revision=-1;private List<VirtualModule> modules=List.of();private long lastTick=Long.MIN_VALUE;
    public void clear(){states.clear();failed.clear();modules=List.of();revision=-1;lastTick=Long.MIN_VALUE;}
    public static Vec3[] axes(PhysicsModuleSupport.Frame frame,boolean local){
        var forward=local?frame.forward():new Vec3(0,0,-1);var right=local?frame.right():new Vec3(1,0,0);var up=local?frame.up():new Vec3(0,1,0);
        return new Vec3[]{forward,forward.scale(-1),right,right.scale(-1),up,up.scale(-1)};
    }
    /** Exponential decay of only the excess speed. Never reverses an axis or crosses its threshold. */
    static double brake(double speed,double threshold,double strength){
        return MotionForceMath.brake(speed,threshold,strength);
    }
    static Vec3 delta(VirtualModule m,Vec3 velocity,Vec3[] axes){
        Vec3 result=Vec3.ZERO;
        for(int i=0;i<axes.length;i++){
            double speed=velocity.dot(axes[i]);String suffix=DIRECTIONS.get(i);
            double amount=brake(speed,m.value("damp"+suffix+"Speed"),m.value("damp"+suffix));
            if(amount>0)result=result.subtract(axes[i].scale(amount));
        }
        return result;
    }
    public void tick(HeartBlockEntity heart){
        long now=heart.getLevel().getGameTime();if(now==lastTick)return;lastTick=now;
        if(!heart.operational()||!heart.wireless().master()){clear();return;}
        if(revision!=heart.modules().revision()){
            modules=heart.modules().all().stream().filter(m->m.type().equals("motion_damping")).toList();revision=heart.modules().revision();
            var keep=modules.stream().map(VirtualModule::id).collect(java.util.stream.Collectors.toSet());states.keySet().retainAll(keep);failed.retainAll(keep);
        }
        for(var m:modules){
            if(failed.contains(m.id()))continue;
            try {
            var s=states.computeIfAbsent(m.id(),id->new State());
            if(!HeartModules.triggered(m,heart)){s.powered=false;continue;}
            long activation=heart.modules().moveActivation(m.id());
            boolean start=!s.powered||s.activation!=activation;s.powered=true;s.activation=activation;
            if(start){if(!heart.modules().hurtTriggers.start(m))continue;s.end=now+(long)m.value("dampDuration");}
            if(m.value("dampMode")==1&&now>=s.end){heart.modules().hurtTriggers.completed(m);continue;}
            if(DIRECTIONS.stream().noneMatch(d->m.value("damp"+d)>0))continue;
            var body=PhysicsModuleSupport.body(heart,m);if(body==null)continue;
            var velocity=body.velocity();if(!PhysicsModuleSupport.finite(velocity))continue;
            var change=delta(m,velocity,axes(body.frame(),m.value("dampFrame")==1));
            if(change.lengthSqr()>1e-16)body.atCenter(change.scale(body.mass()));
            } catch (LinkageError | RuntimeException error) {
                failed.add(m.id());states.remove(m.id());
                com.mojang.logging.LogUtils.getLogger().warn("Sable Life suspended ModuleMotionDamping instance {} after a physics failure; saved settings retained",m.id(),error);
            }
        }
    }
    public static void preview(ServerPlayer player,HeartBlockEntity heart,VirtualModule m){
        var frame=PhysicsModuleSupport.frame(heart,m);if(frame==null)return;
        PhysicsModuleSupport.previewBody(player,frame,0x66CCE5);
        var axes=axes(frame,m.value("dampFrame")==1);var edges=new ArrayList<Vec3>();
        for(int i=0;i<axes.length;i++)if(m.value("damp"+DIRECTIONS.get(i))>0)edges.addAll(PhysicsModuleSupport.arrow(PhysicsModuleSupport.origin(frame),axes[i],1.8));
        if(edges.isEmpty())for(var axis:axes)edges.addAll(PhysicsModuleSupport.arrow(PhysicsModuleSupport.origin(frame),axis,.7));
        MarkOutline.send(player,MarkOutline.Kind.DETAIL,0x66CCE5,edges);
    }
}
