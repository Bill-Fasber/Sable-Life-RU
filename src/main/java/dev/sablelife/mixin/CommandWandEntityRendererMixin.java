package dev.sablelife.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.sablelife.client.CommandWandClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public abstract class CommandWandEntityRendererMixin {
    @ModifyExpressionValue(method = "shouldRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;shouldRender(DDD)Z"))
    private boolean sablelife$renderCommandTarget(boolean withinRenderDistance, Entity entity) {
        return withinRenderDistance || CommandWandClient.highlights(entity);
    }
}
