package dev.sablelife.module;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sablelife.attack.ContactBox;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

/** Stateless geometry and unit conversions used only by the two optional force modules. */
public final class PhysicsModuleSupport {
    public static final double DT = 1.0 / 20;
    public record Frame(ServerSubLevel body, Vec3 point, Quaterniond rotation, Vec3 forward, Vec3 right, Vec3 up) {}
    record Body(Frame frame, RigidBodyHandle handle, double mass) {
        Vec3 velocity() { var v=handle.getLinearVelocity(new Vector3d());return new Vec3(v.x,v.y,v.z); }
        void atPoint(Vec3 worldImpulse) {
            var local=localImpulse(frame,worldImpulse);
            if(finite(worldImpulse)&&local.isFinite())handle.applyImpulseAtPoint(new Vector3d(frame.point.x,frame.point.y,frame.point.z),local);
        }
        void atCenter(Vec3 worldImpulse) {
            var local=localImpulse(frame,worldImpulse);
            if(finite(worldImpulse)&&local.isFinite())handle.applyLinearImpulse(local);
        }
    }
    public static Frame frame(HeartBlockEntity heart, VirtualModule module) {
        if(module.anchors().size()!=1)return null;
        var mark=module.anchors().getFirst();
        if(!(mark.body(heart) instanceof ServerSubLevel body)||body.isRemoved())return null;
        var forward=Vec3.atLowerCornerOf(mark.face().getNormal());
        var up=Math.abs(forward.y)>.9?new Vec3(0,0,-1):new Vec3(0,1,0);
        var right=forward.cross(up).normalize();up=right.cross(forward).normalize();
        var rotation=new Quaterniond(body.logicalPose().orientation()).normalize();
        return new Frame(body,mark.position(body).getCenter().add(forward.scale(.5)),rotation,
                ContactBox.rotate(rotation,forward),ContactBox.rotate(rotation,right),ContactBox.rotate(rotation,up));
    }
    static Body body(HeartBlockEntity heart,VirtualModule module) {
        if(!(heart.getLevel() instanceof ServerLevel level)||!level.getServer().isRunning())return null;
        var frame=frame(heart,module);if(frame==null)return null;
        var handle=RigidBodyHandle.of(frame.body);if(handle==null||!handle.isValid())return null;
        double mass=frame.body.getMassTracker().getMass();
        return Double.isFinite(mass)&&mass>1e-9?new Body(frame,handle,mass):null;
    }
    public static Vec3 direction(Frame frame,double horizontal,double vertical) {
        double yaw=Math.toRadians(horizontal),pitch=Math.toRadians(vertical);
        return frame.forward.scale(Math.cos(yaw)*Math.cos(pitch)).add(frame.right.scale(Math.sin(yaw)*Math.cos(pitch)))
                .add(frame.up.scale(Math.sin(pitch))).normalize();
    }
    private static Vector3d localImpulse(Frame frame,Vec3 impulse) {
        return new Quaterniond(frame.rotation).conjugate().transform(new Vector3d(impulse.x,impulse.y,impulse.z));
    }
    static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    public static List<Vec3> arrow(Vec3 origin,Vec3 direction,double length) {
        var tip=origin.add(direction.scale(length));var side=ParticlePattern.side(direction);
        return List.of(origin,tip,tip,tip.subtract(direction.scale(.3)).add(side.scale(.15)),tip,tip.subtract(direction.scale(.3)).subtract(side.scale(.15)));
    }
    public static Vec3 origin(Frame frame){return frame.body.logicalPose().transformPosition(frame.point);}
    public static void previewBody(net.minecraft.server.level.ServerPlayer player,Frame frame,int color){
        var bounds=frame.body.getPlot().getBoundingBox();if(bounds==null)return;
        var min=new Vec3(bounds.minX()-.025,bounds.minY()-.025,bounds.minZ()-.025);
        var max=new Vec3(bounds.maxX()+1.025,bounds.maxY()+1.025,bounds.maxZ()+1.025);
        MarkOutline.send(player,MarkOutline.Kind.VOLUME,color,MarkOutline.box(min,max,p->frame.body.logicalPose().transformPosition(p)));
    }
    private PhysicsModuleSupport(){}
}
