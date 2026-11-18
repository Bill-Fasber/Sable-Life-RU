package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;
import java.util.*;

/** Each release samples current target positions once. No homing or movement prediction. */
public final class ProjectileAim {
    public static List<Vec3> directions(HeartBlockEntity heart,VirtualModule m,Vec3 origin){
        if(m.value("shotAim")==0)return List.of(ModuleCombat.direction(heart,m));
        var level=(ServerLevel)heart.getLevel();double range=m.value("targetRange");
        java.util.function.ToDoubleFunction<net.minecraft.world.entity.LivingEntity> distance = e ->
                SableBodyProbe.toWorld(level, e.getBoundingBox().getCenter()).distanceToSqr(origin);
        return ModuleTargets.aimedTargets(level, heart, new AABB(origin, origin).inflate(range),
                e -> {
                    if (!ModuleTargets.matches(e, m)) return false;
                    double squared = distance.applyAsDouble(e);
                    return squared > 1e-8 && squared <= range * range;
                },
                ModuleTargets.comparator(heart, distance), e -> ModuleTargets.attackVisible(level, heart, m, origin, e),
                m.value("targetMultiple") == 1 ? 64 : 1, ModuleTargets.attackTarget(heart, m)).stream()
                .map(e -> SableBodyProbe.toWorld(level, e.getBoundingBox().getCenter()).subtract(origin).normalize()).toList();
    }
    private ProjectileAim(){}
}
