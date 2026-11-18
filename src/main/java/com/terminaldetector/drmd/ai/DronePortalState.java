package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6PortalTransform;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.entity.model.FlightHullPose;
import com.terminaldetector.drmd.flight.ShipAttitude;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import java.util.Optional;

/** Optional live-drone auxiliary state in the existing atomic portal transaction; no movement/physics authority. */
public final class DronePortalState {
    private DronePortalState(){}
    public static Optional<Prepared> prepare(DroneEntity drone,D6PortalTransform transform){
        if(!(drone.getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread()||!drone.isAlive()||drone.isRemoved()
            ||world.getEntity(drone.getUuid())!=drone||transform==null||transform.scale()!=1||drone.portalFrameRevision()==Long.MAX_VALUE
            ||!Float.isFinite(drone.getYaw())||!Float.isFinite(drone.getPitch())||!Float.isFinite(drone.getFlightRoll())
            ||!Double.isFinite(drone.getPos().lengthSquared())||!Double.isFinite(drone.getVelocity().lengthSquared()))return Optional.empty();
        var q=transform.rotation();double length=q.x()*q.x()+q.y()*q.y()+q.z()*q.z()+q.w()*q.w();
        if(!Double.isFinite(length)||length<1e-12)return Optional.empty();
        var orientation=transform.transformOrientation(FlightHullPose.physicalRotation(drone.getYaw(),drone.getPitch(),drone.getFlightRoll()));
        var forward=mc(orientation.rotate(new Vec3(0,0,1))).normalize();var up=mc(orientation.rotate(new Vec3(0,1,0))).normalize();
        var attitude=new ShipAttitude();attitude.setForwardUp(forward,up);
        float yaw=attitude.yawDegrees(),pitch=attitude.pitchDegrees(),bank=attitude.bankDegrees();
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||!Float.isFinite(bank))return Optional.empty();
        return Optional.of(new Prepared(world,drone,orientation,yaw,pitch,bank));
    }
    private static Vec3d mc(Vec3 p){return new Vec3d(p.x(),p.y(),p.z());}
    private static long abilityEpoch(DroneEntity drone){return drone instanceof TeleportFlyerEntity tele?tele.ability().epoch():0;}
    public static final class Prepared {
        private final ServerWorld world;private final DroneEntity drone;private final Vec3d before,velocity;private final float yaw,pitch,bank;
        private final long revision,ability;private final Quat destination;private final float destinationYaw,destinationPitch,destinationBank;private boolean applied;
        private Prepared(ServerWorld world,DroneEntity drone,Quat destination,float y,float p,float b){
            this.world=world;this.drone=drone;before=drone.getPos();velocity=drone.getVelocity();yaw=drone.getYaw();pitch=drone.getPitch();bank=drone.getFlightRoll();revision=drone.portalFrameRevision();ability=abilityEpoch(drone);
            this.destination=destination;destinationYaw=y;destinationPitch=p;destinationBank=b;
        }
        public float destinationYaw(){return destinationYaw;}
        public float destinationPitch(){return destinationPitch;}
        public Quat destinationOrientation(){return destination;}
        private boolean auxiliaryCurrent(){return !applied&&world.getServer().isOnThread()&&drone.getWorld()==world&&world.getEntity(drone.getUuid())==drone&&drone.isAlive()&&!drone.isRemoved()
            &&drone.portalFrameRevision()==revision&&drone.getFlightRoll()==bank&&abilityEpoch(drone)==ability;}
        /** All actors are checked before any member's generic position/velocity commit. */
        public boolean current(){return auxiliaryCurrent()&&drone.getPos().equals(before)&&drone.getVelocity().equals(velocity)&&drone.getYaw()==yaw&&drone.getPitch()==pitch;}
        /** Called after the dispatcher's sole generic move; never re-tests pre-transfer position/angles here. */
        public void commit(){
            if(!auxiliaryCurrent())throw new IllegalStateException("validated same-thread drone portal auxiliary state changed");
            applied=true;DroneNavigation.portalDeparted(drone);drone.portalFrameMoved(destinationBank);
        }
    }
}
