package com.terminaldetector.drmd.weapon.core;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import java.util.Optional;

/** Earliest contact of two translated axis-aligned projectile boxes in one shared tick.
 * Deltas are full tick displacements; the result is independent of entity tick order. */
public final class RelativeProjectileSweep {
    public record Contact(double fraction,Vec3 point,Vec3 normal){}
    private RelativeProjectileSweep(){}

    public static Optional<Contact> firstContact(Vec3 first,Vec3 firstDelta,Vec3 firstHalf,
                                                Vec3 second,Vec3 secondDelta,Vec3 secondHalf){
        if(!finite(first)||!finite(firstDelta)||!half(firstHalf)||!finite(second)
            ||!finite(secondDelta)||!half(secondHalf))return Optional.empty();
        Vec3 relative=first.minus(second),delta=firstDelta.minus(secondDelta),extent=firstHalf.plus(secondHalf);
        double enter=0,exit=1;int entryAxis=-1;double sign=0;
        for(int axis=0;axis<3;axis++){
            double p=get(relative,axis),v=get(delta,axis),h=get(extent,axis);
            if(Math.abs(v)<1e-12){if(p < -h||p > h)return Optional.empty();continue;}
            double near=(-h-p)/v,far=(h-p)/v,face=-1;
            if(near>far){double swap=near;near=far;far=swap;face=1;}
            if(near>enter){enter=near;entryAxis=axis;sign=face;}
            exit=Math.min(exit,far);
            if(enter>exit)return Optional.empty();
        }
        if(enter<0||enter>1||exit<0)return Optional.empty();
        Vec3 a=first.plus(firstDelta.scaled(enter)),b=second.plus(secondDelta.scaled(enter));
        // Choose a material point in both actual boxes, including offset grazing contact.
        double[] point=new double[3];
        for(int axis=0;axis<3;axis++){
            double low=Math.max(get(a,axis)-get(firstHalf,axis),get(b,axis)-get(secondHalf,axis));
            double high=Math.min(get(a,axis)+get(firstHalf,axis),get(b,axis)+get(secondHalf,axis));
            point[axis]=(low+high)*.5;
        }
        Vec3 normal=entryAxis<0?(delta.lengthSquared()>1e-24?delta.scaled(-1).normalized():new Vec3(1,0,0))
            :new Vec3(entryAxis==0?sign:0,entryAxis==1?sign:0,entryAxis==2?sign:0);
        return Optional.of(new Contact(enter,new Vec3(point[0],point[1],point[2]),normal));
    }
    private static double get(Vec3 v,int axis){return axis==0?v.x():axis==1?v.y():v.z();}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z());}
    private static boolean half(Vec3 v){return finite(v)&&v.x()>0&&v.y()>0&&v.z()>0;}
}
