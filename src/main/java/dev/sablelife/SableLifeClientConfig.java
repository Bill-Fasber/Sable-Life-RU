package dev.sablelife;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Local rendering compatibility only; never synchronized to a server or saved in a core. */
public final class SableLifeClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue LITTLE_TILES_RECOVERY;

    static {
        var builder = new ModConfigSpec.Builder();
        LITTLE_TILES_RECOVERY = builder
                .comment("Retry lost LittleTiles meshes on Sable structures, only with verified dependency versions. No terrain rebuilds or periodic scanning. Disable to retain native recovery behavior. A standalone sablelife_lt_recovery addon takes precedence over this built-in adapter.")
                .translation("config.sablelife.little_tiles_mesh_recovery")
                .define("littleTilesMeshRecovery", true);
        SPEC = builder.build();
    }

    public static boolean littleTilesMeshRecovery() {
        return !SPEC.isLoaded() || LITTLE_TILES_RECOVERY.get();
    }

    private SableLifeClientConfig() {}
}
