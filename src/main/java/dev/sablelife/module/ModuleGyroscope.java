package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.attack.ContactBox;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

/** A stored local gravity axis, rather than an absolute heading. Linear movement owns poses first. */
public final class ModuleGyroscope {
    public static final Vec3 DOWN = new Vec3(0,-1,0);
    record Recovery(List<LinearMovementGeometry.Body> bodies, Vec3 pivot, double radius,
                    Quaterniond turn, boolean collision, boolean preserveTranslation) {}

    private final Map<UUID,String> states = new HashMap<>();
    private final Set<UUID> recovering = new HashSet<>();
    private long lastTick = Long.MIN_VALUE;
    private boolean controlled;
    public void clear() { states.clear(); recovering.clear(); lastTick=Long.MIN_VALUE; controlled=false; }
    public String state(UUID id) { return states.getOrDefault(id,"idle"); }
    public static boolean calibrate(HeartBlockEntity heart,VirtualModule module) {
        if (!(heart.getLevel() instanceof ServerLevel) || module == null || !module.type().equals("gyroscope")
                || !List.of("valid","disabled").contains(HeartModules.status(module,heart))) return false;
        var body=module.anchors().getFirst().body(heart);
        if (body==null || body.isRemoved()) return false;
        var local=new Quaterniond(body.logicalPose().orientation()).normalize().invert().transform(new Vector3d(0,-1,0));
        return module.gyroDown(new Vec3(local.x,local.y,local.z));
    }
    public static Vec3 down(HeartBlockEntity heart,VirtualModule module) {
        if (module.gyroDown()==null || module.anchors().isEmpty()) return null;
        var body=module.anchors().getFirst().body(heart);
        return body==null || body.isRemoved() ? null : ContactBox.rotate(new Quaterniond(body.logicalPose().orientation()),module.gyroDown()).normalize();
    }
    public static double tilt(Vec3 down) { return down==null ? Double.NaN : Math.toDegrees(Math.acos(Math.clamp(down.normalize().dot(DOWN),-1,1))); }
    public boolean tick(HeartBlockEntity heart,boolean movementBusy) {return tick(heart,movementBusy,false);}
    public boolean tick(HeartBlockEntity heart,boolean movementBusy,boolean preserveTranslation) {
        return tick(heart,movementBusy,preserveTranslation,null);
    }
    public boolean tick(HeartBlockEntity heart,boolean movementBusy,boolean preserveTranslation,Vec3 rotationPivot) {
        return tick(heart, movementBusy, preserveTranslation, rotationPivot, null);
    }
    boolean tick(HeartBlockEntity heart, boolean movementBusy, boolean preserveTranslation, Vec3 rotationPivot,
                 ModuleSignalTurn signalTurn) {
        if (!(heart.getLevel() instanceof ServerLevel level) || !heart.operational() || !heart.wireless().master()) { clear(); return false; }
        if (lastTick==level.getGameTime()) return controlled;
        lastTick=level.getGameTime(); controlled=false; var keep=new HashSet<UUID>();
        for (var module:heart.modules().all()) {
            if (!module.type().equals("gyroscope")) continue;
            var id=module.id(); keep.add(id);
            if (!HeartModules.triggered(module,heart)) { recovering.remove(id); states.put(id,"idle"); continue; }
            var down=down(heart,module);
            if (down==null) { recovering.remove(id); states.put(id,"uncalibrated"); continue; }
            if (movementBusy || controlled) { states.put(id,"busy"); continue; }
            double angle=tilt(down),limit=module.value("gyroTilt");
            if (!recovering.contains(id) && angle>limit+.1) recovering.add(id);
            double target=module.value("gyroReturn")==0 ? 0 : Math.max(0,limit-3);
            if (!recovering.contains(id) || angle<=target+.05) { recovering.remove(id); states.put(id,"stable"); continue; }
            controlled=true;
            var bodies=LinearMovementGeometry.capture(heart);var physics=SubLevelPhysicsSystem.get(level);
            if (bodies.isEmpty() || physics==null) { states.put(id,"blocked"); continue; }
            var pivot=rotationPivot!=null?rotationPivot:SableBodyProbe.toWorld(level,heart.getBlockPos().getCenter());
            double radius=AutoFacingGeometry.radius(bodies,pivot),degrees=Math.min(module.value("gyroSpeed")/20,angle-target);
            boolean collision=module.value("gyroCollision")==1;
            if (collision) degrees=Math.min(degrees,Math.toDegrees(4/radius));
            var turn=AutoFacingGeometry.turn(down,DOWN,true,degrees);
            if (signalTurn != null && signalTurn.tickWithGyroscope(heart,
                    new Recovery(bodies, pivot, radius, turn, collision, preserveTranslation))) {
                states.put(id,"recovering");
                continue;
            }
            if (collision && !AutoFacingGeometry.clearTurn(level,bodies,pivot,turn,radius)) { states.put(id,CollisionWorkBudget.exhausted(level)?"budget":"blocked"); continue; }
            var rotated=AutoFacingGeometry.rotated(bodies,pivot,turn);
            if (!LinearMovementGeometry.loadedDestination(level,rotated)) { states.put(id,"blocked"); continue; }
            applyRecovery(physics, rotated, preserveTranslation);
            dev.sablelife.ownership.LoadedCoreIndex.geometryChanged(level);states.put(id,"recovering");
        }
        states.keySet().retainAll(keep);recovering.retainAll(keep);return controlled;
    }
    static void applyRecovery(SubLevelPhysicsSystem physics, List<LinearMovementGeometry.Body> rotated,
                              boolean preserveTranslation) {
        var pipeline = physics.getPipeline();
        for (var body : rotated) {
            var angular = body.body().latestAngularVelocity;
            if (preserveTranslation) {
                // Keep the drive and step assist already applied by Linear movement this tick.
                pipeline.teleport(body.body(), body.position(), body.rotation());
                pipeline.addLinearAndAngularVelocity(body.body(), new Vector3d(), new Vector3d(-angular.x, 0, -angular.z));
            } else {
                var velocity = new Vector3d(body.body().latestLinearVelocity);
                var spin = new Vector3d(0, angular.y, 0);
                pipeline.resetVelocity(body.body());
                pipeline.teleport(body.body(), body.position(), body.rotation());
                pipeline.addLinearAndAngularVelocity(body.body(), velocity, spin);
            }
        }
    }
    public static void preview(net.minecraft.server.level.ServerPlayer player,HeartBlockEntity heart,VirtualModule module) {
        var center=module.anchors().getFirst().world(heart,false);var current=down(heart,module);
        // Use existing short-lived outline slots and the shared shader-compatible line material.
        // Different arrow lengths keep both tips readable when the calibrated axis is level.
        MarkOutline.send(player,MarkOutline.Kind.ALIGN_DOWN,0x33CCFF,arrow(center,DOWN,2.6));
        MarkOutline.send(player,MarkOutline.Kind.DETAIL,0xFF9933,current==null?List.of():arrow(center,current,2.2));
        double angle=Math.toRadians(module.value("gyroTilt")),radius=2*Math.sin(angle),height=-2*Math.cos(angle);
        var edges=new ArrayList<Vec3>(104);
        if(Math.abs(radius)<1E-6){
            var tip=center.add(0,height,0);edges.add(tip.add(-.12,0,0));edges.add(tip.add(.12,0,0));
            edges.add(tip.add(0,0,-.12));edges.add(tip.add(0,0,.12));edges.add(center);edges.add(tip);
        }else{
            for(int n=0;n<48;n++){
                double a=n*Math.PI*2/48,b=(n+1)*Math.PI*2/48;
                edges.add(center.add(Math.cos(a)*radius,height,Math.sin(a)*radius));
                edges.add(center.add(Math.cos(b)*radius,height,Math.sin(b)*radius));
            }
            for(int n=0;n<4;n++){double a=n*Math.PI/2;edges.add(center);edges.add(center.add(Math.cos(a)*radius,height,Math.sin(a)*radius));}
        }
        MarkOutline.send(player,MarkOutline.Kind.VOLUME,0x33FF66,edges);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("dashboard.sablelife.gyro_preview_help"),true);
    }
    private static List<Vec3> arrow(Vec3 center,Vec3 direction,double length){
        var axis=direction.normalize();var tip=center.add(axis.scale(length));
        var u=axis.cross(Math.abs(axis.y)>.9?new Vec3(0,0,1):new Vec3(0,1,0)).normalize();var v=axis.cross(u);
        var base=tip.subtract(axis.scale(.28));var edges=new ArrayList<Vec3>(10);edges.add(center);edges.add(tip);
        for(var side:List.of(u,u.scale(-1),v,v.scale(-1))){edges.add(tip);edges.add(base.add(side.scale(.13)));}
        return edges;
    }
}
