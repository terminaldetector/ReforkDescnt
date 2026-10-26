package com.terminaldetector.drmd.client.planet;

/** Continuous interpolation of centre-sampled surface observations in world coordinates. */
public final class SurfaceInterpolation {
    private SurfaceInterpolation() {}
    @FunctionalInterface public interface Sampler { double height(double x,double z); }
    public static double height(double x,double z,int pitch,Sampler source) {
        double x0=(Math.floor(x/pitch-.5)+.5)*pitch;
        double z0=(Math.floor(z/pitch-.5)+.5)*pitch;
        double tx=(x-x0)/pitch,tz=(z-z0)/pitch;
        double a=source.height(x0,z0)*(1-tx)+source.height(x0+pitch,z0)*tx;
        double b=source.height(x0,z0+pitch)*(1-tx)+source.height(x0+pitch,z0+pitch)*tx;
        return a*(1-tz)+b*tz;
    }
}
