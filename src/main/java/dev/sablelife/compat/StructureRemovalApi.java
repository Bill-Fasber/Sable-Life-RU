package dev.sablelife.compat;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import java.lang.reflect.*;
import java.util.*;

/** Same native path as Photomancy; verified Toolgun coordinator also cleans its own rope/control state. */
public final class StructureRemovalApi {
    private static final class Toolgun {
        static final Method coordinator = lookup();
        private static final String COORDINATOR = "com.enxv.aeronauticsstructuretool.blueprint.lifecycle.SubLevelRemovalCoordinator";

        private static Method lookup() {
            if(!ModList.get().getModContainerById("create_aeronautics_toolgun").map(c->c.getModInfo().getVersion().toString().equals("0.3.6")).orElse(false))return null;
            try {
                var loader = StructureRemovalApi.class.getClassLoader();
                var coordinatorClass = Class.forName(COORDINATOR, false, loader);
                var method = coordinatorClass.getMethod("remove", ServerLevel.class, ServerSubLevelContainer.class, Collection.class);
                if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class || !method.canAccess(null)) {
                    throw new NoSuchMethodException("Expected an accessible static void remove method");
                }
                Class.forName(COORDINATOR, true, loader);
                return method;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                com.mojang.logging.LogUtils.getLogger().warn("Toolgun 0.3.6 structure removal API is unavailable. Using native Sable removal.", failure);
                return null;
            }
        }
    }
    public static void remove(ServerLevel level,ServerSubLevel body) {
        removeAll(level,List.of(body));
    }
    /** Validate and detach every mounted payload before removing any member of the group. */
    public static void removeAll(ServerLevel level,Collection<ServerSubLevel> bodies) {
        var coordinator = Toolgun.coordinator;
        var container=SubLevelContainer.getContainer(level);
        for(var body:bodies)if(container==null||container.getSubLevel(body.getUniqueId())!=body)throw new IllegalStateException("Structure is no longer registered");
        for(var body:bodies)BodyDecorationCleanup.discardForBody(level,body);
        // Only controllers in this exact plot, with persisted ownership and an active death/part job, qualify.
        for(var body:bodies)for(var holder:body.getPlot().getLoadedChunks())for(var controller:List.copyOf(holder.getChunk().getBlockEntities().values()))
            if(dev.ryanhcode.sable.Sable.HELPER.getContaining(level,controller.getBlockPos())==body)MountedContraptionCleanup.discardForDeath(controller);
        if(coordinator==null){for(var body:bodies)if(container.getSubLevel(body.getUniqueId())==body)container.removeSubLevel(body,SubLevelRemovalReason.REMOVED);return;}
        try {coordinator.invoke(null,level,container,List.copyOf(bodies));}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof RuntimeException cause)throw cause;throw new IllegalStateException("Toolgun structure removal failed",failure.getCause());}
        catch(IllegalAccessException failure){throw new IllegalStateException("Toolgun structure removal is inaccessible",failure);}
    }
    private StructureRemovalApi() {}
}
