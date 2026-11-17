package com.terminaldetector.drmd.client.presentation;

import java.util.Properties;

/** Colour choices belong only to the selected PS1 profile; 256 is an explicit raw bypass. */
public record Ps1ColorOptions(int levels, float ditherStrength) {
    public static final int DEFAULT_LEVELS = 32;
    public static final float DEFAULT_DITHER = .5f;

    public Ps1ColorOptions {
        levels = Math.max(2, Math.min(256, levels));
        ditherStrength = Float.isFinite(ditherStrength) ? Math.max(0, Math.min(1, ditherStrength)) : DEFAULT_DITHER;
    }

    public boolean enabled() { return levels < 256; }

    public static Ps1ColorOptions read(Properties properties) {
        int levels = DEFAULT_LEVELS;
        float dither = DEFAULT_DITHER;
        try { levels = Integer.parseInt(properties.getProperty("ps1ColorLevels", "32").trim()); }
        catch (NumberFormatException ignored) { }
        try { dither = Float.parseFloat(properties.getProperty("ps1DitherStrength", "0.5").trim()); }
        catch (NumberFormatException ignored) { }
        return new Ps1ColorOptions(levels, dither);
    }

    public void write(Properties properties) {
        properties.setProperty("ps1ColorLevels", Integer.toString(levels));
        properties.setProperty("ps1DitherStrength", Float.toString(ditherStrength));
    }

    public int nextLevels() {
        return switch (levels) { case 256 -> 64; case 64 -> 32; case 32 -> 16; case 16 -> 8; case 8 -> 256; default -> 32; };
    }

    public float nextDither() {
        for (float next : new float[]{0, .25f, .5f, .75f, 1}) if (next > ditherStrength) return next;
        return 0;
    }
}
