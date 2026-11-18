package dev.sablelife.module;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

/** Rotates a connected machine from directional signals or a single horizontal turn per activation. */
public final class ModuleSignalTurn {
    public static final List<String> DIRECTIONS=List.of("turnLeft","turnRight","turnUp","turnDown","turnClockwise","turnCounterclockwise");
    private final Map<UUID,String> states=new HashMap<>();
    private final Map<UUID, RandomTurn> randomTurns = new HashMap<>();
    private long lastTick=Long.MIN_VALUE;
    private boolean controlled;
    public String state(UUID id){return states.getOrDefault(id,"idle");}
    public void clear(){states.clear();randomTurns.clear();lastTick=Long.MIN_VALUE;controlled=false;}

    private static final class RandomTurn {
        final long actionStart;
        double remainingDegrees;

        RandomTurn(long actionStart, double remainingDegrees) {
            this.actionStart = actionStart;
            this.remainingDegrees = remainingDegrees;
        }

        boolean complete() { return Math.abs(remainingDegrees) < 1e-6; }
    }

    public static boolean visible(VirtualModule module, String key) {
        boolean random = module.value("turnMode") == 1;
        if (key.equals("turnMinYaw") || key.equals("turnMaxYaw")) return random;
        return !random || !(key.endsWith("Source") || key.endsWith("Channel"));
    }

    private RandomTurn randomTurn(ServerLevel level, HeartBlockEntity heart, VirtualModule module) {
        long actionStart = heart.modules().moveActivation(module.id());
        var turn = randomTurns.get(module.id());
        if (turn != null && actionStart != Long.MIN_VALUE && actionStart < turn.actionStart) return null;
        if (turn == null || turn.actionStart != actionStart) {
            double first = module.value("turnMinYaw"), second = module.value("turnMaxYaw");
            double angle = Math.min(first, second) + level.random.nextDouble() * Math.abs(first - second);
            turn = new RandomTurn(actionStart, angle);
            randomTurns.put(module.id(), turn);
        }
        return turn;
    }

    public static int input(HeartBlockEntity heart,VirtualModule module,String direction){
        int channel=(int)module.value(direction+"Channel")-1;
        return module.value(direction+"Source")==0?heart.wireless().output(channel):heart.wireless().input(channel);
    }
    public boolean tick(HeartBlockEntity heart,boolean movementBusy){return tick(heart,movementBusy,false);}
    public boolean tick(HeartBlockEntity heart,boolean movementBusy,boolean preserveTranslation){
        return tick(heart, movementBusy, preserveTranslation, null);
    }

    boolean tickWithGyroscope(HeartBlockEntity heart, ModuleGyroscope.Recovery recovery) {
        return tick(heart, false, recovery.preserveTranslation(), recovery);
    }

