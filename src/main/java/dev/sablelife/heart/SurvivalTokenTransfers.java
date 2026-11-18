package dev.sablelife.heart;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** An item carries a one-use claim against the world that issued it. */
final class SurvivalTokenTransfers extends SavedData {
    record Claim(long balance, CompoundTag fuel) {
        boolean isEmpty() {
            return balance <= 0 && fuel.isEmpty();
        }
    }

    private final Map<UUID, Claim> unclaimed = new HashMap<>();

    static SurvivalTokenTransfers get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(SurvivalTokenTransfers::new, SurvivalTokenTransfers::load), "sablelife_token_transfers");
    }

    UUID issue(Claim claim) {
        UUID id = UUID.randomUUID();
        unclaimed.put(id, claim);
        setDirty();
        return id;
    }

    Claim redeem(UUID id) {
        Claim claim = unclaimed.remove(id);
        if (claim != null) setDirty();
        return claim;
    }

    private static SurvivalTokenTransfers load(CompoundTag tag, HolderLookup.Provider registries) {
        var transfers = new SurvivalTokenTransfers();
        for (var entry : tag.getList("Claims", 10)) {
            var data = (CompoundTag) entry;
            var claim = new Claim(data.getLong("Balance"), data.getCompound("Fuel"));
            if (data.hasUUID("Id") && !claim.isEmpty())
                transfers.unclaimed.put(data.getUUID("Id"), claim);
        }
        return transfers;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        unclaimed.forEach((id, transfer) -> {
            var claim = new CompoundTag();
            claim.putUUID("Id", id);
            claim.putLong("Balance", transfer.balance());
            if (!transfer.fuel().isEmpty()) claim.put("Fuel", transfer.fuel().copy());
            entries.add(claim);
        });
        tag.put("Claims", entries);
        return tag;
    }
}
