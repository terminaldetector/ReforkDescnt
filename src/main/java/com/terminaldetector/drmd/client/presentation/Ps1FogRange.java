package com.terminaldetector.drmd.client.presentation;
import java.util.Optional;
/** Same smoothstep/radial kernel as the installed Vanilla and Sodium shaders, in true world metres. */
public record Ps1FogRange(float start,float end) {
    public Ps1FogRange {if(!Float.isFinite(start)||!Float.isFinite(end)||start<0||end<=start)throw new IllegalArgumentException("fog range");}
    public static Optional<Ps1FogRange> at(double eyeY,float configured,float nearExtent){
        float density=Ps1Atmosphere.density(eyeY,configured);
        if(density<=0||!Float.isFinite(nearExtent)||nearExtent<0)return Optional.empty();
        float start=Math.max(0,Math.min(512,nearExtent))*.5f;
        return Optional.of(new Ps1FogRange(start,(float)Math.min(Float.MAX_VALUE,(double)start+2048d/density)));
    }
    public double amount(double distance,double alpha){
        if(!Double.isFinite(distance)||!Double.isFinite(alpha))return 0;
        double t=Math.max(0,Math.min(1,(distance-start)/(end-start)));
        return t*t*(3-2*t)*Math.max(0,Math.min(1,alpha));
    }
}
