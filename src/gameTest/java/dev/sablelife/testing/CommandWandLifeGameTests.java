package dev.sablelife.testing;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.item.CommandWandItem;
import dev.sablelife.item.CommandWandItem.Input;
import dev.sablelife.item.CommandWandItem.Operation;
import dev.sablelife.module.CommandTarget;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;

@GameTestHolder("sablelife_control_tests")
@PrefixGameTestTemplate(false)
public final class CommandWandLifeGameTests {
    @GameTest(template = "combat", batch = "command_wand_defaults")
    public static void weaponFollowDefaultsOnAndPreservesSavedOff(GameTestHelper h) {
        var wand = SableLife.COMMAND_WAND.get();
        var plainStack = new ItemStack(wand);
        h.assertTrue(CommandWandItem.enabledModulesMask(plainStack) == CommandTarget.SUPPORTED_MODULES
                        && CommandWandItem.enabledModulesMask(wand.getDefaultInstance()) == CommandTarget.SUPPORTED_MODULES,
                "Weapon follow was disabled on a fresh wand");
        CustomData.update(DataComponents.CUSTOM_DATA, plainStack,
                tag -> tag.putInt("SableLifeCommandModules", CommandTarget.ALL_MODULES));
        var reloaded = ItemStack.parseOptional(h.getLevel().registryAccess(),
                (net.minecraft.nbt.CompoundTag) plainStack.save(h.getLevel().registryAccess()));
        h.assertTrue(CommandWandItem.enabledModulesMask(reloaded) == CommandTarget.ALL_MODULES,
                "Reloading a saved wand re-enabled weapon follow");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "command_wand_life")
    public static void wandTargetsAttachedBlocksWithoutHitboxes(GameTestHelper h) {
        var level = h.getLevel();
        var sourcePos = h.absolutePos(new BlockPos(3, 40, 3));
        var targetPos = h.absolutePos(new BlockPos(8, 40, 3));
        placeCore(level, sourcePos);
        placeCore(level, targetPos);
        var player = TestActors.creative(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SableLife.COMMAND_WAND.get()));
        aim(player, sourcePos.getCenter());
        player.setShiftKeyDown(true);
        h.assertTrue(send(h, player, Operation.BIND), "Could not bind the source core");
        player.setShiftKeyDown(false);
        var source = assemble(h, sourcePos);
        var target = assemble(h, targetPos);
        h.assertTrue(source.activateForRespawn(), "Could not activate the source core");
        h.runAfterDelay(4, () -> verifyTarget(h, player, source, target));
    }

    private static void verifyTarget(GameTestHelper h, ServerPlayer player, HeartBlockEntity source, HeartBlockEntity target) {
        var level = h.getLevel();
        var targetBlock = target.getBlockPos().east();
        aim(player, SableBodyProbe.toWorld(level, targetBlock.getCenter()));
        var selection = CommandWandItem.pick(player);
        h.assertTrue(selection.block().getBlockPos().equals(targetBlock) && selection.entity() == null,
                "The test ray did not hit the non-core structure block");
        h.assertTrue(!send(h, player, Operation.ENTITY), "Accepted a dormant Life");
        h.assertTrue(target.activateForRespawn(), "Could not activate the target core");
        h.runAfterDelay(4, () -> verifyActivatedTarget(h, player, source, target));
    }

    private static void verifyActivatedTarget(GameTestHelper h, ServerPlayer player,
                                              HeartBlockEntity source, HeartBlockEntity target) {
        var level = h.getLevel();
        aim(player, SableBodyProbe.toWorld(level, target.getBlockPos().east().getCenter()));
        h.assertTrue(send(h, player, Operation.ENTITY), "Could not command a Life without entity hitboxes");

        var order = source.modules().commandTarget().current(level);
        h.assertTrue(target.creatureId().equals(order.lifeId()) && target.health().current() == target.health().maximum(),
                "Command stored the wrong Life or damaged it");
        h.assertTrue(target.creatureId().equals(source.modules().commandTarget().weaponTarget(level)),
                "A fresh wand's left-click order did not reach weapon targeting");
        h.runAfterDelay(4, () -> {
            aim(player, SableBodyProbe.toWorld(level, source.getBlockPos().east().getCenter()));
            h.assertTrue(!send(h, player, Operation.ENTITY) && source.modules().commandTarget().current(level) == order,
                    "Selecting the bound Life replaced the current order");

            var oldPosition = order.resolve(level, false).point();
            var body = (ServerSubLevel) Sable.HELPER.getContaining(level, target.getBlockPos());
            SubLevelPhysicsSystem.get(level).getPipeline().teleport(body,
                    new Vector3d(body.logicalPose().position()).add(5, 0, 0), new Quaterniond(body.logicalPose().orientation()));
            h.assertTrue(order.resolve(level, false).point().distanceTo(oldPosition.add(5, 0, 0)) < 1e-4,
                    "Life order did not follow the target's world position");
            h.assertTrue(target.resetForCreative(player) && source.modules().commandTarget().current(level) == null,
                    "Releasing the target retained the Life order");
            h.succeed();
        });
    }

    private static void placeCore(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos.east().below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(), Blocks.IRON_BLOCK.defaultBlockState());
    }

    private static HeartBlockEntity assemble(GameTestHelper h, BlockPos pos) {
        var level = h.getLevel();
        var bounds = new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY(), pos.getZ());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos, pos.east()), bounds);
        return (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
    }

    private static void aim(ServerPlayer player, Vec3 point) {
        player.setPos(point.add(0, -player.getEyeHeight(), 3));
        player.setYRot(180);
        player.setXRot(0);
    }

    private static boolean send(GameTestHelper h, ServerPlayer player, Operation operation) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            var request = new Input(operation, 0);
            Input.CODEC.encode(buffer, request);
            var decoded = Input.CODEC.decode(buffer);
            h.assertTrue(request.equals(decoded) && buffer.readableBytes() == 0, "Command packet failed to round-trip");
            return CommandWandItem.handle(player, decoded);
        } finally {
            buffer.release();
        }
    }
}
