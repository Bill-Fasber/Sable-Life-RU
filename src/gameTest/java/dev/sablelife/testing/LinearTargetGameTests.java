package dev.sablelife.testing;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.item.CommandWandItem;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class LinearTargetGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,40,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());}
    private static VirtualModule movement(HeartBlockEntity c){var m=c.modules().add("linear_movement");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.EAST)));m.edit("trigger",0,"");m.edit("moveAim",1,"");m.edit("target",1,"");m.edit("moveTargetRange",10,"");return m;}
    private static net.minecraft.world.entity.animal.Pig pig(GameTestHelper h,Vec3 center){var pig=EntityType.PIG.create(h.getLevel());pig.setNoAi(true);pig.setNoGravity(true);pig.setPos(center.add(0,-pig.getBbHeight()/2,0));h.getLevel().addFreshEntity(pig);return pig;}

    @GameTest(template = "combat", batch = "command_wand_recipe")
    public static void commandWandCraftsFromThreeDiagonalIngredients(GameTestHelper h) {
        var level = h.getLevel();
        var table = h.absolutePos(new BlockPos(3, 2, 3));
        level.setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
        var player = TestActors.creative(level);
        player.setGameMode(GameType.SURVIVAL);
        var menu = level.getBlockState(table).getMenuProvider(level, table).createMenu(1, player.getInventory(), player);

        menu.getSlot(3).set(new ItemStack(Items.REDSTONE));
        menu.getSlot(5).set(new ItemStack(Items.AMETHYST_SHARD));
        h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "The recipe worked without the stick");
        menu.getSlot(7).set(new ItemStack(Items.STICK));
        menu.clicked(0, 0, ClickType.PICKUP, player);

        var result = menu.getCarried();
        h.assertTrue(result.is(SableLife.COMMAND_WAND.get()) && result.getCount() == 1,
                "The diagonal recipe did not craft one Command Wand");
        for (var fresh : List.of(result, SableLife.COMMAND_WAND.get().getDefaultInstance())) {
            h.assertTrue(CommandWandItem.enabledModulesMask(fresh) == CommandTarget.SUPPORTED_MODULES,
                    "A newly crafted or issued wand did not enable weapon follow");
            var reloaded = ItemStack.parse(level.registryAccess(), fresh.save(level.registryAccess())).orElseThrow();
            h.assertTrue(CommandWandItem.enabledModulesMask(reloaded) == CommandTarget.SUPPORTED_MODULES,
                    "The new default did not survive a save and reload");
        }
        h.assertTrue(menu.getSlot(3).getItem().isEmpty() && menu.getSlot(5).getItem().isEmpty()
                && menu.getSlot(7).getItem().isEmpty(), "Crafting did not consume the three ingredients");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "command_wand")
    public static void commandWandChecksPermissionsAndKeepsWorldPoint(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(3, 9, 3));
        level.setBlockAndUpdate(pos, SableLife.HEART.get().defaultBlockState());
        var core = (HeartBlockEntity) level.getBlockEntity(pos);
        var player = TestActors.creative(level);
        var stack = new ItemStack(SableLife.COMMAND_WAND.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setPos(pos.getCenter().add(0, -player.getEyeHeight(), 3));
        player.setYRot(180);
        player.setXRot(0);
        player.setShiftKeyDown(true);
        h.assertTrue(sendCommand(h, player, CommandWandItem.Operation.BIND, 0), "Could not bind from the looked-at core");
        player.setShiftKeyDown(false);

        h.assertTrue(sendCommand(h, player, CommandWandItem.Operation.DURATION, 120)
                && !sendCommand(h, player, CommandWandItem.Operation.DURATION, 121)
                && !sendCommand(h, player, CommandWandItem.Operation.DURATION, 0)
                && CommandWandItem.duration(stack) == 120, "Duration accepted an out-of-range value");
        h.assertTrue(sendCommand(h, player, CommandWandItem.Operation.DURATION, 1), "Could not save minimum duration");
        var ground = pos.south(2).below();
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        var direction = ground.getCenter().subtract(player.getEyePosition());
        player.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
        h.assertTrue(!sendCommand(h, player, CommandWandItem.Operation.DESTINATION, 0), "Dormant core accepted an order");
        core.health().activate();
        player.setGameMode(GameType.SURVIVAL);
        h.assertTrue(!sendCommand(h, player, CommandWandItem.Operation.DESTINATION, 0), "Survival used a creative binding");
        player.setGameMode(GameType.CREATIVE);
        h.assertTrue(sendCommand(h, player, CommandWandItem.Operation.DESTINATION, 0), "World destination packet failed");
        var order = core.modules().commandTarget().current(level);
        h.assertTrue(order.destination().equals(ground.above()), "Order did not use fixed world coordinates");
        level.removeBlock(ground, false);
        h.assertTrue(core.modules().commandTarget().current(level) == order, "Breaking the clicked block erased the order");
        player.setPos(player.position().add(0, 50, 0));
        player.setXRot(-90);
        h.assertTrue(CommandWandItem.pick(player).block().getType() == net.minecraft.world.phys.HitResult.Type.MISS,
                "Fixture did not aim at empty sky");
        h.runAfterDelay(4, () -> h.assertTrue(!sendCommand(h, player, CommandWandItem.Operation.DESTINATION, 0)
                && core.modules().commandTarget().current(level) == order, "A missed click erased the previous order"));
        h.runAfterDelay(21, () -> {
            h.assertTrue(core.modules().commandTarget().current(level) == null, "Command did not expire");
            h.succeed();
        });
    }

    @GameTest(template = "combat", batch = "command_wand")
    public static void commandWandSettingsUseLegacyDefaultsAndRejectInvalidRequests(GameTestHelper h) {
        var level = h.getLevel();
        var corePos = h.absolutePos(new BlockPos(3, 9, 3));
        level.setBlockAndUpdate(corePos, SableLife.HEART.get().defaultBlockState());
        var core = (HeartBlockEntity) level.getBlockEntity(corePos);
        var player = TestActors.creative(level);
        var stack = new ItemStack(SableLife.COMMAND_WAND.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setPos(corePos.getCenter().add(0, -player.getEyeHeight(), 3));
        player.setYRot(180);
        player.setXRot(0);
        player.setShiftKeyDown(true);
        h.assertTrue(sendCommand(h, player, CommandWandItem.Operation.BIND, 0), "Could not bind the wand");
        player.setShiftKeyDown(false);
        h.assertTrue(sendCommand(h, player, CommandWandItem.Operation.DURATION, 12)
                && CommandWandItem.enabledModulesMask(stack) == CommandTarget.SUPPORTED_MODULES,
                "A bound wand with only a duration did not use all four defaults");

        var ground = corePos.south(2).below();
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        var direction = ground.getCenter().subtract(player.getEyePosition());
        player.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
        core.health().activate();
        h.assertTrue(sendCommand(h, player, CommandWandItem.Operation.DESTINATION, 0), "Legacy wand order failed");
        var legacyOrder = core.modules().commandTarget().current(level);
        h.assertTrue(legacyOrder != null && legacyOrder.enabledModulesMask() == CommandTarget.ALL_MODULES
                && legacyOrder.destination().equals(ground.above()), "Legacy order did not route to all three modules");

        int mask = CommandTarget.TARGET_ALIGNMENT | CommandTarget.LINEAR_MOVEMENT;
        h.assertTrue(sendCommand(h, player, CommandWandItem.Input.settings(120, mask)), "Could not save settings");
        h.assertTrue(!sendCommand(h, player, CommandWandItem.Input.settings(121, CommandTarget.AUTO_FACING))
                && !sendCommand(h, player, CommandWandItem.Input.settings(0, CommandTarget.AUTO_FACING))
                && !sendCommand(h, player, new CommandWandItem.Input(20, 5))
                && CommandWandItem.duration(stack) == 120 && CommandWandItem.enabledModulesMask(stack) == mask,
                "An invalid settings packet changed the duration or module toggles");
        h.assertTrue(core.modules().commandTarget().current(level) == legacyOrder,
                "Changing wand settings altered the order already in progress");
        h.assertTrue(sendCommand(h, player, CommandWandItem.Operation.DURATION, 15)
                && CommandWandItem.enabledModulesMask(stack) == mask, "Legacy duration packet changed saved module toggles");
        h.runAfterDelay(4, () -> {
            h.assertTrue(sendCommand(h, player, CommandWandItem.Operation.DESTINATION, 0),
                    "Destination command did not use the saved toggles");
            var maskedOrder = core.modules().commandTarget().current(level);
            h.assertTrue(maskedOrder != null && maskedOrder.enabledModulesMask() == mask
                    && maskedOrder.expires() == level.getGameTime() + 15 * 20L,
                    "The accepted order did not use the configured toggles and duration");

            h.assertTrue(sendCommand(h, player, CommandWandItem.Input.settings(1, 0))
                    && CommandWandItem.enabledModulesMask(stack) == 0 && CommandWandItem.duration(stack) == 1,
                    "Could not save all three toggles disabled");
            h.runAfterDelay(4, () -> {
                h.assertTrue(!sendCommand(h, player, CommandWandItem.Operation.DESTINATION, 0)
                        && core.modules().commandTarget().current(level) == maskedOrder,
                        "An all-disabled wand replaced a previous order");
                h.assertTrue(sendCommand(h, player, CommandWandItem.Input.settings(1, mask))
                        && sendCommand(h, player, CommandWandItem.Operation.DESTINATION, 0),
                        "An all-disabled request started the cooldown");
                h.succeed();
            });
        });
    }

    @GameTest(template = "combat", batch = "command_wand_weapon_packets")
    public static void commandWandWeaponSettingsRoundTripAndStayOutOfDestinationOrders(GameTestHelper h) {
        var level = h.getLevel();
        var corePos = h.absolutePos(new BlockPos(3, 9, 3));
        level.setBlockAndUpdate(corePos, SableLife.HEART.get().defaultBlockState());
        var core = (HeartBlockEntity) level.getBlockEntity(corePos);
        var player = TestActors.creative(level);
        var wand = bindWand(h, player, corePos);
        int fullMask = CommandTarget.SUPPORTED_MODULES;
        h.assertTrue(CommandWandItem.enabledModulesMask(wand) == fullMask,
                "A wand without saved toggles did not enable weapon targeting");
        h.assertTrue(sendCommand(h, player, CommandWandItem.Input.settings(120, fullMask)),
                "The maximum duration and four enabled toggles failed to decode and apply");
        h.assertTrue(!sendCommand(h, player, CommandWandItem.Input.settings(121, fullMask))
                        && !sendCommand(h, player, new CommandWandItem.Input(20, 15))
                        && !sendCommand(h, player, new CommandWandItem.Input(255, 15)),
                "A forged opcode or out-of-range duration was accepted");
        var reloaded = ItemStack.parse(level.registryAccess(), wand.save(level.registryAccess())).orElseThrow();
        h.assertTrue(CommandWandItem.enabledModulesMask(reloaded) == fullMask && CommandWandItem.duration(reloaded) == 120,
                "Saving and reloading the wand did not preserve its validated settings");
        player.setItemInHand(InteractionHand.MAIN_HAND, reloaded);

        var pig = pig(h, corePos.getCenter().add(0, 0, 6));
        var eye = corePos.getCenter().add(0, 0, 3);
        var ground = corePos.south(2).below();
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        core.health().activate();
        aimWand(player, eye, pig.getBoundingBox().getCenter());
        h.assertTrue(sendCommand(h, player, CommandWandItem.Operation.ENTITY, 0), "Could not issue a mob order");
        var command = core.modules().commandTarget();
        var entityOrder = command.current(level, CommandTarget.WEAPON_TARGETING);
        h.assertTrue(entityOrder != null && entityOrder.entity() == pig && entityOrder.enabledModulesMask() == fullMask,
                "The mob order did not carry the saved weapon toggle");

        h.runAfterDelay(4, () -> {
            h.assertTrue(sendCommand(h, player, CommandWandItem.Input.settings(1, CommandTarget.WEAPON_TARGETING)),
                    "Could not save weapon-only settings");
            aimWand(player, eye, ground.getCenter());
            h.assertTrue(!sendCommand(h, player, CommandWandItem.Operation.DESTINATION, 0)
                            && command.current(level, CommandTarget.WEAPON_TARGETING) == entityOrder,
                    "A weapon-only wand accepted a terrain order or cleared its current weapon target");
            h.assertTrue(sendCommand(h, player, CommandWandItem.Input.settings(1, fullMask))
                            && sendCommand(h, player, CommandWandItem.Operation.DESTINATION, 0),
                    "The enabled movement modules did not receive the destination order");
            var destination = command.current(level, CommandTarget.LINEAR_MOVEMENT);
            h.assertTrue(destination != null && destination.destination().equals(ground.above())
                            && destination.enabledModulesMask() == CommandTarget.ALL_MODULES
                            && command.current(level, CommandTarget.WEAPON_TARGETING) == null,
                    "A right-click destination leaked into weapon targeting");
            pig.discard();
            h.succeed();
        });
    }

    @GameTest(template = "combat", batch = "command_wand")
    public static void commandWandOrdersShareOnePlayerCooldown(GameTestHelper h) {
        var level = h.getLevel();
        var firstPos = h.absolutePos(new BlockPos(3, 9, 3));
        var secondPos = firstPos.east(4);
        level.setBlockAndUpdate(firstPos, SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(secondPos, SableLife.HEART.get().defaultBlockState());
        var first = (HeartBlockEntity) level.getBlockEntity(firstPos);
        var second = (HeartBlockEntity) level.getBlockEntity(secondPos);
        var player = TestActors.creative(level);
        var otherPlayer = TestActors.creative(level);
        var firstWand = bindWand(h, player, firstPos);
        var secondWand = bindWand(h, player, secondPos);
        bindWand(h, otherPlayer, secondPos);
        first.health().activate();
        second.health().activate();

        var ground = firstPos.south(2).below();
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        var mob = pig(h, firstPos.getCenter().add(0, 0, 6));
        var eye = firstPos.getCenter().add(0, 0, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, firstWand);
        aimWand(player, eye, ground.getCenter());
        h.assertTrue(sendCommand(h, player, CommandWandItem.Operation.DESTINATION, 0),
                "First destination order was not accepted immediately");
        var initialOrder = first.modules().commandTarget().current(level);
        h.assertTrue(initialOrder != null && ground.above().equals(initialOrder.destination()),
                "First destination order did not reach its core");

        aimWand(player, eye, mob.getBoundingBox().getCenter());
        h.assertTrue(CommandWandItem.pick(player).entity() == mob, "Fixture did not aim at the entity");
        h.assertTrue(!sendCommand(h, player, CommandWandItem.Operation.ENTITY, 0)
                && first.modules().commandTarget().current(level) == initialOrder,
                "Switching from destination to entity bypassed the cooldown");
        player.setItemInHand(InteractionHand.MAIN_HAND, secondWand);
        for (int i = 0; i < 20; i++) {
            h.assertTrue(!sendCommand(h, player, CommandWandItem.Operation.ENTITY, 0),
                    "Switching wands or cores bypassed the cooldown");
        }
        h.assertTrue(second.modules().commandTarget().current(level) == null,
                "A throttled order reached the second core");
        h.assertTrue(sendCommand(h, player, CommandWandItem.Operation.DURATION, 11),
                "Order spam throttled duration editing");
        bindWand(h, player, secondPos);
        player.setItemInHand(InteractionHand.MAIN_HAND, secondWand);
        aimWand(player, eye, mob.getBoundingBox().getCenter());

        aimWand(otherPlayer, eye, mob.getBoundingBox().getCenter());
        h.assertTrue(sendCommand(h, otherPlayer, CommandWandItem.Operation.ENTITY, 0),
                "Another player's first order was throttled");
        var otherOrder = second.modules().commandTarget().current(level);
        h.assertTrue(otherOrder != null && otherOrder.entity() == mob,
                "The other player's entity order did not reach the second core");

        h.runAfterDelay(3, () -> {
            h.assertTrue(!sendCommand(h, player, CommandWandItem.Operation.ENTITY, 0)
                    && second.modules().commandTarget().current(level) == otherOrder,
                    "An order was accepted before four ticks elapsed");
        });
        h.runAfterDelay(4, () -> {
            var sky = new Vec3(eye.x, level.getMaxBuildHeight() + 1, eye.z);
            aimWand(player, sky, sky.add(0, 1, 0));
            var miss = CommandWandItem.pick(player);
            h.assertTrue(miss.block().getType() == net.minecraft.world.phys.HitResult.Type.MISS && miss.entity() == null,
                    "Fixture did not aim above the build limit");
            h.assertTrue(!sendCommand(h, player, CommandWandItem.Operation.DESTINATION, 0), "Accepted an empty-sky order");
            h.assertTrue(second.modules().commandTarget().current(level) == otherOrder,
                    "A missed order changed the second core");
            aimWand(player, eye, mob.getBoundingBox().getCenter());
            h.assertTrue(!sendCommand(h, player, CommandWandItem.Operation.ENTITY, 0),
                    "An authorized ray miss did not start the cooldown");
        });
        h.runAfterDelay(8, () -> {
            h.assertTrue(sendCommand(h, player, CommandWandItem.Operation.ENTITY, 0),
                    "Expected the next order four ticks after the ray miss");
            var entityOrder = second.modules().commandTarget().current(level);
            h.assertTrue(entityOrder != null && entityOrder != otherOrder && entityOrder.entity() == mob,
                    "The accepted entity order did not reach the second core");
            aimWand(player, eye, ground.getCenter());
            h.assertTrue(!sendCommand(h, player, CommandWandItem.Operation.DESTINATION, 0)
                    && second.modules().commandTarget().current(level) == entityOrder,
                    "Switching from entity to destination bypassed the cooldown");
            mob.discard();
            h.succeed();
        });
    }

    private static ItemStack bindWand(GameTestHelper h, ServerPlayer player, BlockPos core) {
        var wand = new ItemStack(SableLife.COMMAND_WAND.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        aimWand(player, core.getCenter().add(0, 0, 3), core.getCenter());
        player.setShiftKeyDown(true);
        h.assertTrue(sendCommand(h, player, CommandWandItem.Operation.BIND, 0), "Could not bind a test wand");
        player.setShiftKeyDown(false);
        return wand;
    }

    private static void aimWand(ServerPlayer player, Vec3 eye, Vec3 target) {
        player.setPos(eye.add(0, -player.getEyeHeight(), 0));
        var direction = target.subtract(eye);
        player.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
    }

    @GameTest(template = "combat", batch = "command_wand_aim")
    public static void commandsReplaceAimWithoutPoweringModules(GameTestHelper h) {
        var heart = fixture(h);
        var level = h.getLevel();
        var module = movement(heart);
        var anchor = module.anchors().getFirst();
        var center = anchor.world(heart, false);
        var facing = heart.modules().add("auto_facing");
        facing.anchors(List.of(anchor));
        facing.edit("target", 1, "");
        facing.edit("trigger", 0, "");
        facing.edit("facingCollision", 0, "");
        var pig = pig(h, center.add(0, 0, 5));
        var cow = EntityType.COW.create(level);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.setPos(center.add(4, 0, 0));
        level.addFreshEntity(cow);
        heart.relations().excludeMob("minecraft:pig");
        var alignment = heart.modules().add("navigation");
        alignment.anchors(List.of(anchor));
        alignment.edit("target", 1, "");
        alignment.edit("lineOfSight", 1, "");
        var navigation = new TargetNavigation();
        navigation.tick(level, heart, alignment, new HashMap<>());
        h.assertTrue(navigation.name(alignment.id()).equals(cow.getName().getString()), "Whitelist did not filter normal alignment");

        alignment.edit("radius", 2, "");
        facing.edit("facingRange", 2, "");
        module.edit("moveTargetRange", 2, "");
        navigation.tick(level, heart, alignment, new HashMap<>());
        h.assertTrue(navigation.name(alignment.id()).isEmpty() && ModuleLinearMovement.plan(heart, module) == null,
                "Automatic targeting ignored the configured range");
        var saved = module.save();
        heart.modules().commandTarget().entity(pig, level.getGameTime() + 20);
        var signals = new HashMap<ModuleSignals.Face, Integer>();
        navigation.tick(level, heart, alignment, signals);
        var plan = ModuleLinearMovement.plan(heart, module);
        h.assertTrue(plan.commanded() && plan.delta().z > 0
                && navigation.name(alignment.id()).equals(pig.getName().getString())
                && signals.values().stream().anyMatch(value -> value > 0), "Explicit entity did not override the range and whitelist");
        var obstruction = BlockPos.containing(center.add(0, 0, 2));
        level.setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
        alignment.edit("lineOfSight", 0, "");
        navigation.tick(level, heart, alignment, new HashMap<>());
        h.assertTrue(navigation.name(alignment.id()).isEmpty(), "Command bypassed alignment line of sight");
        level.removeBlock(obstruction, false);
        navigation.tick(level, heart, alignment, new HashMap<>());
        h.assertTrue(navigation.name(alignment.id()).equals(pig.getName().getString()), "Clear line of sight did not restore the command");
        h.assertTrue(saved.equals(module.save()), "Temporary target changed saved module settings");

        heart.health().activate();
        heart.wireless().master(true);
        var controller = new ModuleAutoFacing();
        controller.tick(heart, false);
        h.assertTrue(controller.state(facing.id()).equals("tracking"), "Auto Facing did not consume the command");
        var direction = TargetNavigation.worldFront(heart, facing);
        h.assertTrue(direction.z > .05, "Auto Facing turned toward the automatic target");
        module.edit("trigger", 2, "");
        module.edit("channel", 7, "");
        module.edit("moveAim", 2, "");
        var destination = BlockPos.containing(center).north(4);
        heart.modules().commandTarget().destination(destination, level.getGameTime() + 20);
        plan = ModuleLinearMovement.plan(heart, module);
        h.assertTrue(plan.goal().equals(destination.getCenter()) && HeartModules.status(module, heart).equals("valid")
                && !HeartModules.triggered(module, heart), "Coordinate did not supply an aim, or it powered the trigger");
        h.assertTrue(module.arenaCenter() == null, "Coordinate command rewrote the permanent mark");
        heart.modules().clearRuntime();
        h.assertTrue(heart.modules().commandTarget().current(level) == null, "Core reset retained the command");
        cow.discard();
        pig.discard();
        h.succeed();
    }

    @GameTest(template = "combat", batch = "command_wand_aim")
    public static void commandWandTogglesRouteEntityAndDestinationIndependently(GameTestHelper h) {
        var heart = fixture(h);
        var level = h.getLevel();
        var movement = movement(heart);
        var anchor = movement.anchors().getFirst();
        var center = anchor.world(heart, false);
        var facing = heart.modules().add("auto_facing");
        facing.anchors(List.of(anchor));
        facing.edit("trigger", 0, "");
        facing.edit("facingCollision", 0, "");
        facing.edit("facingRange", 2, "");
        var alignment = heart.modules().add("navigation");
        alignment.anchors(List.of(anchor));
        alignment.edit("lineOfSight", 1, "");
        alignment.edit("radius", 2, "");
        movement.edit("moveTargetRange", 2, "");
        var pig = pig(h, center.add(0, 0, 6));
        var destination = BlockPos.containing(center).south(6);
        heart.relations().excludeMob("minecraft:pig");
        heart.health().activate();
        heart.wireless().master(true);

        var order = heart.modules().commandTarget();
        long expires = level.getGameTime() + 20;
        for (int mask : new int[] { CommandTarget.ALL_MODULES, CommandTarget.TARGET_ALIGNMENT,
                CommandTarget.AUTO_FACING, CommandTarget.LINEAR_MOVEMENT,
                CommandTarget.TARGET_ALIGNMENT | CommandTarget.LINEAR_MOVEMENT }) {
            boolean useAlignment = (mask & CommandTarget.TARGET_ALIGNMENT) != 0;
            boolean useFacing = (mask & CommandTarget.AUTO_FACING) != 0;
            boolean useMovement = (mask & CommandTarget.LINEAR_MOVEMENT) != 0;
            var navigation = new TargetNavigation();

            movement.edit("moveAim", 1, "");
            order.entity(pig, expires, mask);
            navigation.tick(level, heart, alignment, new HashMap<>());
            var entityFacing = new ModuleAutoFacing();
            entityFacing.tick(heart, false);
            var entityPlan = ModuleLinearMovement.plan(heart, movement);
            h.assertTrue(navigation.name(alignment.id()).equals(useAlignment ? pig.getName().getString() : "")
                    && entityFacing.state(facing.id()).equals(useFacing ? "tracking" : "no_target")
                    && (useMovement ? entityPlan != null && entityPlan.commanded() : entityPlan == null),
                    "Entity order reached the wrong modules for mask " + mask);

            movement.edit("moveAim", 2, "");
            order.destination(destination, expires, mask);
            navigation.tick(level, heart, alignment, new HashMap<>());
            var destinationFacing = new ModuleAutoFacing();
            destinationFacing.tick(heart, false);
            var destinationPlan = ModuleLinearMovement.plan(heart, movement);
            h.assertTrue(navigation.name(alignment.id()).equals(useAlignment ? destination.toShortString() : "")
                    && destinationFacing.state(facing.id()).equals(useFacing ? "tracking" : "no_target")
                    && HeartModules.status(movement, heart).equals(useMovement ? "valid" : "unmarked")
                    && (useMovement ? destinationPlan != null && destinationPlan.commanded()
                    && destinationPlan.goal().equals(destination.getCenter()) : destinationPlan == null),
                    "Destination order reached the wrong modules for mask " + mask);
        }
        pig.discard();
        h.succeed();
    }

    @GameTest(template = "combat", batch = "command_wand_aim")
    public static void extendedTargetRangesKeepSavedValues(GameTestHelper h) {
        var heart = fixture(h);
        for (var entry : Map.of("navigation", "radius", "auto_facing", "facingRange",
                "linear_movement", "moveTargetRange").entrySet()) {
            var module = heart.modules().add(entry.getKey());
            var field = entry.getValue();
            module.edit(field, 128, "");
            var saved = VirtualModule.load(module.save());
            h.assertTrue(saved.value(field) == 128, "Legacy range changed for " + entry.getKey());
            saved.edit(field, 256, "");
            h.assertTrue(VirtualModule.load(saved.save()).value(field) == 256,
                    "Extended range did not survive a save for " + entry.getKey());
            saved.edit(field, 257, "");
            h.assertTrue(saved.value(field) == 256, "Over-limit range did not clamp for " + entry.getKey());
        }
        h.succeed();
    }

    private static boolean sendCommand(GameTestHelper h, ServerPlayer player,
                                       CommandWandItem.Operation operation, int seconds) {
        return sendCommand(h, player, new CommandWandItem.Input(operation, seconds));
    }

    private static boolean sendCommand(GameTestHelper h, ServerPlayer player,
                                       CommandWandItem.Input request) {
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            CommandWandItem.Input.CODEC.encode(buffer, request);
            var decoded = CommandWandItem.Input.CODEC.decode(buffer);
            h.assertTrue(decoded.equals(request) && buffer.readableBytes() == 0, "Command payload failed to round-trip");
            return CommandWandItem.handle(player, decoded);
        } finally {
            buffer.release();
        }
    }

    @GameTest(template = "combat", batch = "command_wand")
    public static void commandWandLimitsBurstAndRefillsByTick(GameTestHelper h) {
        var player = TestActors.creative(h.getLevel());
        var stack = new ItemStack(SableLife.COMMAND_WAND.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        for (int i = 0; i < 20; i++) {
            h.assertTrue(sendCommand(h, player, CommandWandItem.Operation.DURATION, 7), "Burst rejected early");
        }
        h.assertTrue(!sendCommand(h, player, CommandWandItem.Operation.DURATION, 8)
                && CommandWandItem.duration(stack) == 7, "Throttled request changed the item");
        h.runAfterDelay(1, () -> {
            h.assertTrue(sendCommand(h, player, CommandWandItem.Operation.DURATION, 9)
                    && !sendCommand(h, player, CommandWandItem.Operation.DURATION, 10)
                    && CommandWandItem.duration(stack) == 9, "Expected one more request after one tick");
            h.succeed();
        });
    }

    @GameTest(template = "combat", batch = "linear_world")
    public static void MarkedWorldDestinationKeepsLegacyAnchorAndSettings(GameTestHelper h) {
        var core = fixture(h);
        var module = movement(core);
        var original = module.save();
        var legacy = VirtualModule.load(original);
        h.assertTrue(legacy.value("moveAim") == 1 && legacy.anchors().equals(module.anchors()), "Legacy mark or aim changed");
        var author = TestActors.creative(h.getLevel());
        var start = SableBodyProbe.toWorld(h.getLevel(), core.getBlockPos().getCenter());
        author.setPos(start.add(0, 1, 0));
        var ground = BlockPos.containing(start.add(3, -1, 0));
        h.getLevel().setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        var menu = new HeartDashboardMenu(1, author.getInventory(), core);
        var request = new ModuleNetworking.Action(1, core.modules().revision(), module.id(), "edit", "moveAim", 2, "");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ModuleNetworking.Action.CODEC.encode(buffer, request);
            var decoded = ModuleNetworking.Action.CODEC.decode(buffer);
            h.assertTrue(decoded.equals(request) && menu.action(author, decoded.revision(), decoded.module(),
                    decoded.operation(), decoded.key(), decoded.value(), decoded.text()), "World aim edit did not round-trip");
        } finally {
            buffer.release();
        }
        h.assertTrue(HeartModules.status(module, core).equals("unmarked"), "An old origin became the new destination");
        h.assertTrue(menu.action(author, core.modules().revision(), module.id(), "mark", "", 0, "")
                && !MarkingSessions.select(author, core.getBlockPos(), Direction.UP)
                && MarkingSessions.select(author, ground, Direction.UP), "Could not mark fixed terrain through the editor");
        var saved = VirtualModule.load(module.save());
        h.assertTrue(saved.arenaCenter().equals(ground.above().getCenter()) && saved.anchors().equals(legacy.anchors())
                && saved.value("moveTargetRange") == legacy.value("moveTargetRange"), "Round-trip lost the destination or old settings");
        h.assertTrue(!ModulePreset.portable(saved.save()).contains("ArenaCenter"), "Portable preset kept the world coordinate");
        saved.edit("moveAim", 1, "");
        h.assertTrue(saved.anchors().equals(legacy.anchors()), "Switching aim lost the old mark");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "linear_world_travel", timeoutTicks = 45)
    public static void WorldTravelStopsAtCoreDestination(GameTestHelper h) {
        worldMove(h, true, false);
    }

    @GameTest(template = "combat", batch = "linear_world_teleport", timeoutTicks = 45)
    public static void WorldTeleportStopsAtCoreDestination(GameTestHelper h) {
        worldMove(h, false, false);
    }

    @GameTest(template = "combat", batch = "linear_world_blocked", timeoutTicks = 45)
    public static void WorldTravelStopsAtObstacle(GameTestHelper h) {
        worldMove(h, true, true);
    }

    private static void worldMove(GameTestHelper h, boolean travel, boolean blocked) {
        var core = fixture(h);
        var level = h.getLevel();
        var module = core.modules().add("linear_movement");
        module.edit("moveAim", 2, "");
        module.edit("linearMode", travel ? 1 : 0, "");
        module.edit("trigger", 0, "");
        module.edit("chargeTime", 0, "");
        module.edit("travelSpeed", 20, "");
        module.edit("interval", 1000, "");
        var start = SableBodyProbe.toWorld(level, core.getBlockPos().getCenter());
        var goal = start.add(0, blocked ? 0 : 3, 6);
        module.arenaCenter(goal, "minecraft:the_nether");
        h.assertTrue(ModuleLinearMovement.plan(core, module) == null, "Accepted a different dimension");
        module.arenaCenter(goal, level.dimension().location().toString());
        h.assertTrue(HeartModules.status(module, core).equals("valid") && module.anchors().isEmpty(),
                "World movement still needs a body mark");
        if (travel) {
            module.edit("moveGravity", 1, "");
            h.assertTrue(ModuleLinearMovement.plan(core, module).delta().y == 0, "Gravity route included a vertical destination");
            module.edit("moveGravity", 0, "");
        }
        if (blocked) {
            var wall = BlockPos.containing(start.add(0, 0, 3));
            for (int dy = -2; dy <= 1; dy++) level.setBlockAndUpdate(wall.above(dy), Blocks.STONE.defaultBlockState());
        }
        core.wireless().master(true);
        core.tryActivate(TestActors.creative(level));
        for (int tick = 1; tick <= 12; tick++) {
            int checkTick = tick;
            h.runAfterDelay(tick, () -> {
                core.modules().tick(core);
                var end = SableBodyProbe.toWorld(level, core.getBlockPos().getCenter());
                var status = core.modules().snapshot(core, false).getList("Modules", 10).getCompound(0).getString("MovementPhase");
                if (checkTick < 12 && !status.equals(blocked ? "blocked" : "arrived")) return;
                if (blocked) h.assertTrue(status.equals("blocked") && end.z < goal.z - 2 && end.z > start.z, "Wall was ignored: " + end + " / " + status);
                else h.assertTrue(status.equals("arrived") && end.distanceTo(goal) < 0.35, "Core missed fixed point: " + end + " / " + goal + " / " + status);
                module.edit("enabled", 0, "");
                h.succeed();
            });
        }
    }
    @GameTest(template="combat",batch="linear_target_height") public static void HorizontalTargetModeCanOptIntoTargetHeight(GameTestHelper h){var c=fixture(h);var m=movement(c);var start=m.anchors().getFirst().world(c,true);var target=pig(h,start.add(4,4,0));var horizontal=ModuleLinearMovement.plan(c,m);h.assertTrue(horizontal.delta().distanceTo(new Vec3(2,0,0))<.00001,"Default target mode drove grounded body vertically");m.edit("moveTargetVertical",1,"");var full=ModuleLinearMovement.plan(c,m);h.assertTrue(full.delta().y>0&&Math.abs(full.goal().distanceTo(target.getBoundingBox().getCenter())-2)<.00001,"3D target height did not retain gap");target.discard();h.succeed();}
    @GameTest(template="combat",batch="linear_target_travel",timeoutTicks=40) public static void TargetTravelKeepsGoalAndHurtOnceLastsForTargetRange(GameTestHelper h){var c=fixture(h);var m=movement(c);m.edit("linearMode",1,"");m.edit("moveTargetVertical",1,"");m.edit("moveTargetRange",32,"");m.edit("travelSpeed",20,"");m.edit("chargeTime",0,"");m.edit("trigger",5,"");var start=m.anchors().getFirst().world(c,true);var target=pig(h,start.add(20,0,0));var goal=ModuleLinearMovement.plan(c,m).goal();c.tryActivate(TestActors.creative(h.getLevel()));c.damage(1);c.modules().tick(c);target.setPos(target.position().add(10,0,0));for(int tick=1;tick<=19;tick++){int delay=tick;h.runAfterDelay(delay,()->c.modules().tick(c));}h.runAfterDelay(20,()->{var end=m.anchors().getFirst().world(c,true);h.assertTrue(Math.hypot(end.x-goal.x,end.z-goal.z)<.05,"Target Travel ended early or chased target: "+end+" / "+goal);h.assertTrue(c.modules().snapshot(c,false).getList("Modules",10).getCompound(0).getString("MovementPhase").equals("arrived"),"Target travel never arrived");m.edit("enabled",0,"");target.discard();h.succeed();});}
    @GameTest(template="combat",batch="linear_target_math") public static void GapStopsBeforeTargetWithoutRetreat(GameTestHelper h){var start=new Vec3(2,3,4);var delta=ModuleLinearMovement.towards(start,start.add(6,8,0),2);h.assertTrue(delta.distanceTo(new Vec3(4.8,6.4,0))<.00001,"Target-N geometry wrong");h.assertTrue(ModuleLinearMovement.towards(start,start.add(1,0,0),2).equals(Vec3.ZERO)&&ModuleLinearMovement.towards(start,start,0).equals(Vec3.ZERO),"Close target caused retreat or NaN");h.succeed();}
    @GameTest(template="combat",batch="linear_target_selection") public static void TargetSelectionUsesRangeAndSavedGap(GameTestHelper h){var c=fixture(h);var m=movement(c);var start=m.anchors().getFirst().world(c,true);var near=pig(h,start.add(4,0,0));var far=pig(h,start.add(8,0,0));var plan=ModuleLinearMovement.plan(c,m);h.assertTrue(plan!=null&&plan.delta().distanceTo(new Vec3(2,0,0))<.00001,"Nearest target/gap not used");var copy=VirtualModule.load(m.save());h.assertTrue(copy.value("moveAim")==1&&copy.value("moveStopDistance")==2&&copy.value("moveTargetRange")==10,"Target mode settings lost");near.discard();far.discard();m.edit("moveTargetRange",1,"");h.assertTrue(ModuleLinearMovement.plan(c,m)==null,"Missing target produced displacement");h.succeed();}
    @GameTest(template="combat",batch="linear_target_freeze",timeoutTicks=20) public static void TeleportUsesTargetSnapshotThroughCharging(GameTestHelper h){var c=fixture(h);var m=movement(c);m.edit("chargeTime",2,"");m.edit("moveTargetVertical",1,"");m.edit("moveObstruction",1,"");var start=m.anchors().getFirst().world(c,true);var target=pig(h,start.add(4,0,0));var goal=ModuleLinearMovement.plan(c,m).goal();c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);target.setPos(target.position().add(5,0,0));h.runAfterDelay(3,()->c.modules().tick(c));h.runAfterDelay(5,()->{var end=m.anchors().getFirst().world(c,true);h.assertTrue(end.distanceTo(goal)<.2,"Teleport chased moving target or lost frozen world endpoint: "+end+" / "+goal+" phase="+c.modules().snapshot(c,false).getList("Modules",10).getCompound(0).getString("MovementPhase")+" alive="+c.health().state()+" trigger="+HeartModules.triggered(m,c));m.edit("enabled",0,"");target.discard();h.succeed();});}
    @GameTest(template="combat",batch="linear_target_legacy") public static void FixedModeAndAnglesRemainLegacyDefault(GameTestHelper h){var c=fixture(h);var m=c.modules().add("linear_movement");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.EAST)));var plan=ModuleLinearMovement.plan(c,m);h.assertTrue(m.value("moveAim")==0&&plan.goal()==null&&plan.delta().distanceTo(LinearMovementGeometry.direction(c,m).scale(m.value("moveDistance")))<.00001,"Legacy direction/distance changed");h.succeed();}
}
