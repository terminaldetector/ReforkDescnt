package com.terminaldetector.drmd.ai;

import java.util.UUID;

/** Deterministic antisymmetric escape direction when two bodies have identical centres. */
public final class OverlapSeparation {
    public record Direction(double x,double y,double z) {}
    private OverlapSeparation() {}
    public static Direction between(UUID self,UUID other) {
        int order=self.compareTo(other);
        if(order==0) return new Direction(0,0,0);
        UUID low=order<0?self:other,high=order<0?other:self;
        long seed=low.getMostSignificantBits()^Long.rotateLeft(low.getLeastSignificantBits(),17)
                ^Long.rotateLeft(high.getMostSignificantBits(),31)^high.getLeastSignificantBits();
        double x=component(mix(seed)),y=component(mix(seed+0x9e3779b97f4a7c15L)),z=component(mix(seed+0x3c6ef372fe94f82aL));
        double length=Math.sqrt(x*x+y*y+z*z);
        if(length<1e-8) {x=1;y=z=0;length=1;}
        double factor=(order<0?1:-1)/length;
        return new Direction(x*factor,y*factor,z*factor);
    }
    private static double component(long bits) { return bits*0x1.0p-63; }
    private static long mix(long value) {
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;
        value=(value^(value>>>27))*0x94d049bb133111ebL;
        return value^(value>>>31);
    }
}
