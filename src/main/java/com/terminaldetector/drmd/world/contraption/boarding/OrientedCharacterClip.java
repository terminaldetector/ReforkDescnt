package com.terminaldetector.drmd.world.contraption.boarding;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.*;
import java.util.*;

/** Static-world character shape casts. Broadphase bounds never replace the occupied OBB. */
public final class OrientedCharacterClip implements DynamicBodyAttachment.Motion {
    private static final Vec3 ZERO=new Vec3(0,0,0);
    private final CompoundCollider geometry;
    private final double width,height;
    public OrientedCharacterClip(CompoundCollider geometry,double width,double height) {
        this.geometry=Objects.requireNonNull(geometry);this.width=width;this.height=height;
        PassengerFrame.box(ZERO,width,height,Quat.IDENTITY);
    }
    @Override public boolean canOrient(Vec3 feet,Quat frame) {
        var actor=PassengerFrame.box(feet,width,height,frame);
        for(var bounds:geometry.query(actor.bounds())) {
            var c=actor.contact(OrientedBox.axisAligned(bounds),0);
            if(c.isPresent()&&c.get().penetration()>1e-5)return false;
        }
        return true;
    }
    @Override public DynamicBodyAttachment.Move move(Vec3 feet,Quat frame,Vec3 requested) {
        BodyBounds.finite(requested);
        Vec3 at=feet,remaining=requested;var normals=new ArrayList<Vec3>();
        for(int iteration=0;iteration<6&&remaining.lengthSquared()>1e-20;iteration++) {
            var actor=PassengerFrame.box(at,width,height,frame);
            var moved=PassengerFrame.box(at.plus(remaining),width,height,frame);
            OrientedBox.Sweep nearest=null;
            for(var bounds:geometry.query(actor.bounds().union(moved.bounds()))) {
                var shape=OrientedBox.axisAligned(bounds);var overlap=actor.contact(shape,0);
                // Do not trap an already overlapping actor that is escaping or sliding outward.
                if(overlap.isPresent()&&overlap.get().penetration()>1e-9&&remaining.dot(overlap.get().normal())>=-1e-12)continue;
                var hit=actor.sweep(shape,remaining,ZERO);
                if(hit.isPresent()&&(nearest==null||hit.get().fraction()<nearest.fraction()))nearest=hit.get();
            }
            if(nearest==null){at=at.plus(remaining);break;}
            double t=Math.max(0,nearest.fraction()-1e-7);
            at=at.plus(remaining.scaled(t));remaining=remaining.scaled(1-t);
            Vec3 normal=nearest.normal();normals.add(normal);
            double into=remaining.dot(normal);
            if(into>=-1e-12)break;
            remaining=remaining.minus(normal.scaled(into));
        }
        return new DynamicBodyAttachment.Move(at.minus(feet),normals);
    }
}
