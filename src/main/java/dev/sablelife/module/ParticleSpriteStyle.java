package dev.sablelife.module;

import dev.sablelife.SableLife;

/** Defaults for the bundled pixel-art particles. */
public enum ParticleSpriteStyle {
    ALERT("symbol_alert", 0xFF784F, 24, .48F),
    QUESTION("symbol_question", 0xC89AFF, 32, .46F),
    ELLIPSIS("symbol_ellipsis", 0xD8DFEA, 36, .60F),
    FAULT("symbol_fault", 0xFF675F, 18, .43F),
    BOLT("symbol_bolt", 0xFFE461, 16, .46F),
    METAL_CHIP("metal_chip", 0xBEC8D2, 20, .19F),
    FAULT_SPARK("fault_spark", 0x7EEBFF, 14, .36F);

    private final String path;
    private final int color;
    private final int lifetime;
    private final float size;

    ParticleSpriteStyle(String path, int color, int lifetime, float size) {
        this.path = path;
        this.color = color;
        this.lifetime = lifetime;
        this.size = size;
    }

    public String path() { return path; }
    public String id() { return SableLife.MOD_ID + ":" + path; }
    public int color() { return color; }
    public int lifetime() { return lifetime; }
    public float size() { return size; }
}
