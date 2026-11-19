package com.terminaldetector.drmd.client.presentation;

import com.terminaldetector.drmd.world.level.WorldLevels;

/** Continuous presentation-only air column; source geometry, depth and world simulation stay independent. */
public final class Ps1Atmosphere {
    private Ps1Atmosphere() {}

    private static double ramp(double y,double low,double high) {
        double t=Math.max(0,Math.min(1,(y-low)/(high-low)));
        return t*t*(3-2*t);
    }

    /** Thin air fades continuously through the sky and orbital bands; space never gains dense terrestrial haze. */
    public static float density(double y,float configured) {
        if(!Double.isFinite(y)||!Float.isFinite(configured)||configured<=0)return 0;
        double air=1-.55*ramp(y,WorldLevels.SURFACE_TOP,WorldLevels.SKY_TOP)
            -.37*ramp(y,WorldLevels.SKY_TOP,WorldLevels.ORBITAL_TOP);
        return (float)(Math.min(2,configured)*air);
    }

    /** Reference for the shader's colour-only distant terrain/landmark fade; no alpha/discard policy. */
    public static double fogAmount(double y,float configured,double trueDistance,double innerRadius) {
        if(!Double.isFinite(trueDistance)||!Double.isFinite(innerRadius))return 0;
        double into=Math.max(0,trueDistance-Math.max(512,innerRadius))/2048;
        return Math.min(.85,1-Math.exp(-density(y,configured)*into));
    }
}