    private boolean tick(HeartBlockEntity heart, boolean movementBusy, boolean preserveTranslation,
                         ModuleGyroscope.Recovery recovery) {
        if(!(heart.getLevel() instanceof ServerLevel level)||!heart.operational()||!heart.wireless().master()){clear();return false;}
        if(lastTick==level.getGameTime())return recovery == null && controlled;
        lastTick=level.getGameTime();controlled=false;var keep=new HashSet<UUID>();
        boolean combinedApplied = false;
        for(var module:heart.modules().all()){
            if(!module.type().equals("signal_turn"))continue;keep.add(module.id());
            boolean triggered = HeartModules.triggered(module, heart);
            boolean random = module.value("turnMode") == 1;
            if (!triggered || !random) randomTurns.remove(module.id());
            if (!triggered) { states.put(module.id(), "idle"); continue; }
            var randomTurn = random ? randomTurn(level, heart, module) : null;
            if (random && (randomTurn == null || randomTurn.complete())) {
                states.put(module.id(), "random_done");
                finishHurtTurn(heart, module);
                continue;
            }
            if (movementBusy || controlled || (recovery != null && module.value("turnWithGyro") != 1)) {
                states.put(module.id(), "busy");
                continue;
            }
            double speed = module.value("turnSpeed");
            double yaw, pitch, roll;
            if (randomTurn != null) {
                yaw = Math.clamp(randomTurn.remainingDegrees * 20, -speed, speed);
                pitch = roll = 0;
            } else {
                yaw = SignalTurnGeometry.rate(input(heart,module,"turnLeft"), input(heart,module,"turnRight"), speed);
                pitch = SignalTurnGeometry.rate(input(heart,module,"turnUp"), input(heart,module,"turnDown"), speed);
                roll = SignalTurnGeometry.rate(input(heart,module,"turnClockwise"), input(heart,module,"turnCounterclockwise"), speed);
            }
            if (yaw == 0 && pitch == 0 && roll == 0) { states.put(module.id(), random ? "no_speed" : "no_input"); continue; }
            controlled = true;
            var anchor = module.anchors().getFirst();
            var marked = anchor.body(heart);
            var bodies = recovery == null ? LinearMovementGeometry.capture(heart) : recovery.bodies();
            var physics = SubLevelPhysicsSystem.get(level);
            if(marked==null||bodies.isEmpty()||physics==null){states.put(module.id(),"blocked");continue;}
            var pivot = recovery == null ? SableBodyProbe.toWorld(level, heart.getBlockPos().getCenter()) : recovery.pivot();
            double radius = recovery == null ? AutoFacingGeometry.radius(bodies, pivot) : recovery.radius();
            double scale = 1;
            boolean collision = module.value("turnCollision") == 1 || (recovery != null && recovery.collision());
            double magnitude=Math.sqrt(yaw*yaw+pitch*pitch+roll*roll);
            if(collision)scale=Math.min(1,Math.toDegrees(4/radius)*20/magnitude);
            SignalTurnGeometry.Frame frame = null;
            Quaterniond turn;
            if (random) {
                turn = new Quaterniond().rotationY(Math.toRadians(yaw * scale / 20));
            } else {
                var pose = new Quaterniond(marked.logicalPose().orientation());
                if (recovery != null) pose.premul(recovery.turn());
                frame = SignalTurnGeometry.frame(pose, anchor.face());
                turn = SignalTurnGeometry.turn(frame, yaw, pitch, roll, scale);
            }
            boolean partial = false;
            if (recovery != null) {
                turn.mul(recovery.turn());
                if (collision && !AutoFacingGeometry.clearCombinedTurn(level, bodies, pivot, turn, radius)) {
                    states.put(module.id(), CollisionWorkBudget.exhausted(level) ? "budget" : "blocked");
                    continue;
                }
            } else if(collision&&!AutoFacingGeometry.clearTurn(level,bodies,pivot,turn,radius)){
                if (random) { states.put(module.id(), CollisionWorkBudget.exhausted(level) ? "budget" : "blocked"); continue; }
                // A blocked vertical/roll axis must not stop a clear horizontal rotation.
                turn=new Quaterniond();partial=true;
                for(int axis=0;axis<3;axis++){
                    var step=SignalTurnGeometry.turn(frame,axis==0?yaw:0,axis==1?pitch:0,axis==2?roll:0,scale);
                    var candidate=new Quaterniond(step).mul(turn);
                    if(AutoFacingGeometry.clearTurn(level,bodies,pivot,candidate,radius))turn=candidate;
                }
                if(2*Math.acos(Math.min(1,Math.abs(turn.w)))<1e-6){states.put(module.id(),CollisionWorkBudget.exhausted(level)?"budget":"blocked");continue;}
            }
            var rotated = AutoFacingGeometry.rotated(bodies, pivot, turn);
            if (recovery != null) {
                if (!LinearMovementGeometry.loadedDestination(level, rotated)) {
                    states.put(module.id(), "blocked");
                    continue;
                }
                ModuleGyroscope.applyRecovery(physics, rotated, preserveTranslation);
                combinedApplied = true;
            } else {
                for(var body:rotated){
                    var velocity=(preserveTranslation?physics.getPipeline().getLinearVelocity(body.body(),new Vector3d()):new Vector3d(body.body().latestLinearVelocity));physics.getPipeline().resetVelocity(body.body());physics.getPipeline().teleport(body.body(),body.position(),body.rotation());physics.getPipeline().addLinearAndAngularVelocity(body.body(),velocity,new Vector3d());
                }
            }
            heart.modules().movementTurned(heart,pivot,turn);
            dev.sablelife.ownership.LoadedCoreIndex.geometryChanged(level);
            if (randomTurn != null) {
                randomTurn.remainingDegrees -= yaw * scale / 20;
                states.put(module.id(), randomTurn.complete() ? "random_done" : "random_turning");
                if (randomTurn.complete()) finishHurtTurn(heart, module);
            } else states.put(module.id(),partial?"partial":"turning");
        }
        states.keySet().retainAll(keep);
        randomTurns.keySet().retainAll(keep);
        return recovery == null ? controlled : combinedApplied;
    }

    private void finishHurtTurn(HeartBlockEntity heart, VirtualModule module) {
        if (ModuleHurt.once(module)) {
            randomTurns.remove(module.id());
            heart.modules().hurtTriggers.finish(module.id());
        }
    }
}
