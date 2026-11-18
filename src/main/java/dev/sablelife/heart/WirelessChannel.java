package dev.sablelife.heart;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** One independent waveform, measured from the Heart's common activation clock. */
public final class WirelessChannel {
    public static final int MAX_TIME = 12000;
    public static final int MAX_OUTPUT_FACES = 64;
    public static final String OUTPUT_FACES_TAG = "OutputFaces";
    public static final String OUTPUT_FACE_NAME = "Name";
    private final java.util.Map<dev.sablelife.module.ModuleAnchor,String> faceNames = new java.util.HashMap<>();

    public String outputFaceName(int index) {
        return faceNames.getOrDefault(outputFaces.get(index), "");
    }

    public boolean renameOutputFace(int index, String name) {
        if (index < 0 || index >= outputFaces.size() || !validName(name)) return false;
        var anchor = outputFaces.get(index);
        if (name.isBlank()) faceNames.remove(anchor);
        else faceNames.put(anchor, name.trim());
        return true;
    }
    private java.util.List<dev.sablelife.module.ModuleAnchor> outputFaces = java.util.List.of();
    private Runnable changed = () -> {};
    private Runnable facesChanged = () -> {};

    public java.util.List<dev.sablelife.module.ModuleAnchor> outputFaces() { return outputFaces; }

    void onFacesChanged(Runnable callback) { facesChanged = callback; }

    public boolean canSetOutputFace(int index) {
        return index >= 0 && index < MAX_OUTPUT_FACES && index <= outputFaces.size()
                && (index < outputFaces.size() || outputFaces.size() < dev.sablelife.SableLifeServerConfig.maximumRedstoneOutputFaces());
    }

    public boolean setOutputFace(int index, dev.sablelife.module.ModuleAnchor anchor) {
        if (anchor == null || !canSetOutputFace(index)) return false;
        int duplicate = outputFaces.indexOf(anchor);
        if (duplicate >= 0) return duplicate == index;
        var next = new java.util.ArrayList<>(outputFaces);
        if (index == next.size()) next.add(anchor);
        else {
            var name = faceNames.remove(next.set(index, anchor));
            if (name != null) faceNames.put(anchor,name);
        }
        outputFaces = java.util.List.copyOf(next);
        facesChanged.run();
        return true;
    }

