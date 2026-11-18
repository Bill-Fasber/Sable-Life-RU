package dev.sablelife.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("sablelife_arena_blueprint_restart_tests")
@PrefixGameTestTemplate(false)
public final class ArenaBlueprintRestartGameTests {
    @GameTestGenerator
    public static List<TestFunction> restartFixture() {
        String phase = System.getenv("SABLELIFE_ARENA_RESTART_TEST");
        if (!"prepare".equals(phase) && !"verify".equals(phase))
            return List.of(new TestFunction("arena_blueprint_capacity","arena_summon_limit_and_cleanup",
                    "sablelife_arena_blueprint_restart_tests:combat",370,0,true,
                    BlueprintSpawnGameTests::arenaRejectsOutsideAndSeventeenthSummonAndResetsQuota));

        return List.of(new TestFunction("arena_blueprint_restart","arena_restart_"+phase,
                "sablelife_arena_blueprint_restart_tests:combat",500,0,true,helper->{
                    var manifest = helper.getLevel().getServer().getServerDirectory().resolve("arena-restart-fixture.nbt");
                    try {
                        if (phase.equals("prepare")) BlueprintSpawnGameTests.prepareArenaRestart(helper,manifest);
                        else BlueprintSpawnGameTests.verifyArenaRestart(helper,manifest);
                    } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                }));
    }
}
