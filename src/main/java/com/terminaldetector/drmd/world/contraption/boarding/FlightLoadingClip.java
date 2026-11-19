package com.terminaldetector.drmd.world.contraption.boarding;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.*;
import java.util.*;

/** A metadata-only loading fence: truncate the real sweep, never apply a collision impulse.
 * The suit retains its velocity while unavailable cubes arrive. Real terrain uses the normal clip. */
public final class FlightLoadingClip {
    private static final Vec3 ZERO=new Vec3(0,0,0);
    private final CompoundCollider unknown;
    private final double width,height;
    public FlightLoadingClip(List<BodyBounds> unavailable,double width,double height){
        unknown=new CompoundCollider(unavailable);this.width=width;this.height=height;
        PassengerFrame.box(ZERO,width,height,Quat.IDENTITY);
    }
    public boolean canOrient(Vec3 feet,Quat frame){
        var actor=PassengerFrame.box(feet,width,height,frame);
        for(var bounds:unknown.query(actor.bounds())){
            var hit=actor.contact(OrientedBox.axisAligned(bounds),0);
            if(hit.isPresent()&&hit.get().penetration()>1e-8)return false;
        }
        return true;
    }
    public Vec3 clip(Vec3 feet,Quat frame,Vec3 requested){
        BodyBounds.finite(requested);
        if(requested.lengthSquared()<1e-20)return requested;
        var actor=PassengerFrame.box(feet,width,height,frame);
        var end=PassengerFrame.box(feet.plus(requested),width,height,frame);
        double fraction=1;
        for(var bounds:unknown.query(actor.bounds().union(end.bounds()))){
            var shape=OrientedBox.axisAligned(bounds);var overlap=actor.contact(shape,0);
            if(overlap.isPresent()&&overlap.get().penetration()>1e-8)return ZERO;
            var hit=actor.sweep(shape,requested,ZERO);
            if(hit.isPresent())fraction=Math.min(fraction,Math.max(0,hit.get().fraction()-1e-7));
        }
        return requested.scaled(fraction);
    }
}
