package dev.sablelife.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;

import java.util.OptionalDouble;

/** Shared viewer-only line weight; never changes Minecraft's global line render type. */
public abstract class PreviewLineStyle extends RenderType {
    private static final float WEIGHT=1.4F;
    private PreviewLineStyle(){super("sablelife_preview_lines",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,1536,false,false,()->{},()->{});}

    static LineStateShard widthState(float base,boolean scaleWithWindow){
        return new LineStateShard(OptionalDouble.of(base*WEIGHT)){
            @Override public void setupRenderState(){
                RenderSystem.lineWidth(scaleWithWindow ? windowWidth(base) : base * WEIGHT);
            }
        };
    }

    private static float windowWidth(float base) {
        return Math.max(base, Minecraft.getInstance().getWindow().getWidth() / 1920F * base) * WEIGHT;
    }

    private static final float WORLD_WIDTH = 2.5F;
    private static final float COMMAND_WAND_WIDTH = WORLD_WIDTH * 1.25F;
    private static final RenderType WORLD_LINES = worldLines("sablelife_preview_lines", WORLD_WIDTH);
    private static final RenderType COMMAND_WAND_LINES = worldLines("sablelife_command_wand_lines", COMMAND_WAND_WIDTH);
    private static final RenderType COMMAND_WAND_LATE_LINES = create("sablelife_command_wand_late_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, false, false,
            CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER).setLineState(widthState(COMMAND_WAND_WIDTH,true))
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));

    private static RenderType worldLines(String name, float width) {
        return create(name,DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,1536,
            CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER).setLineState(widthState(width,true))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(ITEM_ENTITY_TARGET).setWriteMaskState(COLOR_DEPTH_WRITE).setCullState(NO_CULL).createCompositeState(false));
    }

    public static RenderType worldLines(){return WORLD_LINES;}
    static RenderType commandWandLines(boolean late) { return late ? COMMAND_WAND_LATE_LINES : COMMAND_WAND_LINES; }
}
