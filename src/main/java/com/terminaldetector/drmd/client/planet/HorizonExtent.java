package com.terminaldetector.drmd.client.planet;
/** Current-eye outer opacity; cached mesh origins never define where the horizon haze is centred. */
public final class HorizonExtent {
    private HorizonExtent(){}
    public static float outerRadius(){return (float)HorizonProjection.MAX_TRUE_RADIUS;}
    public static double opacity(double horizontalDistance,double outerRadius){
        if(!Double.isFinite(horizontalDistance)||!Double.isFinite(outerRadius)||outerRadius<=0)return 0;
        double t=Math.max(0,Math.min(1,(horizontalDistance-outerRadius*.55)/(outerRadius*.45)));
        return 1-t*t*(3-2*t);
    }
    public static double at(double worldX,double worldZ,double eyeX,double eyeZ,double outerRadius){
        return opacity(Math.hypot(worldX-eyeX,worldZ-eyeZ),outerRadius);
    }
}
