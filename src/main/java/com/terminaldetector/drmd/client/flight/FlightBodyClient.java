package com.terminaldetector.drmd.client.flight;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.D6PhysicsBody;
import com.terminaldetector.drmd.network.FlightBodyNetworking;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Shared CCD prediction, with bounded replay of the suit integrator's velocity increments.
 * Ship motion never supplies a player-local position anchor. */
public final class FlightBodyClient {
    private record Prediction(Vec3 impulse,DynamicBodyAttachment.State state) {}
    private static final NavigableMap<Integer,Prediction> HISTORY=new TreeMap<>();
    private static Object world;private static int epoch=-1,sequence;private static boolean active;
    private static Vec3 correction=new Vec3(0,0,0),lastVelocity;
    private static FlightBodyNetworking.State waitingForShape;
    private FlightBodyClient(){}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FlightBodyNetworking.State.ID,(p,c)->c.client().execute(()->apply(p)));
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->clear());ClientPlayConnectionEvents.JOIN.register((h,s,c)->clear());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c->{if(waitingForShape!=null&&c.world!=null&&ready(waitingForShape)){var packet=waitingForShape;waitingForShape=null;apply(packet);}});
    }
    public static void clear(){world=null;epoch=-1;sequence=0;active=false;HISTORY.clear();correction=new Vec3(0,0,0);lastVelocity=null;waitingForShape=null;}
    public static boolean active(){return active;}
    public static int pending(){return HISTORY.size();}
    public static int sequence(){return sequence;}
    public static DynamicBodyAttachment.State predictedState(int sequence){var p=HISTORY.get(sequence);return p==null?null:p.state();}
    private static boolean ready(FlightBodyNetworking.State packet) {
        var w=MinecraftClient.getInstance().world;if(w==null||!w.getRegistryKey().getValue().equals(packet.dimension()))return false;
        for(var geometry:packet.geometry()) {
            var body=BodySpatialIndex.find(w,geometry.entity()).orElse(null);
            if(body==null||body.cells().isEmpty()||body.shapeRevision()!=geometry.revision())return false;
        }
        return true;
    }
    private static DynamicBodyAttachment.State state(PlayerEntity p,Vec3 velocity) {
        // Retain the existing upright suit collision shape. Walking publishes its own frame
        // after H-off; flight thrust/model/camera orientation remains owned by ShipAttitude.
        return new DynamicBodyAttachment.State(BoardingSystem.pure(p.getPos()),velocity,false,-1);
    }
    private static List<BodyMotion> motions(PlayerEntity p,DynamicBodyAttachment.State state,int lead) {
        var result=new ArrayList<BodyMotion>();var offset=BoardingSystem.mc(state.feet()).subtract(p.getPos());
        var area=p.getBoundingBox().offset(offset).stretch(BoardingSystem.mc(state.velocity().scaled(.05))).expand(4+512*.05);
        for(var body:BodySpatialIndex.query(p.getWorld(),area)) {
            if(body.cells().isEmpty())continue;
            var pos=body.massPosition();double r=body.radius();
            if(!new net.minecraft.util.math.Box(pos.x()-r,pos.y()-r,pos.z()-r,pos.x()+r,pos.y()+r,pos.z()+r).intersects(area))continue;
            var dynamics=new D6PhysicsBody().withLimits(512,2).withMass(body.physicsMass()).withInertia(body.physics().inertia())
                .withPosition(pos).withRotation(body.rotation()).withLinearVelocity(body.physics().linearVelocity()).withAngularMomentum(body.physics().angularMomentum());
            boolean paused=body.boardingMotionPaused();if(!paused)for(int i=0;i<Math.min(16,lead);i++)dynamics.step(.05);
            var from=dynamics.position();var before=dynamics.rotation();if(!paused)dynamics.step(.05);
            result.add(new BodyMotion(body.getId(),body.collider(),body.localCentre(),from,before,dynamics.position(),dynamics.rotation(),dynamics,BodyGravityField.NONE,paused));
        }
        return result;
    }
    /** Null retains ordinary flight when no hull or authority lease is nearby. */
    public static Vec3d travel(PlayerEntity p,Vec3d integrated) {
        if(world!=null&&world!=p.getWorld())clear();
        if(!DescentClientState.enabled){clear();return null;}
        if(waitingForShape!=null){p.setVelocity(Vec3d.ZERO);p.fallDistance=0;return lastVelocity==null?Vec3d.ZERO:BoardingSystem.mc(lastVelocity);}
        if(!active&&FlightBodySystem.nearby(p,integrated).isEmpty())return null;
        if(HISTORY.size()>=16){p.setVelocity(Vec3d.ZERO);p.fallDistance=0;return lastVelocity==null?Vec3d.ZERO:BoardingSystem.mc(lastVelocity);}
        var start=state(p,BoardingSystem.pure(integrated));
        if(active&&correction.lengthSquared()>4e-6) {
            var before=p.getPos();var fix=correction.length()>.2?correction.scaled(.2/correction.length()):correction;
            // Pure replay finds a safe correction against both current hull material and the
            // same loaded-world snapshot, then executes only that checked world displacement.
            var correcting=state(p,fix.scaled(20));
            var safe=DynamicBodyAttachment.freeFlight(correcting,motions(p,correcting,0),p.getWidth(),p.getHeight(),.05,BoardingWorldMotion.captureFlight(p,correcting,true));
            var actual=BoardingWorldMotion.captureFlight(p,correcting,false).move(correcting.feet(),Quat.IDENTITY,safe.feet().minus(correcting.feet()));
            correction=correction.minus(actual.displacement());start=state(p,BoardingSystem.pure(integrated));
        }
        Vec3 impulse=lastVelocity==null?new Vec3(0,0,0):start.velocity().minus(lastVelocity);
        var after=DynamicBodyAttachment.freeFlight(start,motions(p,start,0),p.getWidth(),p.getHeight(),.05,BoardingWorldMotion.captureFlight(p,start,false));
        p.setVelocity(BoardingSystem.mc(after.velocity().scaled(.05)));p.setOnGround(false);p.fallDistance=0;
        if(active) {
            int seq=sequence++;HISTORY.put(seq,new Prediction(impulse,after));
            if(ClientPlayNetworking.canSend(FlightBodyNetworking.Input.ID))ClientPlayNetworking.send(new FlightBodyNetworking.Input(epoch,seq));
        }
        lastVelocity=after.velocity();return BoardingSystem.mc(after.velocity());
    }
    private static void apply(FlightBodyNetworking.State packet) {
        var mc=MinecraftClient.getInstance();var p=mc.player;
        if(p==null||mc.world==null||!mc.world.getRegistryKey().getValue().equals(packet.dimension())||!DescentClientState.enabled)return;
        if(world!=null&&world!=mc.world)clear();if(packet.epoch()<epoch)return;
        if(packet.active()&&!ready(packet)){waitingForShape=packet;return;}
        waitingForShape=null;
        var velocity=new Vec3(packet.vx(),packet.vy(),packet.vz());var feet=new Vec3(packet.x(),packet.y(),packet.z());
        if(!Double.isFinite(feet.lengthSquared())||!Double.isFinite(velocity.lengthSquared()))return;
        if(epoch!=packet.epoch()||!active) {
            world=mc.world;epoch=packet.epoch();sequence=0;HISTORY.clear();
        } else if(packet.acknowledgment()>=sequence)return;
        active=packet.active();HISTORY.headMap(packet.acknowledgment(),true).clear();
        var replay=new DynamicBodyAttachment.State(feet,velocity,false,-1);int lead=0;
        for(var entry:HISTORY.entrySet()) {
            var v=replay.velocity().plus(entry.getValue().impulse());
            var from=new DynamicBodyAttachment.State(replay.feet(),v,false,-1);
            replay=DynamicBodyAttachment.freeFlight(from,motions(p,from,lead++),p.getWidth(),p.getHeight(),.05,BoardingWorldMotion.captureFlight(p,from,true));
            entry.setValue(new Prediction(entry.getValue().impulse(),replay));
        }
        correction=replay.feet().minus(BoardingSystem.pure(p.getPos()));lastVelocity=replay.velocity();
        DescentFlightMotion.contactVelocity(BoardingSystem.mc(lastVelocity));
        if(!active){HISTORY.clear();lastVelocity=null;correction=new Vec3(0,0,0);}
    }
}
