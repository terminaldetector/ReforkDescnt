package com.terminaldetector.drmd.world.bombardment;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;

/** Finite authored spatial orders for the existing seven/fourteen-bomblet types. No random or recursive fan-out. */
public record ClusterProgram(Pattern pattern,int armTicks,int splitTicks,int stages,int staggerTicks,
                             int lifetime,double safetyDistance,double spread) {
    public enum Pattern { FAN,RING,STAGGER }
    public ClusterProgram {
        if(pattern==null||armTicks<2||armTicks>80||splitTicks<armTicks||splitTicks>200||stages<1||stages>3
            ||staggerTicks<0||staggerTicks>12||lifetime<splitTicks+(stages-1)*staggerTicks+8||lifetime>600
            ||!Double.isFinite(safetyDistance)||safetyDistance<1||safetyDistance>32
            ||!Double.isFinite(spread)||spread<.1||spread>1.5)throw new IllegalArgumentException("bounded cluster program");
    }
    public static ClusterProgram preset(Pattern pattern){return new ClusterProgram(pattern,6,16,pattern==Pattern.STAGGER?3:1,4,160,3,.65);}
    public ClusterProgram next(){return preset(Pattern.values()[(pattern.ordinal()+1)%Pattern.values().length]);}
    public int first(int total,int stage){check(total,stage);return total*stage/stages;}
    public int count(int total,int stage){check(total,stage);return total*(stage+1)/stages-first(total,stage);}
    public int due(int stage){if(stage<0||stage>=stages)throw new IllegalArgumentException("stage");return splitTicks+stage*staggerTicks;}
    private void check(int total,int stage){if((total!=7&&total!=14)||stage<0||stage>=stages)throw new IllegalArgumentException("finite cluster roster");}
    /** Axes are the actual saved launcher frame, transported at portals independently of owner motion. */
    public Vec3 direction(int total,int index,Vec3 forward,Vec3 up){
        if((total!=7&&total!=14)||index<0||index>=total)throw new IllegalArgumentException("child index");
        Vec3 f=forward.normalized(),u=up.minus(f.scaled(up.dot(f))).normalized();Vec3 right=f.cross(u).normalized();
        if(f.lengthSquared()<.9||u.lengthSquared()<.9||right.lengthSquared()<.9)throw new IllegalArgumentException("launcher frame");
        if(pattern==Pattern.FAN){double lateral=spread*(2.0*index/(total-1)-1);double row=(index%2==0?1:-1)*spread*.18;return f.plus(right.scaled(lateral)).plus(u.scaled(row)).normalized();}
        double angle=2*Math.PI*index/total;
        // Stages alternate ring height while retaining a forward bias and a deterministic azimuth.
        double axial=pattern==Pattern.STAGGER?1+.18*(index%3):1;
        return f.scaled(axial).plus(right.scaled(Math.cos(angle)*spread)).plus(u.scaled(Math.sin(angle)*spread)).normalized();
    }
}
