package dev.sablelife.compat.littletiles;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

final class LittleTilesMixinContractTest {
    private static Map<String, byte[]> classes(String version) throws Exception {
        var result = new HashMap<String, byte[]>();
        try (var jar = new ZipFile(Path.of(".dev/littletiles-regression/mods/LittleTiles_BETA_v1.6.0-" + version + "_mc1.21.1.jar").toFile())) {
            for (var entry : Collections.list(jar.entries())) if (entry.getName().endsWith(".class"))
                result.put(entry.getName().replace(".class", ""), jar.getInputStream(entry).readAllBytes());
        }
        return result;
    }

    private static void edit(Map<String, byte[]> data, String target, Consumer<ClassNode> mutation) {
        var node = new ClassNode(); new ClassReader(data.get(target)).accept(node, 0); mutation.accept(node);
        var writer = new ClassWriter(0); node.accept(writer); data.put(target, writer.toByteArray());
    }

    @Test void verifiedVersionsMatchWithoutLoadingThirdPartyClasses() throws Exception {
        for (String version : List.of("pre235", "pre239")) assertTrue(LittleTilesMixinContract.inspect(classes(version)::get), version);
    }

    @Test void absentCorruptOrUnreadableOptionalClassesAreSkipped() {
        assertFalse(LittleTilesMixinContract.inspect(name -> null));
        assertFalse(LittleTilesMixinContract.inspect(name -> new byte[]{1, 2, 3}));
        assertFalse(LittleTilesMixinContract.inspect(name -> { throw new IllegalStateException(); }));
    }

    @Test void changedMethodAndShadowFieldAreSkipped() throws Exception {
        var data = classes("pre239");
        edit(data, LittleTilesMixinContract.CACHE, n -> n.fields.stream().filter(f -> f.name.equals("uploaded")).forEach(f -> f.desc = "Ljava/lang/Object;"));
        assertFalse(LittleTilesMixinContract.inspect(data::get));
        data = classes("pre239");
        edit(data, LittleTilesMixinContract.PIPELINE, n -> n.methods.stream().filter(m -> m.name.equals("compile")).forEach(m -> m.access &= ~Opcodes.ACC_STATIC));
        assertFalse(LittleTilesMixinContract.inspect(data::get));
    }

    @Test void changedUploadInvocationIsSkippedEvenWhenMethodsStillExist() throws Exception {
        var data = classes("pre239");
        edit(data, LittleTilesMixinContract.CACHE, n -> {
            for (var method : n.methods) if (method.name.equals("upload")) for (var instruction : method.instructions.toArray())
                if (instruction instanceof MethodInsnNode call && call.owner.equals(LittleTilesMixinContract.PIPELINE) && call.name.equals("upload")) call.name = "differentUpload";
        });
        assertFalse(LittleTilesMixinContract.inspect(data::get));
    }

    @Test void changedNativeRebuildQueueIsSkipped() throws Exception {
        var data = classes("pre239");
        edit(data, "team/creative/littletiles/client/render/block/BERenderManager", n -> n.methods.removeIf(m -> m.name.equals("queue")));
        assertFalse(LittleTilesMixinContract.inspect(data::get));
    }

    @Test void unsupportedDependencyVersionsAreSkipped() {
        assertTrue(LittleTilesMixinContract.supported("1.6.0-pre239", "2.13.54", "2.0.6"));
        assertFalse(LittleTilesMixinContract.supported("1.6.0-pre999", "2.13.54", "2.0.6"));
        assertFalse(LittleTilesMixinContract.supported("1.6.0-pre239", "2.13.55", "2.0.6"));
        assertFalse(LittleTilesMixinContract.supported("1.6.0-pre239", "2.13.54", "2.0.7"));
    }
}
