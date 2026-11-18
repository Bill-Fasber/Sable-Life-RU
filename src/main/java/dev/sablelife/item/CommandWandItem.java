package dev.sablelife.item;

import dev.ryanhcode.sable.Sable;
import dev.sablelife.SableLife;
import dev.sablelife.combat.EntityHitbox;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import dev.sablelife.module.AuthoringRateLimit;
import dev.sablelife.module.CommandTarget;
import dev.sablelife.module.MarkingSessions;
import dev.sablelife.ownership.BodyOwnership;
import dev.sablelife.ownership.LoadedCoreIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class CommandWandItem extends Item {
    public static final int MIN_SECONDS = 1;
    public static final int MAX_SECONDS = 120;
    private static final int DEFAULT_SECONDS = 10;
    private static final int PICK_RANGE = 128;
    private static final int ORDER_COOLDOWN_TICKS = 4;
    private static final Map<ServerPlayer, Integer> LAST_ORDER_TICK = new WeakHashMap<>();
    private static final String BINDING = "SableLifeCommandCore";
    private static final String DURATION = "SableLifeCommandDuration";
    private static final String MODULES = "SableLifeCommandModules";
    // Opcodes 4–11 keep the original settings encoding. 12–19 include weapon targeting.
    private static final int SETTINGS_OPERATION_BASE = 4;

    public CommandWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getDefaultInstance() {
        var stack = super.getDefaultInstance();
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putInt(MODULES, CommandTarget.SUPPORTED_MODULES));
        return stack;
    }

    public record Selection(BlockHitResult block, LivingEntity entity) {}

    public static BlockHitResult pickBlock(Player player, double range) {
        var eye = SableBodyProbe.toWorld(player.level(), player.getEyePosition());
        return pickBlock(player, eye, eye.add(player.getLookAngle().scale(range)));
    }

    private static BlockHitResult pickBlock(Player player, Vec3 eye, Vec3 end) {
        return player.level().clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    private static double loadedRayLength(Player player, Vec3 eye, Vec3 direction) {
        // Walk chunk columns so a long aim ray never asks the server to load terrain.
        double chunkSize = SectionPos.SECTION_SIZE;
        var start = new Vec3(eye.x / chunkSize, .5, eye.z / chunkSize);
        var step = new Vec3(direction.x / chunkSize, 0, direction.z / chunkSize);
        var end = start.add(step.scale(PICK_RANGE));
        var chunks = player.level().getChunkSource();
        if (chunks.getChunkNow(SectionPos.blockToSectionCoord(eye.x), SectionPos.blockToSectionCoord(eye.z)) == null) return 0;
        return BlockGetter.traverseBlocks(start.add(step.scale(.001)), end, chunks, (cache, column) -> {
            if (cache.getChunkNow(column.getX(), column.getZ()) != null) return null;
            double enterX = chunkEntry(start.x, step.x, column.getX());
            double enterZ = chunkEntry(start.z, step.z, column.getZ());
            return Math.max(0, Math.max(enterX, enterZ) - .001);
        }, cache -> (double) PICK_RANGE);
    }

    private static double chunkEntry(double start, double step, int column) {
        if (step == 0) return 0;
        double edge = step > 0 ? column : column + 1.0;
        return (edge - start) / step;
    }

    public static Selection pick(Player player) {
        var level = player.level();
        var eye = SableBodyProbe.toWorld(level, player.getEyePosition());
        var direction = player.getLookAngle();
        var end = eye.add(direction.scale(loadedRayLength(player, eye, direction)));
        var block = pickBlock(player, eye, end);
        if (block.getType() == HitResult.Type.BLOCK) end = SableBodyProbe.toWorld(level, block.getLocation());
        var entity = ProjectileUtil.getEntityHitResult(player, eye, end, new AABB(eye, end).inflate(1),
                candidate -> candidate instanceof LivingEntity living && living.isAlive() && living.isPickable()
                        && !living.isSpectator() && !(living instanceof EntityHitbox),
                eye.distanceToSqr(end));
        return new Selection(block, entity == null ? null : (LivingEntity) entity.getEntity());
    }

    public static int duration(ItemStack stack) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.contains(DURATION) ? Math.clamp(data.copyTag().getInt(DURATION), MIN_SECONDS, MAX_SECONDS) : DEFAULT_SECONDS;
    }

    public static int enabledModulesMask(ItemStack stack) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(MODULES, Tag.TAG_INT)) return CommandTarget.SUPPORTED_MODULES;
        int modules = data.getInt(MODULES);
        return validModulesMask(modules) ? modules : CommandTarget.ALL_MODULES;
    }

    private static boolean validModulesMask(int modules) {
        return (modules & ~CommandTarget.SUPPORTED_MODULES) == 0;
    }

    private static CompoundTag binding(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(BINDING);
    }

    public enum Operation { ENTITY, DESTINATION, BIND, DURATION }

    public record Input(int operation, int seconds) implements CustomPacketPayload {
        public static final Type<Input> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID, "command_wand"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Input> CODEC = StreamCodec.of(
                (buffer, input) -> buffer.writeByte(input.operation()).writeByte(input.seconds()),
                buffer -> new Input(buffer.readUnsignedByte(), buffer.readUnsignedByte()));

        public Input(Operation operation, int seconds) {
            this(operation.ordinal(), seconds);
        }

        public static Input settings(int seconds, int enabledModulesMask) {
            if (!validModulesMask(enabledModulesMask)) throw new IllegalArgumentException("Invalid Command Wand module mask");
            return new Input(SETTINGS_OPERATION_BASE + enabledModulesMask, seconds);
        }

        @Override public Type<Input> type() {
            return TYPE;
        }
    }

    public static boolean handle(ServerPlayer player, Input input) {
        var stack = player.getMainHandItem();
        int settingsMask = input.operation() - SETTINGS_OPERATION_BASE;
        boolean settings = input.operation() >= SETTINGS_OPERATION_BASE && validModulesMask(settingsMask);
        if (!(stack.getItem() instanceof CommandWandItem) || !player.isAlive() || player.isSpectator()
                || player.containerMenu != player.inventoryMenu || MarkingSessions.isMarking(player)
                || input.operation() < 0 || !settings && input.operation() >= Operation.values().length) return false;
        boolean order = input.operation() == Operation.ENTITY.ordinal() || input.operation() == Operation.DESTINATION.ordinal();
        int tick = player.serverLevel().getServer().getTickCount();
        Integer lastOrder = order ? LAST_ORDER_TICK.get(player) : null;
        if (lastOrder != null && tick - lastOrder < ORDER_COOLDOWN_TICKS || !AuthoringRateLimit.allow(player, 4)) return false;
        if (settings || input.operation() == Operation.DURATION.ordinal()) {
            if (input.seconds() < MIN_SECONDS || input.seconds() > MAX_SECONDS) return false;
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.putInt(DURATION, input.seconds());
                if (settings) tag.putInt(MODULES, settingsMask);
            });
            return true;
        }
        var operation = Operation.values()[input.operation()];
        if (operation == Operation.BIND) return bind(player, stack);

        var level = player.serverLevel();
        var binding = binding(stack);
        if (!binding.hasUUID("Core")) return feedback(player, "unbound");
        var core = binding.getString("Dimension").equals(level.dimension().location().toString())
                ? LoadedCoreIndex.findRemote(level, binding.getUUID("Core")) : null;
        if (core == null) return feedback(player, "unavailable");
        if (!CoreRemoteItem.canAccess(player, core)) return feedback(player, "denied");
        if (!core.operational()) return feedback(player, "inactive");

        int seconds = duration(stack);
        int modules = enabledModulesMask(stack);
        if (modules == 0) return feedback(player, "no_modules");
        if (operation == Operation.DESTINATION && (modules & CommandTarget.ALL_MODULES) == 0) {
            return feedback(player, "no_destination_modules");
        }
        LAST_ORDER_TICK.put(player, tick);
        long expires = level.getGameTime() + seconds * 20L;
        var selection = pick(player);
        if (operation == Operation.ENTITY) {
            Component name;
            if (selection.entity() != null) {
                core.modules().commandTarget().entity(selection.entity(), expires, modules);
                name = selection.entity().getDisplayName();
            } else {
                var target = pickLife(level, selection.block(), core);
                if (target == null) return feedback(player, "no_entity", PICK_RANGE);
                core.modules().commandTarget().life(target.creatureId(), expires, modules);
                name = target.displayName();
            }
            message(player, ChatFormatting.GREEN, "entity",
                    name.copy().withStyle(ChatFormatting.AQUA), durationText(seconds));
        } else {
            var pos = selection.block().getBlockPos();
            if (selection.entity() != null || selection.block().getType() != HitResult.Type.BLOCK
                    || Sable.HELPER.getContaining(level, pos) != null || !level.hasChunkAt(pos)
                    || !level.isInWorldBounds(pos.above()) || !level.getWorldBorder().isWithinBounds(pos)) {
                return feedback(player, "no_destination", PICK_RANGE);
            }
            var destination = pos.above();
            core.modules().commandTarget().destination(destination, expires, modules);
            message(player, ChatFormatting.GREEN, "destination",
                    Component.literal(destination.toShortString()).withStyle(ChatFormatting.AQUA), durationText(seconds));
        }
        return true;
    }

    private static HeartBlockEntity pickLife(ServerLevel level, BlockHitResult hit, HeartBlockEntity source) {
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        var pos = hit.getBlockPos();
        var body = Sable.HELPER.getContaining(level, pos);
        if (body == null || body.isRemoved()) return null;
        var ownership = BodyOwnership.get(level);
        var lifeId = ownership.owner(pos);
        if (lifeId == null || lifeId.equals(source.creatureId()) || ownership.part(pos) != null) return null;
        var target = ownership.loadedHeart(level, lifeId);
        if (target == null || target.health().state() != HeartState.ALIVE || !target.operational()
                || !SableBodyProbe.connected(level, target.getBlockPos(), body.getUniqueId())) return null;
        return target;
    }

    private static boolean bind(ServerPlayer player, ItemStack stack) {
        var hit = pickBlock(player, player.blockInteractionRange() + 1);
        var pos = hit.getBlockPos();
        if (hit.getType() != HitResult.Type.BLOCK || !player.canInteractWithBlock(pos, 1)
                || !(player.level().getBlockEntity(pos) instanceof HeartBlockEntity core)
                || !CoreRemoteItem.canAccess(player, core)) return feedback(player, "denied");

        var binding = new CompoundTag();
        binding.putUUID("Core", core.bindRemote());
        binding.putString("Dimension", player.level().dimension().location().toString());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(BINDING, binding));
        message(player, ChatFormatting.AQUA, "bound", core.displayName());
        return true;
    }

    private static boolean feedback(Player player, String key, Object... arguments) {
        message(player, key.equals("denied") ? ChatFormatting.RED : ChatFormatting.YELLOW, key, arguments);
        return false;
    }

    private static Component durationText(int seconds) {
        return Component.literal(Integer.toString(seconds)).withStyle(ChatFormatting.GOLD);
    }

    private static void message(Player player, ChatFormatting color, String key, Object... arguments) {
        player.displayClientMessage(Component.translatable("message.sablelife.command_wand_" + key, arguments)
                .withStyle(color), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean bound = binding(stack).hasUUID("Core");
        tooltip.add(Component.translatable("tooltip.sablelife.command_wand_" + (bound ? "bound" : "unbound"), duration(stack))
                .withStyle(bound ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sablelife.command_wand_use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sablelife.command_wand_settings").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sablelife.command_wand_weapon_scope").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.sablelife.command_wand_weapon_target").withStyle(ChatFormatting.GRAY));
    }
}
