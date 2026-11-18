package dev.sablelife.compat.littletiles;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import java.util.function.Function;

/** Inspect optional classes as bytes; no LittleTiles or client class is initialized here. */
public final class LittleTilesMixinContract {
    public static final String CACHE = "team/creative/littletiles/client/render/cache/BlockBufferCache";
    public static final String PIPELINE = "team/creative/littletiles/client/render/cache/pipeline/LittleRenderPipelineType";
    private static final String BASE = "team/creative/littletiles/client/render/";
    private static final String BUFFER = "L" + BASE + "cache/buffer/BufferCache;";
    private static final String MAP = "Lteam/creative/creativecore/common/util/type/map/ChunkLayerMap;";
    private static final String UPLOAD = "(L" + BASE + "cache/buffer/ChunkBufferUploader;L" + BASE + "cache/buffer/BufferCollection;" + BUFFER + ")" + BUFFER;

    public static boolean supported(String tiles, String core, String sable) {
        return Set.of("1.6.0-pre235", "1.6.0-pre239").contains(tiles)
                && core.equals("2.13.54") && Set.of("2.0.5", "2.0.6").contains(sable);
    }

    public static boolean inspect(Function<String, byte[]> reader) {
        try {
            var cache = read(CACHE, reader);
            var pipeline = read(PIPELINE, reader);
            var render = read(BASE + "block/BERenderManager", reader);
            var tiles = read("team/creative/littletiles/common/block/entity/BETiles", reader);
            var buffer = read(BASE + "cache/buffer/BufferCache", reader);
            if (cache == null || pipeline == null || render == null || tiles == null || buffer == null) return false;
            var upload = method(cache, "upload", "(Ljava/util/function/Function;Ljava/util/function/Function;)V", false);
            return method(cache, "getUploaded", "(Lnet/minecraft/client/renderer/RenderType;)" + BUFFER, false) != null
                    && method(cache, "setBuffers", "(" + MAP + ")V", false) != null
                    && method(cache, "setEmpty", "()V", false) != null
                    && cache.fields.stream().anyMatch(f -> f.name.equals("uploaded") && f.desc.equals(MAP) && (f.access & Opcodes.ACC_STATIC) == 0)
                    && upload != null && Arrays.stream(upload.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode call
                            && call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(PIPELINE) && call.name.equals("upload") && call.desc.equals(UPLOAD))
                    && method(pipeline, "compile", "(JLteam/creative/littletiles/common/block/entity/BETiles;Ljava/util/function/Function;Ljava/util/function/Function;)V", true) != null
                    && method(pipeline, "upload", UPLOAD, true) != null
                    && method(render, "buffers", "()L" + BASE + "cache/IBlockBufferCache;", false) != null
                    && method(render, "queue", "(ZZJ)V", false) != null
                    && tiles.fields.stream().anyMatch(f -> f.name.equals("render") && f.desc.equals("L" + BASE + "block/BERenderManager;") && (f.access & Opcodes.ACC_PUBLIC) != 0)
                    && method(buffer, "isInvalid", "()Z", false) != null
                    && method(buffer, "isEmpty", "()Z", false) != null;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static ClassNode read(String name, Function<String, byte[]> reader) {
        var bytes = reader.apply(name);
        if (bytes == null) return null;
        var node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static MethodNode method(ClassNode node, String name, String desc, boolean isStatic) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)
                && ((m.access & Opcodes.ACC_STATIC) != 0) == isStatic).findFirst().orElse(null);
    }

    private LittleTilesMixinContract() {}
}
