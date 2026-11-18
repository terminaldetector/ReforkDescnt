package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;

/** Packet-owned display discontinuities; rendering never consumes a pending position acknowledgement. */
public final class BodyPortalDisplay {
    public record PoseUpdate(boolean accepted,Vec3 snapOrigin) {
        public boolean discontinuity(){return snapOrigin!=null;}
    }
    public enum PositionAction { APPLY, ALREADY_AT_WARP_DESTINATION }
    /** Native relative entity tracking quantizes each coordinate to1/4096 block. */
    private static final double POSITION_QUANTUM=1.0/4096.0;
    private final BodyRotationTimeline rotation=new BodyRotationTimeline();
    private long epoch;
    private Vec3 pendingDestination;

    public long epoch(){return epoch;}

    /**
     * A missing old POSE epoch is0 and may omit origin. A newer epoch must contain the actual
     * entity pivot/origin, so the caller can atomically reset current, previous and render positions
     * at this return boundary, even if vanilla's destination position packet has not arrived yet.
     * Reject stale epochs before reading their remaining physics state in the caller.
     */
    public PoseUpdate receivePose(long receivedEpoch,Quat pose,Vec3 origin,double time){
        if(receivedEpoch<epoch)return new PoseUpdate(false,null);
        checkTime(time);
        if(receivedEpoch>epoch){
            finite(origin);rotation.reset(pose,time);
            epoch=receivedEpoch;pendingDestination=origin;
            return new PoseUpdate(true,origin);
        }
        rotation.receive(pose,time);
        return new PoseUpdate(true,null);
    }

    /**
     * POSE already applied the discontinuity exactly once. A following destination echo must not
     * run vanilla position handling again; a later moving destination follows the normal path
     * from the already snapped origin. Position-first is handled by the subsequent POSE snap.
     * Vanilla position packets have no epoch: arbitrary stale positions cannot be identified here.
     */
    public PositionAction receivePosition(Vec3 origin){
        finite(origin);
        var pending=pendingDestination;pendingDestination=null;
        if(pending!=null&&Math.abs(origin.x()-pending.x())<=POSITION_QUANTUM
                &&Math.abs(origin.y()-pending.y())<=POSITION_QUANTUM&&Math.abs(origin.z()-pending.z())<=POSITION_QUANTUM)
            return PositionAction.ALREADY_AT_WARP_DESTINATION;
        return PositionAction.APPLY;
    }

    /** Spawn/NBT restoration starts a new local timeline without manufacturing a portal event. */
    public void reset(long savedEpoch,Quat pose,double time){
        if(savedEpoch<0)throw new IllegalArgumentException("negative portal epoch");
        checkTime(time);rotation.reset(pose,time);epoch=savedEpoch;pendingDestination=null;
    }

    /** Same timeline and two-tick smoothing for ordinary Native/PS1/offscreen body views. */
    public Quat sample(double time){return rotation.sample(time);}

    private static void checkTime(double time){
        if(!Double.isFinite(time))throw new IllegalArgumentException("non-finite display time");
    }
    private static void finite(Vec3 value){
        if(value==null||!Double.isFinite(value.lengthSquared()))throw new IllegalArgumentException("non-finite portal display origin");
    }
}
