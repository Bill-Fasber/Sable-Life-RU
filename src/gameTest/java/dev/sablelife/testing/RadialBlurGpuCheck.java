package dev.sablelife.testing;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.sablelife.client.ShockwaveClient;
import dev.sablelife.client.ShockwaveMath;
import dev.sablelife.client.ShockwaveRenderer;
import dev.sablelife.module.ShockwavePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.List;

/** Checks the projected emitter, rear-facing fallback and transient cleanup in a real framebuffer. */
final class RadialBlurGpuCheck {
    private static final Vec3 REAR_VIEW_CAMERA = new Vec3(0, 0, -20);

    private static ShockwavePacket packet(Vec3 point,float strength){
        return new ShockwavePacket(ResourceLocation.withDefaultNamespace("overworld"),point,18,40,1.5F,0,strength,0,1,0,1,256,false,1,1,.08F);
    }

    private static void pattern(TextureTarget target){
        target.bindWrite(true);RenderSystem.disableScissor();
        for(int x=0;x<target.width;x+=12)for(int y=0;y<target.height;y+=12){
            RenderSystem.enableScissor(x,y,Math.min(12,target.width-x),Math.min(12,target.height-y));
            boolean light=(x/12+y/12)%2==0;
            RenderSystem.clearColor(light?.9F:.1F,.15F+.6F*x/target.width,.15F+.6F*y/target.height,1);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,false);
        }
        RenderSystem.disableScissor();
    }

    private static int difference(int a,int b){
        int max=0;for(int s=0;s<24;s+=8)max=Math.max(max,Math.abs((a>>s&255)-(b>>s&255)));return max;
    }

    static void run(Minecraft mc)throws Exception{
        var target=new TextureTarget(512,384,false,Minecraft.ON_OSX);
        var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);
        var projection=new Matrix4f().perspective((float)Math.PI/2,(float)target.width/target.height,.05F,200);
        var p=packet(new Vec3(-3,2,-10),.9F);
        try{
            pattern(target);
            try(var clean=Screenshot.takeScreenshot(target)){
                clean.writeToFile(folder.resolve("radial-baseline.png"));
                for(var camera:List.of(Vec3.ZERO,new Vec3(5,0,0),REAR_VIEW_CAMERA)){
                    pattern(target);RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
                    ShockwaveRenderer.render(target,List.of(new ShockwaveClient.Wave(p,0)),camera,new Matrix4f(),projection,8,1);
                    if(!ShockwaveRenderer.ready()||!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)||!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                            ||GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=target.frameBufferId)throw new AssertionError("Radial pass leaked render state");
                    var screen=ShockwaveMath.project(p.point(),camera,new Matrix4f(),projection);
                    var center=ShockwaveMath.radialCenter(screen);
                    if(camera==REAR_VIEW_CAMERA){
                        if(screen!=null||center.x()!=.5F||center.y()!=.5F)throw new AssertionError("Rear view did not use screen center");
                    }else if(!center.equals(screen)||Math.abs(center.x()-.5)<.05){
                        throw new AssertionError("Front view did not use off-center emitter");
                    }
                    int clearChanged=0,outerChanged=0;
                    try(var actual=Screenshot.takeScreenshot(target)){
                        for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++){
                            double u=(x+.5)/target.width,v=1-(y+.5)/target.height;
                            double distance=Math.hypot((u-center.x())*target.width/target.height,v-center.y());
                            if(difference(clean.getPixelRGBA(x,y),actual.getPixelRGBA(x,y))>2){
                                if(distance<p.clearRadius()-.004)clearChanged++;
                                if(distance>.28)outerChanged++;
                            }
                        }
                        if(clearChanged!=0||outerChanged<1000)throw new AssertionError("Radial clear centre/streaks failed: "+clearChanged+"/"+outerChanged);
                        String screenshot = camera==REAR_VIEW_CAMERA ? "radial-behind.png"
                                : camera==Vec3.ZERO ? "radial-emitter.png" : "radial-camera-shift.png";
                        actual.writeToFile(folder.resolve(screenshot));
                        System.out.println("Radial GPU centre="+center.x()+","+center.y()+", protected changes="+clearChanged+", outer changes="+outerChanged);
                    }
                }
                var offRange=packet(new Vec3(0,0,-300),.9F);
                var rearRing=new ShockwavePacket(p.dimension(),new Vec3(-3,2,10),
                        18,40,1.5F,.7F,.9F,0,1,0,1,256,false,1,0,0);
                var inactive=List.of(rearRing,packet(p.point(),0),p,p,offRange);
                for(int test=0;test<inactive.size();test++){
                    pattern(target);
                    ShockwaveRenderer.render(target,List.of(new ShockwaveClient.Wave(inactive.get(test),0)),Vec3.ZERO,new Matrix4f(),projection,test==2?41:8,test==3?0:1);
                    try(var actual=Screenshot.takeScreenshot(target)){
                        for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++)
                            if(difference(clean.getPixelRGBA(x,y),actual.getPixelRGBA(x,y))>2)throw new AssertionError("Inactive radial changed image, case="+test);
                    }
                }
            }
            if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("Radial GPU error");
            var previous=ShockwaveMath.radialCenter(ShockwaveMath.project(p.point(),Vec3.ZERO,new Matrix4f(),projection));
            for(int step=1;step<=720;step++){
                var view=new Matrix4f().rotationY((float)Math.toRadians(step*.25));
                var center=ShockwaveMath.radialCenter(ShockwaveMath.project(p.point(),Vec3.ZERO,view,projection));
                if(Math.hypot(center.x()-previous.x(),center.y()-previous.y())>.04)
                    throw new AssertionError("Radial center jumped during camera rotation at step "+step);
                previous=center;
            }
            if(previous.x()!=.5F||previous.y()!=.5F)throw new AssertionError("Rotated rear view is not centered");
            System.out.println("Radial blur GPU passed: front/rear, smooth turn, clear center, outer streaks, rear ring/zero/expired/client-disabled/out of range and render state");
        }finally{target.destroyBuffers();ShockwaveRenderer.release();mc.getMainRenderTarget().bindWrite(true);RenderSystem.clearColor(0,0,0,1);}
    }
}
