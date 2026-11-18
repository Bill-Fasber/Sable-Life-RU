package dev.sablelife.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sablelife.combat.TaczBreachingDamage;
import net.neoforged.bus.api.ICancellableEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "com.victoriomods.taczballisticbreaching.BallisticBlockHitHandler", remap = false)
public abstract class TaczBreachingDamageMixin {
    @WrapMethod(method = "onAmmoHitBlock(Lcom/tacz/guns/api/event/server/AmmoHitBlockEvent;)V")
    private static void sablelife$settleCanceledBodyHit(@Coerce Object event, Operation<Void> original) {
        if (!(event instanceof ICancellableEvent cancellable) || cancellable.isCanceled()) {
            original.call(event);
            return;
        }
        var impact = TaczBreachingDamage.capture(event);
        original.call(event);
        if (impact != null && cancellable.isCanceled()) impact.run();
    }
}
