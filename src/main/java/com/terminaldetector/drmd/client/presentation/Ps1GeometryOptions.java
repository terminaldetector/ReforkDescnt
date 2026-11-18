package com.terminaldetector.drmd.client.presentation;

import java.util.Properties;

/** Client-only, independent opt-in controls for the first textured BlockBody geometry path. */
public record Ps1GeometryOptions(float snapPixels, float affineAmount) {
    public static final float DEFAULT_SNAP = 0;
    public static final float DEFAULT_AFFINE = 0;

    public Ps1GeometryOptions {
        snapPixels = bounded(snapPixels, 4, DEFAULT_SNAP);
        affineAmount = bounded(affineAmount, 1, DEFAULT_AFFINE);
    }

    private static float bounded(float value, float maximum, float fallback) {
        return Float.isFinite(value) ? Math.max(0, Math.min(maximum, value)) : fallback;
    }

    public boolean enabled() { return snapPixels > 0 || affineAmount > 0; }

    /** Profile alone cannot admit GUI, viewmodels, Native, Debug or nested offscreen draws. */
    public boolean eligible(PresentationProfile profile, boolean lowResolutionWorld, boolean offscreen) {
        return enabled() && profile == PresentationProfile.PS1_HYBRID && lowResolutionWorld && !offscreen;
    }

    public static Ps1GeometryOptions read(Properties properties) {
        return new Ps1GeometryOptions(read(properties, "ps1VertexSnapPixels", DEFAULT_SNAP),
                read(properties, "ps1AffineAmount", DEFAULT_AFFINE));
    }

    private static float read(Properties properties, String key, float fallback) {
        try { return Float.parseFloat(properties.getProperty(key, Float.toString(fallback)).trim()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    public void write(Properties properties) {
        properties.setProperty("ps1VertexSnapPixels", Float.toString(snapPixels));
        properties.setProperty("ps1AffineAmount", Float.toString(affineAmount));
    }

    public float nextSnap() {
        for (float next : new float[]{0, .5f, 1, 2, 4}) if (next > snapPixels) return next;
        return 0;
    }

    public float nextAffine() {
        for (float next : new float[]{0, .25f, .5f, .75f, 1}) if (next > affineAmount) return next;
        return 0;
    }
}
