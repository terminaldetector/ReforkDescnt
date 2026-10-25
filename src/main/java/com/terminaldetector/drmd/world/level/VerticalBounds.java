package com.terminaldetector.drmd.world.level;

/** Finite dimension bounds; a narrative height fallback is not proof of playable world space. */
public final class VerticalBounds {
    public enum Position { BELOW, INSIDE, ABOVE, INVALID }
    private VerticalBounds() {}
    public static Position classify(double y,int bottom,int top) {
        if(!Double.isFinite(y) || top<=bottom) return Position.INVALID;
        if(y<bottom) return Position.BELOW;
        return y>=top?Position.ABOVE:Position.INSIDE;
    }
    public static boolean contains(double y,int bottom,int top) { return classify(y,bottom,top)==Position.INSIDE; }
    public static boolean needsLowerRecovery(double feetY,int bottom) {
        return Double.isFinite(feetY) && feetY<bottom+4;
    }
}
