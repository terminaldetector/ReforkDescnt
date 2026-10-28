package com.terminaldetector.drmd.world.contraption.boarding;

import com.terminaldetector.drmd.client.portal.PortalTransform;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;

/** Physical character frame. Rotation changes its shape, not a ship-local position anchor. */
public final class PassengerFrame {
    public static final Vec3 WORLD_UP=new Vec3(0,1,0);
    private PassengerFrame() {}
    public static Vec3 up(Quat frame){return frame.rotate(WORLD_UP);}
    public static Quat turn(Quat before,Vec3 targetUp,double maxAngle) {
        BodyBounds.finite(targetUp);
        if(targetUp.lengthSquared()<1e-12||!Double.isFinite(maxAngle)||maxAngle<0)throw new IllegalArgumentException("invalid frame turn");
        Vec3 old=up(before),next=targetUp.normalized();
        double angle=Math.acos(Math.max(-1,Math.min(1,old.dot(next))));
        if(angle<1e-10)return before;
        Quat delta=PortalTransform.rotationBetween(old,next);
        if(angle>maxAngle&&angle>1e-10)delta=ImmPtlQuaternions.interpolate(Quat.IDENTITY,delta,maxAngle/angle).normalized();
        return ImmPtlQuaternions.hamiltonProduct(delta,before).normalized();
    }
    public static Vec3 angularVelocity(Quat before,Quat after,double seconds) {
        Quat delta=ImmPtlQuaternions.hamiltonProduct(after,before.inverse()).normalized();
        if(delta.w()<0)delta=new Quat(-delta.x(),-delta.y(),-delta.z(),-delta.w());
        Vec3 vector=new Vec3(delta.x(),delta.y(),delta.z());double length=vector.length();
        if(length<1e-12)return new Vec3(0,0,0);
        return vector.scaled(2*Math.atan2(length,Math.max(0,delta.w()))/(seconds*length));
    }
    public static Vec3 transport(Vec3 direction,Quat before,Quat after) {
        return ImmPtlQuaternions.hamiltonProduct(after,before.inverse()).normalized().rotate(direction);
    }
    /** Returning to vanilla can rebase a safe square hitbox without spinning the player's heading. */
    public static Vec3 releaseLook(Vec3 direction,Quat before,Quat after,int gravityOwner) {
        return gravityOwner<0&&after.equals(Quat.IDENTITY)
            ?PortalTransform.rotationBetween(up(before),up(after)).rotate(direction):transport(direction,before,after);
    }
    public static OrientedBox box(Vec3 feet,double width,double height,Quat frame) {
        return new OrientedBox(feet.plus(up(frame).scaled(height/2)),new Vec3(width/2,height/2,width/2),frame);
    }
    public static Vec3 wish(DynamicBodyAttachment.Input input,Quat initial,Quat current) {
        Vec3 up=up(current),look=transport(input.look(),initial,current);
        Vec3 forward=look.minus(up.scaled(look.dot(up)));
        if(forward.lengthSquared()<1e-8)forward=current.rotate(new Vec3(0,0,1));
        forward=forward.normalized();
        Vec3 wish=up.cross(forward).scaled(input.strafe()).plus(forward.scaled(input.forward()));
        return (wish.lengthSquared()>1?wish.normalized():wish).scaled(4.3);
    }
}
