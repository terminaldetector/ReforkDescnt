package com.terminaldetector.drmd.client.presentation;

import java.util.Locale;

/** Client raster/presentation only; independent of world geometry and simulation profiles. */
public enum PresentationProfile {
    HYBRID_NATIVE, PS1_HYBRID, RENDER_DEBUG;

    public static PresentationProfile parse(String value) {
        if (value == null) return HYBRID_NATIVE;
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "PS1_HYBRID" -> PS1_HYBRID;
            case "RENDER_DEBUG", "DEBUG" -> RENDER_DEBUG;
            default -> HYBRID_NATIVE;
        };
    }

    public PresentationProfile next() { return values()[(ordinal() + 1) % values().length]; }
}
