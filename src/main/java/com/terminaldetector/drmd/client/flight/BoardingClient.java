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
    private static int snapshotId=-1;
    private static ClientPlayerEntity readyOwner;
    private BoardingClient() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BoardingNetworking.State.ID,(p,c)->c.client().execute(()->apply(p)));
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{clear();readyOwner=null;});
        ClientPlayConnectionEvents.JOIN.register((h,s,c)->{clear();readyOwner=null;});
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(c.player!=null&&c.player!=readyOwner&&ClientPlayNetworking.canSend(BoardingNetworking.Ready.ID)) {
                ClientPlayNetworking.send(new BoardingNetworking.Ready(1));readyOwner=c.player;
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
    public static void clear(){state=null;world=null;epoch=-1;sequence=0;HISTORY.clear();correction=new Vec3(0,0,0);jumpHeld=false;bodySnapshot=null;snapshotId=-1;}
    private static void apply(BoardingNetworking.State p) {
        var mc=MinecraftClient.getInstance();var player=mc.player;
        if(player==null||mc.world==null)return;
        if(!mc.world.getRegistryKey().getValue().equals(p.dimension()))return;
        if(DescentPlayerData.get(player).isEnabled()){clear();return;}
        if(world!=null&&world!=mc.world)clear();
        if(p.epoch()<epoch)return;
        Vec3 feet=new Vec3(p.x(),p.y(),p.z()),velocity=new Vec3(p.vx(),p.vy(),p.vz());
        if(!Double.isFinite(feet.lengthSquared())||!Double.isFinite(velocity.lengthSquared()))return;
        var authoritative=new DynamicBodyAttachment.State(feet,velocity,p.grounded(),p.support());
        if(!p.active()) {
            if(state!=null) {
                player.move(MovementType.SELF,BoardingSystem.mc(feet.minus(BoardingSystem.pure(player.getPos()))));
                player.setVelocity(BoardingSystem.mc(velocity.scaled(.05)));player.setOnGround(false);
            }
            clear();return;
        }
        bodySnapshot=p.body();snapshotId=p.support();
        if(state==null||epoch!=p.epoch()) {
            world=mc.world;epoch=p.epoch();sequence=0;HISTORY.clear();correction=new Vec3(0,0,0);
            player.move(MovementType.SELF,BoardingSystem.mc(feet.minus(BoardingSystem.pure(player.getPos()))));
            state=new DynamicBodyAttachment.State(BoardingSystem.pure(player.getPos()),velocity,p.grounded(),p.support());
        } else {
            HISTORY.headMap(p.acknowledgment(),true).clear();
            var replay=authoritative;int lead=0;
            for(var entry:HISTORY.entrySet()) {
                var command=entry.getValue().input();
                var bodies=nearby(player,replay,lead++);
                replay=DynamicBodyAttachment.step(replay,command,bodies,player.getWidth(),player.getHeight(),.05,
                    (at,delta)->clipWorld(player,at,delta));
                entry.setValue(new Prediction(command,replay));
            }
            correction=replay.feet().minus(BoardingSystem.pure(player.getPos()));
            state=new DynamicBodyAttachment.State(BoardingSystem.pure(player.getPos()),replay.velocity(),replay.grounded(),replay.support());
        }
    }
    public static void travel(ClientPlayerEntity player,Vec3d movement) {
        if(!drives(player))return;
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
        if(length>.002){Vec3d before=player.getPos();player.move(MovementType.SELF,BoardingSystem.mc(offset));correction=correction.minus(BoardingSystem.pure(player.getPos().subtract(before)));}
        Vec3 feet=BoardingSystem.pure(player.getPos());state=new DynamicBodyAttachment.State(feet,state.velocity(),state.grounded(),state.support());
        boolean jump=mc.currentScreen==null&&mc.options.jumpKey.isPressed();
        double strafe=mc.currentScreen==null?Math.max(-1,Math.min(1,movement.x)):0;
        double forward=mc.currentScreen==null?Math.max(-1,Math.min(1,movement.z)):0;
        Vec3 look=BoardingSystem.pure(player.getRotationVec(1));
        var input=new DynamicBodyAttachment.Input(strafe,forward,jump&&!jumpHeld,look);jumpHeld=jump;
        int seq=sequence++;
        ClientPlayNetworking.send(new BoardingNetworking.Input(epoch,seq,(float)strafe,(float)forward,input.jump(),(float)look.x(),(float)look.y(),(float)look.z()));
        var bodies=nearby(player,state,HISTORY.size());
        state=DynamicBodyAttachment.step(state,input,bodies,player.getWidth(),player.getHeight(),.05,(at,delta)->{
            Vec3d before=player.getPos();player.move(MovementType.SELF,BoardingSystem.mc(delta));return BoardingSystem.pure(player.getPos().subtract(before));
        });
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
    private static Vec3 clipWorld(ClientPlayerEntity player,Vec3 at,Vec3 delta) {
        var box=player.getBoundingBox().offset(BoardingSystem.mc(at).subtract(player.getPos()));
        Vec3d allowed=net.minecraft.entity.Entity.adjustMovementForCollisions(player,BoardingSystem.mc(delta),box,player.getWorld(),
            player.getWorld().getEntityCollisions(player,box.stretch(BoardingSystem.mc(delta))));
        return BoardingSystem.pure(allowed);
    }
    private static BodyMotion predictedMotion(BlockBodyEntity body,int ticks) {
        var snapshot=body.getId()==snapshotId?bodySnapshot:null;
        var p=new com.terminaldetector.drmd.d6.D6PhysicsBody().withMass(body.physicsMass()).withInertia(body.physics().inertia());
        if(snapshot!=null)p.withPosition(new Vec3(snapshot.x(),snapshot.y(),snapshot.z()))
            .withRotation(new Quat(snapshot.qx(),snapshot.qy(),snapshot.qz(),snapshot.qw()))
            .withLinearVelocity(new Vec3(snapshot.vx(),snapshot.vy(),snapshot.vz()))
            .withAngularMomentum(new Vec3(snapshot.lx(),snapshot.ly(),snapshot.lz()));
        else p.withPosition(BoardingSystem.pure(body.getPos())).withRotation(body.rotation())
            .withLinearVelocity(body.physics().linearVelocity()).withAngularMomentum(body.physics().angularMomentum());
        int lead=snapshot==null?0:Math.min(4,ticks);for(int i=0;i<lead;i++)p.step(.05);
        Vec3 from=p.position();Quat before=p.rotation();p.step(.05);
        return new BodyMotion(body.getId(),body.collider(),body.localCentre(),from,before,p.position(),p.rotation(),p);
    }
}
