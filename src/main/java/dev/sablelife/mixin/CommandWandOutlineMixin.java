package dev.sablelife.mixin;

import dev.sablelife.client.CommandWandClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class CommandWandOutlineMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void sablelife$highlightCommandTarget(Entity entity, CallbackInfoReturnable<Boolean> result) {
        if (CommandWandClient.highlights(entity)) result.setReturnValue(true);
    }
}
