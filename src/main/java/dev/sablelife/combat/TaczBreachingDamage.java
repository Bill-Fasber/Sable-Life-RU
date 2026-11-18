package dev.sablelife.combat;

import dev.ryanhcode.sable.Sable;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;

import java.lang.reflect.Method;
import java.util.Optional;

/** Captures the original body impact before Breaching can destroy the block or change bullet damage. */
public final class TaczBreachingDamage {
    private record Access(Method ammo, Method hit) {}

    private static final ClassValue<Optional<Access>> ACCESS = new ClassValue<>() {
        @Override protected Optional<Access> computeValue(Class<?> type) {
            try {
                return Optional.of(new Access(type.getMethod("getAmmo"), type.getMethod("getHitResult")));
            } catch (NoSuchMethodException ignored) {
                return Optional.empty();
            }
        }
    };

    public static Runnable capture(Object event) {
        var access = ACCESS.get(event.getClass()).orElse(null);
        if (access == null) return null;
        try {
            if (!(access.ammo().invoke(event) instanceof Projectile bullet)
                    || !(bullet.level() instanceof ServerLevel level) || bullet.isRemoved()
                    || !(access.hit().invoke(event) instanceof BlockHitResult hit)
                    || Sable.HELPER.getContaining(level, hit.getBlockPos()) == null) return null;

            var core = BodyCombat.findDamageHeart(level, hit.getBlockPos());
            if (core == null || core.isControlCore() || core.health().state() != HeartState.ALIVE
                    || EntityHitboxes.blocksStructure(core)) return null;

            float amount = BodyDamageBridge.projectileDamage(bullet, SableBodyProbe.toWorld(level, hit.getLocation()));
            if (!Float.isFinite(amount) || amount <= 0) return null;
            return () -> BodyDamageBridge.hit(level, core, bullet, hit, amount);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private TaczBreachingDamage() {}
}
