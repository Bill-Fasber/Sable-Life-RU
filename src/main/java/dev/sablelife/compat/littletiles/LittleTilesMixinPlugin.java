package dev.sablelife.compat.littletiles;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.nio.file.Files;
import java.util.*;

public final class LittleTilesMixinPlugin implements IMixinConfigPlugin {
    private Boolean compatible;

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (compatible == null) compatible = inspect();
        return compatible;
    }

    private static boolean inspect() {
        if (FMLLoader.getDist() != Dist.CLIENT || FMLLoader.getLoadingModList() == null) return false;
        var mods = new HashMap<String, String>();
        for (var mod : FMLLoader.getLoadingModList().getMods()) mods.put(mod.getModId(), mod.getVersion().toString());
        if (!mods.containsKey("littletiles")) return false;
        if (mods.containsKey("sablelife_lt_recovery")) {
            com.mojang.logging.LogUtils.getLogger().info("Sable Life skipped built-in LittleTiles mesh recovery because the standalone recovery addon is installed");
            return false;
        }
        boolean result = LittleTilesMixinContract.supported(mods.getOrDefault("littletiles", ""),
                mods.getOrDefault("creativecore", ""), mods.getOrDefault("sable", ""))
                && LittleTilesMixinContract.inspect(LittleTilesMixinPlugin::bytes);
        if (!result) com.mojang.logging.LogUtils.getLogger().warn("Sable Life skipped optional LittleTiles mesh recovery: dependency version or interface is unsupported");
        return result;
    }

    private static byte[] bytes(String name) {
        for (var mod : FMLLoader.getLoadingModList().getMods()) {
            try {
                var path = mod.getOwningFile().getFile().findResource(name + ".class");
                if (Files.isRegularFile(path)) return Files.readAllBytes(path);
            } catch (java.io.IOException | RuntimeException ignored) {}
        }
        return null;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
