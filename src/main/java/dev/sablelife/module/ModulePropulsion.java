package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** One marked rigid body per module. Adds native impulses, never owns poses or clears velocity. */
public final class ModulePropulsion {
    private static final List<Direction> WORLD_DIRECTIONS=List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST,Direction.UP,Direction.DOWN);
    private static final class State { long ready,end,activation=Long.MIN_VALUE; boolean active; }
    private final Map<UUID,State> states=new HashMap<>();
    private final Set<UUID> failed=new HashSet<>();
    private int revision=-1;private List<VirtualModule> modules=List.of();private long lastTick=Long.MIN_VALUE;
    public void clear(){states.clear();failed.clear();modules=List.of();revision=-1;lastTick=Long.MIN_VALUE;}
    public static boolean visible(VirtualModule m,String key){return switch(key){
        case "propWorldDirection" -> m.value("propFrame")==1;
        case "propDuration" -> m.value("propMode")==1;
        case "propMaxSpeed","propOverspeed" -> m.value("propLimit")==1;
        default -> true;
    };}
    /** Saved modules default to marked-face space; world axes never inherit body rotation. */
    public static Vec3 direction(PhysicsModuleSupport.Frame frame,VirtualModule module) {
        double horizontal=module.value("propHorizontal"),vertical=module.value("propVertical");
        if(module.value("propFrame")!=1)return PhysicsModuleSupport.direction(frame,horizontal,vertical);
        var forward=Vec3.atLowerCornerOf(WORLD_DIRECTIONS.get(Math.clamp((int)module.value("propWorldDirection"),0,5)).getNormal());
        var up=Math.abs(forward.y)>.9?new Vec3(0,0,-1):new Vec3(0,1,0);
        var right=forward.cross(up).normalize();up=right.cross(forward).normalize();
        double yaw=Math.toRadians(horizontal),pitch=Math.toRadians(vertical);
        return forward.scale(Math.cos(yaw)*Math.cos(pitch)).add(right.scale(Math.sin(yaw)*Math.cos(pitch)))
                .add(up.scale(Math.sin(pitch))).normalize();
    }
    /** Impulse in the marked direction; a ceiling never accelerates a slower body toward that ceiling. */
    static double impulse(double speed,double mass,double strength,boolean continuous,boolean limited,double maximum,boolean brake) {
        return MotionForceMath.impulse(speed,mass,strength,continuous,limited,maximum,brake);
    }
    public void tick(HeartBlockEntity heart){
        long now=heart.getLevel().getGameTime();if(now==lastTick)return;lastTick=now;
        if(!heart.operational()||!heart.wireless().master()){clear();return;}
        if(revision!=heart.modules().revision()){
            modules=heart.modules().all().stream().filter(m->m.type().equals("propulsion")).toList();revision=heart.modules().revision();
            var keep=modules.stream().map(VirtualModule::id).collect(java.util.stream.Collectors.toSet());states.keySet().retainAll(keep);failed.retainAll(keep);
        }
        for(var m:modules){
            if(failed.contains(m.id()))continue;
            try {
            var s=states.computeIfAbsent(m.id(),id->new State());
            if(!HeartModules.triggered(m,heart)){s.active=false;continue;}
            long activation=heart.modules().moveActivation(m.id());
            if(s.activation!=activation){s.activation=activation;s.active=false;s.ready=now;}
            if(s.active&&now>=s.end){s.active=false;heart.modules().hurtTriggers.completed(m);}
            if(!s.active){
                if(now<s.ready||!heart.modules().hurtTriggers.start(m))continue;
                boolean continuous=m.value("propMode")==1;
                s.end=now+(continuous?(long)m.value("propDuration"):1);
                s.ready=s.end+(long)m.value("interval");s.active=true;
            }
            var body=PhysicsModuleSupport.body(heart,m);if(body==null)continue;
            var direction=direction(body.frame(),m);
            var velocity=body.velocity();if(!PhysicsModuleSupport.finite(velocity))continue;
            double amount=impulse(velocity.dot(direction),body.mass(),m.value("propStrength"),m.value("propMode")==1,
                    m.value("propLimit")==1,m.value("propMaxSpeed"),m.value("propOverspeed")==1);
            if(amount!=0)body.atPoint(direction.scale(amount));
            if(m.value("propMode")==0){s.active=false;heart.modules().hurtTriggers.completed(m);}
            } catch (LinkageError | RuntimeException error) {
                failed.add(m.id());states.remove(m.id());
                com.mojang.logging.LogUtils.getLogger().warn("Sable Life suspended ModulePropulsion instance {} after a physics failure; saved settings retained",m.id(),error);
            }
        }
    }
    public static void preview(ServerPlayer player,HeartBlockEntity heart,VirtualModule m){
        var frame=PhysicsModuleSupport.frame(heart,m);if(frame==null)return;
        PhysicsModuleSupport.previewBody(player,frame,0xFFB347);
        MarkOutline.send(player,MarkOutline.Kind.DETAIL,0xFFB347,PhysicsModuleSupport.arrow(PhysicsModuleSupport.origin(frame),
                direction(frame,m),2));
    }
}
