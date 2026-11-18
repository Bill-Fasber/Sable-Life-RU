package dev.sablelife.testing;

import com.mojang.logging.LogUtils;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprintFiles;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolService;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.sablelife.combat.BodyCombat;
import dev.sablelife.compat.letitwalk.WalkerLegDamage;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import dev.sablelife.ownership.BodyOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@GameTestHolder("sablelife_hitbox_tests")
@PrefixGameTestTemplate(false)
public final class WalkerBlueprintGameTests {
    @GameTest(template = "combat", batch = "walker_blueprint_roundtrip", timeoutTicks = 240)
    public static void PhotomancyCopyRoutesLegDamageToTheNewHeart(GameTestHelper test) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("sable_schematic_api")
                || ModList.get().getModContainerById("let_it_walk")
                .filter(mod -> mod.getModInfo().getVersion().toString().equals("0.1.5")).isEmpty()
                || Boolean.getBoolean("sablelife.walkerExpectDisabled")) {
            test.succeed();
            return;
        }

        var level = test.getLevel();
        var origin = test.absolutePos(new BlockPos(8, 100, 8));
        WalkerLegDamageGameTests.buildWalker(level, origin);
        test.runAfterDelay(12, () -> {
            var source = WalkerLegDamageGameTests.walker(test, origin);
            test.assertTrue(source.targets().stream().allMatch(pos -> WalkerLegDamage.owner(level, pos) == source.heart()),
                    "The original walker could not receive damage on every leg segment");
            var name = "walker_roundtrip_" + UUID.randomUUID();
            var center = source.hull().logicalPose().transformPosition(source.heart().getBlockPos().getCenter());
            var destination = center.add(48, 0, 0);
            var destinationChunk = new ChunkPos(BlockPos.containing(destination));
            var forced = new HashSet<ChunkPos>();
            for (int x = destinationChunk.x - 1; x <= destinationChunk.x + 1; x++) {
                for (int z = destinationChunk.z - 1; z <= destinationChunk.z + 1; z++) {
                    if (level.setChunkForced(x, z, true)) forced.add(new ChunkPos(x, z));
                    level.getChunk(x, z);
                }
            }
            var container = SubLevelContainer.getContainer(level);
            var originalIds = new HashSet<UUID>();
            container.getAllSubLevels().forEach(body -> originalIds.add(body.getUniqueId()));
            try {
                var saved = BlueprintToolService.save(level.getServer(), level, center, 24, name);
                test.assertTrue(saved.success(), "Photomancy save failed: " + saved.message());
                int expectedBodies = source.legs().size() + 1;
                test.assertTrue(saved.affectedSubLevels() == expectedBodies,
                        "Saved " + saved.affectedSubLevels() + " sublevels, expected " + expectedBodies);
                var placed = BlueprintToolService.load(level.getServer(), level, destination, name);
                test.assertTrue(placed.success() && placed.affectedSubLevels() == expectedBodies,
                        "Photomancy paste did not place the full walker: " + placed.message());
            } finally {
                try {
                    Files.deleteIfExists(SableBlueprintFiles.path(level.getServer(), name));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            var restoredBlocks = container.getAllSubLevels().stream().filter(body -> !originalIds.contains(body.getUniqueId()))
                    .flatMap(body -> WalkerLegDamageGameTests.blockEntities(body).stream()).toList();
            var heart = restoredBlocks.stream().filter(HeartBlockEntity.class::isInstance).map(HeartBlockEntity.class::cast)
                    .findFirst().orElseThrow();
            var walker = restoredBlocks.stream().filter(be -> be.getClass().getName().equals("dev.letitwalk.content.WalkerCoreBlockEntity"))
                    .findFirst().orElseThrow();
            test.assertTrue(heart.health().state() == HeartState.DORMANT, "Photomancy copy should start dormant");
            test.assertTrue(!heart.creatureId().equals(source.heart().creatureId()), "Copy shares the original Heart identity");
            test.runAfterDelay(20, () -> {
                heart.tryActivate(TestActors.creative(level));
                test.assertTrue(heart.health().state() == HeartState.ALIVE, "The pasted Heart could not activate. Removed="
                        + heart.isRemoved() + ", connected=" + SableBodyProbe.inspect(level, heart.getBlockPos()));
                test.runAfterDelay(20, () -> {
                    try {
                        verifyDamage(test, source, heart, walker, originalIds);
                    } finally {
                        for (var chunk : forced) level.setChunkForced(chunk.x, chunk.z, false);
                    }
                });
            });
        });
    }

    private static void verifyDamage(GameTestHelper test, WalkerLegDamageGameTests.WalkerFixture source,
                                     HeartBlockEntity heart, BlockEntity walker, HashSet<UUID> originalIds) {
        var level = test.getLevel();
        test.assertTrue(Sable.HELPER.getContaining(level, walker.getBlockPos()) == Sable.HELPER.getContaining(level, heart.getBlockPos()),
                "The restored Walker Core and Heart are in different hulls");
        var segments = new HashSet<SubLevel>();
        ((BlockEntitySubLevelActor) walker).sable$getLoadingDependencies().forEach(segments::add);
        test.assertTrue(segments.size() == source.legs().size()
                        && segments.stream().noneMatch(leg -> originalIds.contains(leg.getUniqueId())),
                "Restored Walker Core tracks " + segments.size() + " new leg segments, expected " + source.legs().size());
        var positions = segments.stream().map(leg -> WalkerLegDamageGameTests.solidBlock(level, leg)).toList();
        var ownership = BodyOwnership.get(level);
        test.assertTrue(positions.stream().noneMatch(pos -> ownership.owner(pos) != null),
                "Copy unexpectedly registered walker legs as native Sable Life body blocks");
        float originalHealth = source.heart().health().current();
        float initialHealth = heart.health().current();
        for (var pos : positions) {
            test.assertTrue(WalkerLegDamage.owner(level, pos) == heart, "Restored leg did not route to the new Heart: " + pos);
            shoot(arrow(test), List.of(pos));
        }
        test.assertTrue(heart.health().current() == initialHealth - 6 * positions.size(),
                "A separate arrow on each segment did not produce the expected total damage");
        float afterSeparateHits = heart.health().current();
        shoot(arrow(test), positions);
        test.assertTrue(heart.health().current() == afterSeparateHits - 6, "One projectile on several leg segments charged more than once");
        var aim = positions.stream().map(pos -> WalkerWeaponGameTests.aimAt(test, pos))
                .filter(java.util.Objects::nonNull).findFirst().orElseThrow(() -> new IllegalStateException("No exposed walker leg block"));
        var flying = arrow(test);
        flying.setPos(aim.start());
        flying.setDeltaMovement(aim.velocity());
        flying.tick();
        test.assertTrue(heart.health().current() == afterSeparateHits - 14,
                "An actual arrow flight did not deal 8 HP to the new Heart. Remaining HP=" + heart.health().current());
        test.assertTrue(source.heart().health().current() == originalHealth, "Shooting the copy damaged the original Heart");
        LogUtils.getLogger().info("Walker blueprint round trip: {} restored segments, {} -> {} HP, source remained at {} HP",
                segments.size(), initialHealth, heart.health().current(), originalHealth);
        test.succeed();
    }

    private static Arrow arrow(GameTestHelper test) {
        var arrow = new Arrow(test.getLevel(), 0, 0, 0, new ItemStack(Items.ARROW), null);
        arrow.setBaseDamage(3);
        arrow.setDeltaMovement(2, 0, 0);
        return arrow;
    }

    private static void shoot(Arrow arrow, List<BlockPos> positions) {
        for (var pos : positions) {
            BodyCombat.onProjectileImpact(new ProjectileImpactEvent(arrow, new BlockHitResult(pos.getCenter(), Direction.UP, pos, false)));
        }
    }
}