    public boolean removeOutputFace(int index) {
        if (index < 0 || index >= outputFaces.size()) return false;
        var next = new java.util.ArrayList<>(outputFaces);
        faceNames.remove(next.remove(index));
        outputFaces = java.util.List.copyOf(next);
        facesChanged.run();
        return true;
    }
    public void onChange(Runnable callback) { changed = callback; }
    private ItemStack first = ItemStack.EMPTY, second = ItemStack.EMPTY;
    private boolean enabled, loop = true;
    public static final int NAME_LIMIT = 40;
    private String name = "";
    public String name() { return name; }
    /** Cosmetic only: renaming must not restart the waveform or unregister its links. */
    public boolean name(String value) {
        if (!validName(value)) return false;
        name = value.trim(); return true;
    }
    private static boolean validName(String value) {
        return value != null && value.codePointCount(0,value.length()) <= NAME_LIMIT
                && value.codePoints().noneMatch(Character::isISOControl);
    }
    private int mode;
    private boolean latch;
    private int resetChannel;
    public boolean receiving() { return mode == 1; }
    public boolean programmed() { return mode >= 3; }
    public boolean transmitting() { return mode == 2 || mode == 4; }
    private int delay, on = 20, off = 20, strength = 15;
    public ItemStack first() { return first.copy(); }
    public ItemStack second() { return second.copy(); }
    public int value(int field) {
        return switch (field) { case 0 -> enabled ? 1 : 0; case 1 -> delay; case 2 -> on;
            case 3 -> off; case 4 -> strength; case 5 -> loop ? 1 : 0; case 6 -> mode; case 7 -> latch ? 1 : 0; case 8 -> resetChannel; default -> 0; };
    }
    public boolean set(int field, int value) {
        switch (field) {
            case 0 -> enabled = value != 0;
            case 1 -> delay = Math.clamp(value, 0, MAX_TIME);
            case 2 -> on = Math.clamp(value, 1, MAX_TIME);
            case 3 -> off = Math.clamp(value, 0, MAX_TIME);
            case 4 -> strength = Math.clamp(value, 1, 15);
            case 5 -> loop = value != 0;
            case 6 -> mode = Math.clamp(value, 0, 4);
            case 7 -> latch = value != 0;
            case 8 -> resetChannel = Math.clamp(value,0,HeartWireless.CHANNELS);
            default -> { return false; }
        }
        changed.run(); return true;
    }
    public void frequency(boolean blue, ItemStack sample) {
        ItemStack copy = sample.isEmpty() ? ItemStack.EMPTY : sample.copyWithCount(1);
        if (blue) second = copy; else first = copy;
        changed.run();
    }
    public boolean configured() { return enabled && (mode == 0 || mode == 3 || !first.isEmpty() || !second.isEmpty()); }
    public int powerAt(long elapsed) {
        if (receiving() || programmed() || !configured() || elapsed < delay) return 0;
        long phase = elapsed - delay;
        return (loop ? phase % ((long) on + off) < on : phase < on) ? strength : 0;
    }
    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        if (!name.isEmpty()) tag.putString("Name", name);
        if (!outputFaces.isEmpty()) {
            var faces = new net.minecraft.nbt.ListTag();
            for (var anchor : outputFaces) {
                var face = anchor.save();
                var name = faceNames.get(anchor);
                if (name != null) face.putString(OUTPUT_FACE_NAME,name);
                faces.add(face);
            }
            tag.put(OUTPUT_FACES_TAG, faces);
        }
        if (!first.isEmpty()) tag.put("First", first.save(registries));
        if (!second.isEmpty()) tag.put("Second", second.save(registries));
        tag.putBoolean("Latch",latch); tag.putInt("LatchReset",resetChannel);
        tag.putBoolean("Enabled", enabled); tag.putBoolean("Loop", loop);
        tag.putBoolean("Receiving", receiving()); tag.putInt("Mode", mode);
        tag.putInt("Delay", delay); tag.putInt("On", on); tag.putInt("Off", off); tag.putInt("Strength", strength);
        return tag;
    }
    public static WirelessChannel load(CompoundTag tag, HolderLookup.Provider registries) {
        var channel = new WirelessChannel();
        channel.name(tag.getString("Name"));
        var faces = tag.getList(OUTPUT_FACES_TAG, 10);
        var restored = new java.util.LinkedHashSet<dev.sablelife.module.ModuleAnchor>();
        for (int i = 0; i < Math.min(MAX_OUTPUT_FACES, faces.size()); i++) {
            var face = faces.getCompound(i);
            var anchor = dev.sablelife.module.ModuleAnchor.load(face);
            if (anchor != null && restored.add(anchor)) {
                String name = face.getString(OUTPUT_FACE_NAME);
                if (validName(name) && !name.isBlank()) channel.faceNames.put(anchor,name.trim());
            }
        }
        channel.outputFaces = java.util.List.copyOf(restored);
        channel.first = ItemStack.parseOptional(registries, tag.getCompound("First"));
        channel.second = ItemStack.parseOptional(registries, tag.getCompound("Second"));
        if (!channel.first.isEmpty()) channel.first.setCount(1);
        if (!channel.second.isEmpty()) channel.second.setCount(1);
        channel.set(0, tag.getBoolean("Enabled") ? 1 : 0);
        channel.set(1, tag.getInt("Delay")); channel.set(2, tag.contains("On") ? tag.getInt("On") : 20);
        channel.set(3, tag.contains("Off") ? tag.getInt("Off") : 20);
        channel.set(4, tag.contains("Strength") ? tag.getInt("Strength") : 15);
        channel.set(5, !tag.contains("Loop") || tag.getBoolean("Loop") ? 1 : 0);
        channel.set(6, tag.contains("Mode") ? tag.getInt("Mode") : tag.getBoolean("Receiving") ? 1 : 2);
        channel.set(7,tag.getBoolean("Latch")?1:0); channel.set(8,tag.getInt("LatchReset"));
        return channel;
    }
}
