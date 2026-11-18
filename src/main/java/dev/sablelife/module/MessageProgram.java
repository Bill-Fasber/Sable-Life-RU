package dev.sablelife.module;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/** The ordered, persistent settings for a Message Display module. */
public final class MessageProgram {
    public static final int MAX_ROWS = 8;
    public static final int MAX_TEXT = 256;
    public static final int CHAT = 0, TITLE = 1, ACTION_BAR = 2;
    public static final int MIN_DURATION = 1, MAX_DURATION = 1200, DEFAULT_DURATION = 60;

    public record Row(int mode, String text, String subtitle, int duration, int delayAfter) {
        public int delayAfterOr(int previousInterval) {
            return delayAfter > 0 ? delayAfter : previousInterval;
        }
    }

    private static final Row BLANK = new Row(CHAT, "", "", DEFAULT_DURATION, 0);
    private final List<Row> rows = new ArrayList<>(List.of(BLANK));

    public List<Row> rows() { return List.copyOf(rows); }

    public boolean edit(String key, int index, String text) {
        if (key.equals("add")) {
            if (rows.size() >= MAX_ROWS) return false;
            rows.add(BLANK);
            return true;
        }
        if (index < 0 || index >= rows.size()) return false;
        if (key.equals("remove")) {
            if (rows.size() == 1) return false;
            rows.remove(index);
            return true;
        }
        if (!validText(text)) return false;
        Row row = rows.get(index);
        try {
            Row updated = switch (key) {
                case "text" -> new Row(row.mode(), text, row.subtitle(), row.duration(), row.delayAfter());
                case "subtitle" -> new Row(row.mode(), row.text(), text, row.duration(), row.delayAfter());
                case "mode" -> {
                    int mode = Integer.parseInt(text);
                    yield mode < CHAT || mode > ACTION_BAR ? null : new Row(mode, row.text(), row.subtitle(), row.duration(), row.delayAfter());
                }
                case "duration" -> {
                    int duration = Integer.parseInt(text);
                    yield duration < MIN_DURATION || duration > MAX_DURATION ? null : new Row(row.mode(), row.text(), row.subtitle(), duration, row.delayAfter());
                }
                case "delay_after" -> {
                    int delay = text.isEmpty() ? 0 : Integer.parseInt(text);
                    yield delay < 0 || delay > MAX_DURATION ? null : new Row(row.mode(), row.text(), row.subtitle(), row.duration(), delay);
                }
                default -> null;
            };
            if (updated == null) return false;
            rows.set(index, updated);
            return true;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private static boolean validText(String text) {
        return text.length() <= MAX_TEXT && text.codePoints().noneMatch(Character::isISOControl);
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        var list = new ListTag();
        for (Row row : rows) {
            var entry = new CompoundTag();
            entry.putInt("Mode", row.mode());
            entry.putString("Text", row.text());
            entry.putString("Subtitle", row.subtitle());
            entry.putInt("Duration", row.duration());
            if (row.delayAfter() > 0) entry.putInt("DelayAfter", row.delayAfter());
            list.add(entry);
        }
        tag.put("Rows", list);
        return tag;
    }

    void load(CompoundTag tag) {
        rows.clear();
        var entries = tag.getList("Rows", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(entries.size(), MAX_ROWS); i++) {
            var entry = entries.getCompound(i);
            String text = entry.getString("Text");
            String subtitle = entry.getString("Subtitle");
            int duration = entry.contains("Duration", Tag.TAG_ANY_NUMERIC) ? entry.getInt("Duration") : DEFAULT_DURATION;
            int delay = entry.getInt("DelayAfter");
            rows.add(new Row(Math.clamp(entry.getInt("Mode"), CHAT, ACTION_BAR), validText(text) ? text : "",
                    validText(subtitle) ? subtitle : "", Math.clamp(duration, MIN_DURATION, MAX_DURATION),
                    delay >= MIN_DURATION && delay <= MAX_DURATION ? delay : 0));
        }
        if (rows.isEmpty()) rows.add(BLANK);
    }
}
