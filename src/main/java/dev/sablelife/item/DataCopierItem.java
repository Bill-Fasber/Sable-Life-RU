package dev.sablelife.item;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.AuthoringLimits;
import dev.sablelife.module.MarkingSessions;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public final class DataCopierItem extends Item {
    private static final String KEY = "SableLifeConfiguration";
    private static final String SUMMARY = "SableLifeConfigurationSummary";
    private static final int ITEM_PAYLOAD_LIMIT = 1_000_000;
    public DataCopierItem(Properties properties) { super(properties); }
    private static CustomData customData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
    }

    private static CompoundTag payload(CompoundTag custom) {
        if (!custom.contains(KEY, Tag.TAG_BYTE_ARRAY)) return custom.getCompound(KEY);
        byte[] bytes = custom.getByteArray(KEY);
        if (bytes.length > ITEM_PAYLOAD_LIMIT) return new CompoundTag();
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(AuthoringLimits.MAX_CONFIGURATION_BYTES));
        } catch (IOException | net.minecraft.nbt.NbtException invalid) {
            return new CompoundTag();
        }
    }

    private static boolean store(ItemStack stack, CompoundTag copied) {
        int size = copied.sizeInBytes();
        if (size > AuthoringLimits.MAX_CONFIGURATION_BYTES
                || !AuthoringLimits.compound(copied, AuthoringLimits.MAX_CONFIGURATION_BYTES)) return false;
        var custom = customData(stack).copyTag();
        custom.remove(SUMMARY);
        if (size <= ITEM_PAYLOAD_LIMIT) {
            custom.put(KEY, copied);
        } else {
            var output = new ByteArrayOutputStream();
            try {
                NbtIo.writeCompressed(copied, output);
            } catch (IOException invalid) {
                return false;
            }
            if (output.size() > ITEM_PAYLOAD_LIMIT) return false;
            custom.putByteArray(KEY, output.toByteArray());
            var summary = new CompoundTag();
            summary.putString("SourceName", copied.getString("SourceName"));
            summary.putInt("ModuleCount", copied.getList("Modules", Tag.TAG_COMPOUND).size());
            custom.put(SUMMARY, summary);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        return true;
    }

    @Override public boolean isFoil(ItemStack stack) { return customData(stack).contains(KEY); }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) { return transfer(context); }
    @Override public InteractionResult useOn(UseOnContext context) { return transfer(context); }
    private InteractionResult transfer(UseOnContext context) {
        var player = context.getPlayer(); var level = context.getLevel();
        if (player == null || !(level.getBlockEntity(context.getClickedPos()) instanceof HeartBlockEntity heart)) return InteractionResult.PASS;
        if (!heart.canAccessEditor(player)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.sablelife.copier_permission"), true);
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!dev.sablelife.module.AuthoringRateLimit.allow(player,20)) return InteractionResult.FAIL;
        var stack = context.getItemInHand();
        if (!isFoil(stack) || player.isShiftKeyDown()) {
            var copied = heart.copyConfiguration(); copied.putString("SourceName", heart.displayName().getString());
            if (!store(stack, copied)) {
                player.displayClientMessage(Component.translatable("message.sablelife.copier_too_large"), true);
                return InteractionResult.FAIL;
            }
            player.displayClientMessage(Component.translatable("message.sablelife.copier_copied", heart.displayName(), copied.getList("Modules", 10).size()), true);
        } else {
            if (!heart.pasteConfiguration(player, payload(customData(stack).copyTag()))) {
                player.displayClientMessage(Component.translatable("message.sablelife.copier_locked"), true); return InteractionResult.FAIL;
            }
            MarkingSessions.cancel(player);
            player.displayClientMessage(Component.translatable("message.sablelife.copier_pasted", heart.modules().all().size()), false);
        }
        var point = SableBodyProbe.toWorld(level, context.getClickedPos().getCenter());
        level.playSound(null, point.x, point.y, point.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .7F, 1.3F);
        return InteractionResult.CONSUME;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean loaded = isFoil(stack);
        tooltip.add(Component.translatable(loaded ? "tooltip.sablelife.copier_loaded" : "tooltip.sablelife.copier_empty").withStyle(loaded ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        if (loaded) {
            var custom = customData(stack).copyTag();
            boolean compressed = custom.contains(KEY, Tag.TAG_BYTE_ARRAY);
            var data = custom.getCompound(compressed ? SUMMARY : KEY);
            int count = compressed ? data.getInt("ModuleCount") : data.getList("Modules", Tag.TAG_COMPOUND).size();
            tooltip.add(Component.translatable("tooltip.sablelife.copier_source", data.getString("SourceName"), count).withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("tooltip.sablelife.copier_use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sablelife.copier_marks").withStyle(ChatFormatting.GRAY));
    }
}
