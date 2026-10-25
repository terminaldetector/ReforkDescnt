package com.terminaldetector.drmd.client.render;

/** Angular bound for mod-owned flash billboards; never alters explosion damage or terrain. */
public final class FxVisibility {
    private FxVisibility() {}
    public static float radius(double eyeDistance,float requested) {
        if(!Double.isFinite(eyeDistance) || !Float.isFinite(requested) || eyeDistance<.12 || requested<=0) return 0;
        return (float)Math.min(requested,eyeDistance*.18);
    }
}
