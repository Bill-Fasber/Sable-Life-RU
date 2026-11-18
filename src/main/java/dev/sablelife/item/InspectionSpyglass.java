package dev.sablelife.item;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.InspectionSnapshot;
import dev.sablelife.ownership.LoadedCoreIndex;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = SableLife.MOD_ID)
public final class InspectionSpyglass extends Item {
    private static final String BINDING = "SableLifeInspection";
    private static final Map<ServerPlayer, Session> sessions = new WeakHashMap<>();
    private static final Map<ServerPlayer, Integer> lastOpen = new WeakHashMap<>();

    private record Session(HeartBlockEntity core, UUID id, int revision) {}

    public InspectionSpyglass(Properties properties) { super(properties); }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return useOn(context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (context.getHand() != InteractionHand.MAIN_HAND || player == null || !player.isSecondaryUseActive()
                || !(level.getBlockEntity(pos) instanceof HeartBlockEntity core)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer server) || !supportsInspection(server)
                || !player.canInteractWithBlock(pos, 1) || !CoreRemoteItem.canAccess(server, core)) {
            player.displayClientMessage(Component.translatable("message.sablelife.core_remote_denied"), true);
            return InteractionResult.FAIL;
        }
        var binding = new CompoundTag();
        binding.putUUID("Core", core.bindRemote());
        binding.putString("Dimension", level.dimension().location().toString());
        CustomData.update(DataComponents.CUSTOM_DATA, context.getItemInHand(), data -> data.put(BINDING, binding));
        player.displayClientMessage(Component.translatable("message.sablelife.inspection_bound", core.displayName()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player holder, InteractionHand hand) {
        var stack = holder.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(holder instanceof ServerPlayer player) || !supportsInspection(player)) return InteractionResultHolder.fail(stack);
        var id = boundCore(stack);
        if (id == null) {
            player.displayClientMessage(Component.translatable("tooltip.sablelife.inspection_bind"), true);
            return InteractionResultHolder.fail(stack);
        }
        Integer previous = lastOpen.get(player);
        if (previous != null && player.tickCount - previous < 10) return InteractionResultHolder.consume(stack);
        lastOpen.put(player, player.tickCount);
        var core = inDimension(stack, level.dimension().location().toString())
                ? LoadedCoreIndex.findRemote(player.serverLevel(), id) : null;
        if (core == null || !CoreRemoteItem.canAccess(player, core)) {
            stop(player, id);
            player.displayClientMessage(Component.translatable(core == null
                    ? "message.sablelife.core_remote_unavailable" : "message.sablelife.core_remote_denied"), true);
            return InteractionResultHolder.fail(stack);
        }
        sessions.put(player, new Session(core, id, core.modules().revision()));
        InspectionSnapshot.capture(id, core, true).send(player);
        return InteractionResultHolder.consume(stack);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        var session = sessions.get(player);
        if (session == null || !session.id.equals(boundCore(player.getMainHandItem()))) return;
        var core = session.core;
        if (!session.id.equals(core.remoteId()) || !CoreRemoteItem.canAccess(player, core)) {
            stop(player, session.id);
        } else if (session.revision != core.modules().revision()) {
            sessions.put(player, new Session(core, session.id, core.modules().revision()));
            InspectionSnapshot.capture(session.id, core, false).send(player);
        }
    }

    private static void stop(ServerPlayer player, UUID id) {
        sessions.remove(player);
        new InspectionSnapshot(id, false, false, List.of()).send(player);
    }

    private static boolean supportsInspection(ServerPlayer player) {
        return NetworkRegistry.hasChannel(player.connection, InspectionSnapshot.TYPE.id());
    }

    public static UUID boundCore(ItemStack stack) {
        var binding = binding(stack);
        return binding.hasUUID("Core") ? binding.getUUID("Core") : null;
    }

    public static boolean inDimension(ItemStack stack, String dimension) {
        return binding(stack).getString("Dimension").equals(dimension);
    }

    private static CompoundTag binding(ItemStack stack) {
        if (!(stack.getItem() instanceof InspectionSpyglass)) return new CompoundTag();
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.contains(BINDING) ? data.copyTag().getCompound(BINDING) : new CompoundTag();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(boundCore(stack) == null ? "tooltip.sablelife.inspection_bind"
                : "tooltip.sablelife.inspection_open").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
