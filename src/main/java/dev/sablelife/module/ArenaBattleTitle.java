package dev.sablelife.module;

import dev.sablelife.heart.CreatureNames;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Optional;
import java.util.regex.Pattern;

public enum ArenaBattleTitle {
    START("Start", 0, ChatFormatting.GOLD, "start"),
    TEN_MINUTES("10m", 600, ChatFormatting.YELLOW, "remaining"),
    FIVE_MINUTES("5m", 300, ChatFormatting.GOLD, "remaining"),
    ONE_MINUTE("1m", 60, ChatFormatting.RED, "remaining"),
    TEN_SECONDS("10s", 10, ChatFormatting.DARK_RED, "remaining");

    public static final String ENABLED_KEY = "arenaRespawnBattleTitles";
    public static final String DISPLAY_KEY = "arenaRespawnBattleDisplay";
    public static final int CHAT_DISPLAY = 1;
    private static final String TEXT = "arena_battle_title.sablelife.";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(boss|time)\\}");
    private static final ArenaBattleTitle[] TITLES = values();

    private final String key;
    private final int ticks;
    private final ChatFormatting color;
    private final String template;

    ArenaBattleTitle(String suffix, int seconds, ChatFormatting color, String template) {
        this.key = "arenaRespawnBattleTitle" + suffix;
        this.ticks = seconds * 20;
        this.color = color;
        this.template = TEXT + template;
    }

    public String key() {
        return key;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public static boolean isTemplate(String key) {
        if (!key.startsWith("arenaRespawnBattleTitle")) return false;
        for (var title : TITLES) if (title.key.equals(key)) return true;
        return false;
    }

    public static ArenaBattleTitle due(int remaining, int initial) {
        if (remaining >= initial || remaining <= 0 || remaining % 20 != 0) return null;
        for (var title : TITLES) if (title.ticks == remaining) return title;
        return null;
    }

    public String example() {
        return "&" + color.getChar() + "&l" + Component.translatable(template, "{boss}", "{time}").getString();
    }

    public static String input(VirtualModule module, String key) {
        if (module.hasText(key)) return module.text(key);
        for (var title : TITLES) if (title.key.equals(key)) return title.example();
        return "";
    }

    public Component message(String custom, String name, int remaining) {
        String plainName = CreatureNames.formatted(name).getString();
        Component boss = plainName.isBlank() ? Component.translatable("entity.sablelife.creature") : Component.literal(plainName);
        int seconds = ((this == START ? remaining : ticks) + 19) / 20;
        Component time = seconds % 60 == 0
                ? Component.translatable(TEXT + "minutes", seconds / 60)
                : seconds < 60 ? Component.translatable(TEXT + "seconds", seconds)
                : Component.translatable(TEXT + "minutes_seconds", seconds / 60, seconds % 60);
        if (custom == null) return Component.translatable(template, boss, time).withStyle(color, ChatFormatting.BOLD);

        var result = Component.empty();
        CreatureNames.formatted(custom).visit((style, part) -> {
            var matches = PLACEHOLDER.matcher(part);
            int start = 0;
            while (matches.find()) {
                result.append(Component.literal(part.substring(start, matches.start())).setStyle(style));
                result.append(Component.empty().setStyle(style).append(matches.group(1).equals("boss") ? boss : time));
                start = matches.end();
            }
            result.append(Component.literal(part.substring(start)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }
}
