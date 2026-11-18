package dev.sablelife.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlock;
import dev.sablelife.item.CommandWandItem;
import dev.sablelife.item.CommandWandItem.Input;
import dev.sablelife.item.CommandWandItem.Operation;
import dev.sablelife.module.MarkOutline;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@EventBusSubscriber(modid = SableLife.MOD_ID, value = Dist.CLIENT)
public final class CommandWandClient {
    private static final int GREEN = 0x48FF70;
    private static CommandWandItem.Selection hover;
    private static boolean attackHandled, useHandled;

    private CommandWandClient() {}

    private static boolean holdingWand(Minecraft mc) {
        return mc.player != null && mc.player.isAlive() && !mc.player.isSpectator()
                && !HeartDashboardScreen.marking && mc.player.getMainHandItem().is(SableLife.COMMAND_WAND.get())
                && NetworkRegistry.hasChannel(mc.player.connection, Input.TYPE.id());
    }

    public static boolean highlights(Entity entity) {
        var mc = Minecraft.getInstance();
        return hover != null && hover.entity() == entity && entity.isAlive()
                && entity.level() == mc.level && mc.screen == null && holdingWand(mc);
    }

    @SubscribeEvent
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        if (mc.screen != null || !holdingWand(mc) || !event.isAttack() && !event.isUseItem()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.isAttack()) {
            if (attackHandled) return;
            attackHandled = mc.options.keyAttack.isDown();
        } else {
            if (useHandled) return;
            useHandled = mc.options.keyUse.isDown();
        }

        var player = mc.player;
        var operation = event.isAttack() ? Operation.ENTITY : Operation.DESTINATION;
        if (event.isUseItem() && (player.isShiftKeyDown() || mc.options.keyShift.isDown())) {
            var hit = CommandWandItem.pickBlock(player, player.blockInteractionRange() + 1);
            if (hit.getType() == HitResult.Type.BLOCK && player.level().getBlockState(hit.getBlockPos()).getBlock() instanceof HeartBlock) {
                operation = Operation.BIND;
            } else {
                var wand = player.getMainHandItem();
                mc.setScreen(new CommandWandScreen(CommandWandItem.duration(wand), CommandWandItem.enabledModulesMask(wand)));
                return;
            }
        }
        player.connection.send(new ServerboundMovePlayerPacket.Rot(player.getYRot(), player.getXRot(), player.onGround()));
        PacketDistributor.sendToServer(new Input(operation, 0));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!mc.options.keyAttack.isDown()) attackHandled = false;
        if (!mc.options.keyUse.isDown()) useHandled = false;
        hover = mc.screen == null && holdingWand(mc) ? CommandWandItem.pick(mc.player) : null;
    }

    @SubscribeEvent
    public static void hideVanillaOutline(RenderHighlightEvent.Block event) {
        var mc = Minecraft.getInstance();
        if (hover != null && mc.screen == null && holdingWand(mc)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        var stage = event.getStage();
        if (stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES && stage != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        boolean shaders = ShaderParticleCompat.active();
        if (stage != (shaders ? RenderLevelStageEvent.Stage.AFTER_LEVEL : RenderLevelStageEvent.Stage.AFTER_ENTITIES)) return;
        var mc = Minecraft.getInstance();
        if (hover == null || mc.screen != null || !holdingWand(mc)) return;
        var selection = hover;
        if (selection.entity() != null || selection.block().getType() != HitResult.Type.BLOCK) return;

        var pos = selection.block().getBlockPos();
        var box = new AABB(pos).inflate(.006);
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var body = Sable.HELPER.getContaining(mc.level, pos);
        var transform = body instanceof ClientSubLevel clientBody ? clientBody.renderPose(partial) : null;
        var vertices = MarkOutline.box(new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.maxZ), point -> transform == null ? point : transform.transformPosition(point));
        var pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        var lineType = PreviewLineStyle.commandWandLines(shaders);
        var modelView = RenderSystem.getModelViewStack();
        if (shaders) {
            // Use the composited world framebuffer before the hand and HUD clear its depth.
            mc.getMainRenderTarget().bindWrite(false);
            modelView.pushMatrix().mul(event.getModelViewMatrix());
            RenderSystem.applyModelViewMatrix();
        }
        pose.pushPose();
        try {
            pose.translate(-camera.x, -camera.y, -camera.z);
            var lines = buffers.getBuffer(lineType);
            for (int i = 0; i < vertices.size(); i += 2) {
                BlueprintPreviewClient.line(pose, lines, vertices.get(i), vertices.get(i + 1), GREEN);
            }
            buffers.endBatch(lineType);
        } finally {
            pose.popPose();
            if (shaders) {
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        }
    }
}
