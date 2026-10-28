package com.terminaldetector.drmd.client.flight;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.network.BoardingNetworking;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Owner prediction; received positions correct prediction errors, never attach to a local anchor. */
public final class BoardingClient {
    private static DynamicBodyAttachment.State state;
    private record Prediction(DynamicBodyAttachment.Input input,DynamicBodyAttachment.State state) {}
    private static final NavigableMap<Integer,Prediction> HISTORY=new TreeMap<>();
    private static Object world;
    private static int epoch=-1,sequence;
    private static Vec3 correction=new Vec3(0,0,0);
    private static boolean jumpHeld;
    private static BoardingNetworking.BodyState bodySnapshot;
    private static BoardingNetworking.State waitingForShape;
    private static int snapshotId=-1;
    private static ClientPlayerEntity readyOwner;
    public record BodyView(Vec3 position,Quat rotation) {}
    private static BodyView beforeView,currentView;
    private static int viewBody=-1,viewAge=-1;
    public static BodyView bodyView(BlockBodyEntity body,float delta) {
        if(body.getId()!=viewBody||currentView==null||state==null)return null;
        double alpha=Math.max(0,Math.min(1,delta));
        return new BodyView(beforeView.position().plus(currentView.position().minus(beforeView.position()).scaled(alpha)),
            com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.interpolate(beforeView.rotation(),currentView.rotation(),alpha).normalized());
    }
    private static void publishBodyView(ClientPlayerEntity player,java.util.List<BodyMotion> bodies) {
        for(var b:bodies)if(b.id==snapshotId&&(b.hasGravity()||b.id==viewBody)) {
            var next=new BodyView(b.position(1),b.rotation(1));
            beforeView=currentView==null||viewBody!=b.id?new BodyView(b.position(0),b.rotation(0)):viewAge==player.age?beforeView:currentView;
            currentView=next;viewBody=b.id;viewAge=player.age;return;
        }
    }
    private BoardingClient() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BoardingNetworking.State.ID,(p,c)->c.client().execute(()->apply(p)));
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{clear();BoardingPose.clearClient();readyOwner=null;});
        ClientPlayConnectionEvents.JOIN.register((h,s,c)->{clear();BoardingPose.clearClient();readyOwner=null;});
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(waitingForShape!=null&&c.world!=null) {
                var pending=waitingForShape;int id=pending.pose().gravityOwner()>=0?pending.pose().gravityOwner():pending.support();
                if(c.world.getEntityById(id) instanceof BlockBodyEntity b&&b.shapeRevision()==pending.body().shapeRevision()){waitingForShape=null;apply(pending);}
            }
            if(c.player!=null&&c.player!=readyOwner&&ClientPlayNetworking.canSend(BoardingNetworking.Ready.ID)) {
                ClientPlayNetworking.send(new BoardingNetworking.Ready(BoardingNetworking.PROTOCOL));readyOwner=c.player;
            }
        });
    }
    public static boolean drives(ClientPlayerEntity p) {
        return state!=null&&p.getWorld()==world&&!DescentPlayerData.get(p).isEnabled()&&!p.isSpectator()&&!p.hasVehicle();
    }
    public static DynamicBodyAttachment.State state(){return state;}
    public static int epoch(){return epoch;}
    public static int sequence(){return sequence;}
    /** Prediction at the same processed input as a server acknowledgment, for diagnostics. */
    public static DynamicBodyAttachment.State predictedState(int acknowledgment){
        var p=HISTORY.get(acknowledgment);return p==null?null:p.state();
    }
    public static void clear(){var p=MinecraftClient.getInstance().player;if(p!=null)BoardingPose.clear(p);state=null;world=null;epoch=-1;sequence=0;HISTORY.clear();correction=new Vec3(0,0,0);jumpHeld=false;bodySnapshot=null;waitingForShape=null;snapshotId=-1;beforeView=null;currentView=null;viewBody=-1;viewAge=-1;}
    private static void apply(BoardingNetworking.State p) {
        var mc=MinecraftClient.getInstance();var player=mc.player;
        if(player==null||mc.world==null)return;
        if(!mc.world.getRegistryKey().getValue().equals(p.dimension()))return;
        if(DescentPlayerData.get(player).isEnabled()){clear();return;}
        if(world!=null&&world!=mc.world)clear();
        if(p.epoch()<epoch)return;
        Vec3 feet=new Vec3(p.x(),p.y(),p.z()),velocity=new Vec3(p.vx(),p.vy(),p.vz());
        if(!Double.isFinite(feet.lengthSquared())||!Double.isFinite(velocity.lengthSquared()))return;
        var authoritative=new DynamicBodyAttachment.State(feet,velocity,p.grounded(),p.support(),p.pose().frame(),p.pose().gravityOwner());
        if(!p.active()) {
            if(state!=null) {
                player.move(MovementType.SELF,BoardingSystem.mc(feet.minus(BoardingSystem.pure(player.getPos()))));
                player.setVelocity(BoardingSystem.mc(velocity.scaled(.05)));player.setOnGround(false);
            }
            clear();return;
        }
        if(p.body()!=null) {
            int id=p.pose().gravityOwner()>=0?p.pose().gravityOwner():p.support();
            if(!(mc.world.getEntityById(id) instanceof BlockBodyEntity b)||b.shapeRevision()!=p.body().shapeRevision()) {waitingForShape=p;return;}
        }
        waitingForShape=null;
        bodySnapshot=p.body();snapshotId=p.pose().gravityOwner()>=0?p.pose().gravityOwner():p.support();
        if(state==null||epoch!=p.epoch()) {
            world=mc.world;epoch=p.epoch();sequence=0;HISTORY.clear();correction=new Vec3(0,0,0);
            var origin=new DynamicBodyAttachment.State(BoardingSystem.pure(player.getPos()),velocity,p.grounded(),p.support(),authoritative.frame(),authoritative.gravityOwner());
            BoardingWorldMotion.capture(player,origin,false).move(origin.feet(),origin.frame(),feet.minus(origin.feet()));
            state=new DynamicBodyAttachment.State(BoardingSystem.pure(player.getPos()),velocity,p.grounded(),p.support(),authoritative.frame(),authoritative.gravityOwner());
            BoardingPose.publish(player,state.frame());
        } else {
            HISTORY.headMap(p.acknowledgment(),true).clear();
            var replay=authoritative;int lead=0;
            for(var entry:HISTORY.entrySet()) {
                var command=entry.getValue().input();
                var bodies=nearby(player,replay,lead++);
                var worldCommand=new DynamicBodyAttachment.Input(command.strafe(),command.forward(),command.jump(),replay.frame().rotate(command.look()));
                replay=DynamicBodyAttachment.step(replay,worldCommand,bodies,player.getWidth(),player.getHeight(),.05,BoardingWorldMotion.capture(player,replay,true));
                entry.setValue(new Prediction(command,replay));
            }
            correction=replay.feet().minus(BoardingSystem.pure(player.getPos()));
            orientLook(player,state.frame(),replay.frame(),replay.gravityOwner());
            state=new DynamicBodyAttachment.State(BoardingSystem.pure(player.getPos()),replay.velocity(),replay.grounded(),replay.support(),replay.frame(),replay.gravityOwner());
            BoardingPose.publish(player,state.frame());
        }
    }
    public static void travel(ClientPlayerEntity player,Vec3d movement) {
        if(!drives(player)||waitingForShape!=null)return;
        // Backpressure keeps a stalled connection from growing history or permanently outrunning
        // the server's bounded command queue. Resume as soon as an acknowledgment arrives.
        if(HISTORY.size()>=16){player.fallDistance=0;return;}
        var mc=MinecraftClient.getInstance();
        Vec3 offset=correction;double length=offset.length();
        // Smooth normal prediction noise. After a loading stall, limiting a multi-block
        // authoritative correction to .2 per input leaves the physical actor behind the deck.
        // Recover that accumulated error through collision-checked movement before predicting
        // the next command. This is network reconciliation, never a ship-local follow anchor.
        if(length>.2&&length<=.5)offset=offset.scaled(.2/length);
        if(length>.002){Vec3d before=player.getPos();BoardingWorldMotion.capture(player,state,false).move(BoardingSystem.pure(before),state.frame(),offset);correction=correction.minus(BoardingSystem.pure(player.getPos().subtract(before)));}
        Vec3 feet=BoardingSystem.pure(player.getPos());state=new DynamicBodyAttachment.State(feet,state.velocity(),state.grounded(),state.support(),state.frame(),state.gravityOwner());
        boolean jump=mc.currentScreen==null&&mc.options.jumpKey.isPressed();
        double strafe=mc.currentScreen==null?Math.max(-1,Math.min(1,movement.x)):0;
        double forward=mc.currentScreen==null?Math.max(-1,Math.min(1,movement.z)):0;
        Vec3 look=state.frame().inverse().rotate(BoardingSystem.pure(player.getRotationVec(1)));
        var input=new DynamicBodyAttachment.Input(strafe,forward,jump&&!jumpHeld,look);jumpHeld=jump;
        int seq=sequence++;
        ClientPlayNetworking.send(new BoardingNetworking.Input(epoch,seq,(float)strafe,(float)forward,input.jump(),(float)look.x(),(float)look.y(),(float)look.z()));
        var bodies=nearby(player,state,HISTORY.size());
        var beforeFrame=state.frame();
        var worldInput=new DynamicBodyAttachment.Input(strafe,forward,input.jump(),beforeFrame.rotate(look));
        state=DynamicBodyAttachment.step(state,worldInput,bodies,player.getWidth(),player.getHeight(),.05,BoardingWorldMotion.capture(player,state,false));
        orientLook(player,beforeFrame,state.frame(),state.gravityOwner());BoardingPose.publish(player,state.frame());publishBodyView(player,bodies);
        player.setVelocity(BoardingSystem.mc(state.velocity().scaled(.05)));player.setOnGround(state.grounded());player.fallDistance=0;
        HISTORY.put(seq,new Prediction(input,state));while(HISTORY.size()>32)HISTORY.pollFirstEntry();
    }
    private static java.util.List<BodyMotion> nearby(ClientPlayerEntity player,DynamicBodyAttachment.State at,int lead) {
        var bodies=new ArrayList<BodyMotion>();
        Vec3d shift=BoardingSystem.mc(at.feet()).subtract(player.getPos());
        var entities=new ArrayList<>(player.getWorld().getEntitiesByClass(BlockBodyEntity.class,player.getBoundingBox().offset(shift)
            .expand(4+Math.min(8,at.velocity().length()*.05)),b->!b.isRemoved()));
        entities.sort(Comparator.comparingInt(BlockBodyEntity::getId));for(var body:entities)bodies.add(predictedMotion(body,lead));
        return bodies;
    }
    private static void orientLook(ClientPlayerEntity player,Quat before,Quat after,int gravityOwner) {
        Vec3 look=PassengerFrame.releaseLook(BoardingSystem.pure(player.getRotationVec(1)),before,after,gravityOwner);
        var angles=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(look);
        player.setYaw((float)angles.yawDegrees());player.setPitch((float)angles.pitchDegrees());
    }
    private static BodyMotion predictedMotion(BlockBodyEntity body,int ticks) {
        var snapshot=body.getId()==snapshotId?bodySnapshot:null;
        var p=new com.terminaldetector.drmd.d6.D6PhysicsBody().withMass(body.physicsMass()).withInertia(body.physics().inertia());
        if(snapshot!=null)p.withPosition(new Vec3(snapshot.x(),snapshot.y(),snapshot.z()))
            .withRotation(new Quat(snapshot.qx(),snapshot.qy(),snapshot.qz(),snapshot.qw()))
            .withLinearVelocity(new Vec3(snapshot.vx(),snapshot.vy(),snapshot.vz()))
            .withAngularMomentum(new Vec3(snapshot.lx(),snapshot.ly(),snapshot.lz()));
        else p.withPosition(body.massPosition()).withRotation(body.rotation())
            .withLinearVelocity(body.physics().linearVelocity()).withAngularMomentum(body.physics().angularMomentum());
        boolean paused=snapshot==null?body.boardingMotionPaused():snapshot.paused();
        int lead=snapshot==null?0:Math.min(4,ticks);if(!paused)for(int i=0;i<lead;i++)p.step(.05);
        Vec3 from=p.position();Quat before=p.rotation();if(!paused)p.step(.05);
        Vec3 gravity=snapshot==null?body.passengerGravity():new Vec3(snapshot.gx(),snapshot.gy(),snapshot.gz());
        return new BodyMotion(body.getId(),body.collider(),body.localCentre(),from,before,p.position(),p.rotation(),p,body.passengerField(gravity),paused);
    }
}
