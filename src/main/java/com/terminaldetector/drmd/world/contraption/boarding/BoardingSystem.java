package com.terminaldetector.drmd.world.contraption.boarding;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.network.BoardingNetworking;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.gravity.FootGravitySystem;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Authoritative first boarding runtime: world-UP walking, only on genuine compound contacts. */
public final class BoardingSystem {
    private record Previous(WeakReference<ServerWorld> world,Vec3 feet) {}
    private static final class Lease {
        private record Command(int sequence,DynamicBodyAttachment.Input input) {}
        final WeakReference<ServerPlayerEntity> owner;
        final ServerWorld world;
        final int epoch;
        DynamicBodyAttachment.State state;
        DynamicBodyAttachment.Input input=DynamicBodyAttachment.Input.idle();
        int sequence=-1,acceptedSequence=-1;long inputTick;
        final ArrayDeque<Command> pending=new ArrayDeque<>();
        Lease(ServerPlayerEntity p,DynamicBodyAttachment.State s,int e){owner=new WeakReference<>(p);world=p.getServerWorld();state=s;epoch=e;}
    }
    private static final Map<UUID,Lease> LEASES=new HashMap<>();
    private static final Map<UUID,Previous> PREVIOUS=new HashMap<>();
    private static final Map<UUID,WeakReference<ServerPlayerEntity>> READY=new HashMap<>();
    private static int epoch;
    private BoardingSystem() {}
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(BoardingSystem::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{LEASES.clear();PREVIOUS.clear();READY.clear();});
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{LEASES.remove(h.player.getUuid());PREVIOUS.remove(h.player.getUuid());READY.remove(h.player.getUuid());});
    }
    public static boolean ready(ServerPlayerEntity p,int version) {
        if(version!=BoardingNetworking.PROTOCOL)return false;READY.put(p.getUuid(),new WeakReference<>(p));return true;
    }
    public static boolean ready(ServerPlayerEntity p){var ref=READY.get(p.getUuid());return ref!=null&&ref.get()==p;}
    public static boolean drives(ServerPlayerEntity p) {
        Lease l=LEASES.get(p.getUuid());return l!=null&&l.owner.get()==p&&l.world==p.getWorld()&&!DescentPlayerData.get(p).isEnabled();
    }
    public static DynamicBodyAttachment.State state(ServerPlayerEntity p){var l=LEASES.get(p.getUuid());return l==null?null:l.state;}
    public static int epoch(ServerPlayerEntity p){var l=LEASES.get(p.getUuid());return l==null?-1:l.epoch;}
    public static boolean receive(ServerPlayerEntity p,BoardingNetworking.Input packet) {
        Lease l=LEASES.get(p.getUuid());
        if(l==null||!drives(p)||!eligible(p)||packet.epoch()!=l.epoch||packet.sequence()<0
            ||packet.sequence()<=l.acceptedSequence||(long)packet.sequence()-l.acceptedSequence>128||l.pending.size()>=32)return false;
        DynamicBodyAttachment.Input command;
        try {command=new DynamicBodyAttachment.Input(packet.strafe(),packet.forward(),packet.jump(),
            new Vec3(packet.lookX(),packet.lookY(),packet.lookZ()));}
        catch(IllegalArgumentException invalid){return false;}
        l.acceptedSequence=packet.sequence();l.pending.addLast(new Lease.Command(packet.sequence(),command));return true;
    }
    private static boolean eligible(ServerPlayerEntity p) {
        return p.isAlive()&&!p.isSpectator()&&!p.hasVehicle()&&!p.getAbilities().flying&&!p.isSleeping()
            &&!p.isTouchingWater()&&!p.isInLava()&&!DescentPlayerData.get(p).isEnabled()&&!FootGravitySystem.isActive(p);
    }
    public static void tick(ServerWorld world) {
        var motions=new HashMap<Integer,BodyMotion>();var touched=new HashSet<BlockBodyEntity>();
        var publish=new LinkedHashMap<ServerPlayerEntity,Lease>();
        var players=new ArrayList<>(world.getPlayers());players.sort(Comparator.comparing(p->p.getUuid().toString()));
        for(var p:players) {
            Lease l=LEASES.get(p.getUuid());Vec3 feet=pure(p.getPos());
            if(!eligible(p)||!ready(p)) {if(l!=null)release(p,l,pure(p.getVelocity()).scaled(20));PREVIOUS.put(p.getUuid(),new Previous(new WeakReference<>(world),feet));continue;}
            var candidates=new ArrayList<>(com.terminaldetector.drmd.world.contraption.BodySpatialIndex.query(world,p.getBoundingBox().expand(4+Math.min(8,p.getVelocity().length()))));
            candidates.sort(Comparator.comparingInt(BlockBodyEntity::getId));
            var near=new ArrayList<BodyMotion>();
            for(var b:candidates){near.add(motions.computeIfAbsent(b.getId(),id->b.boardingMotion()));touched.add(b);}
            if(l==null) {
                l=acquire(p,near);if(l!=null){LEASES.put(p.getUuid(),l);publish.put(p,l);}
                PREVIOUS.put(p.getUuid(),new Previous(new WeakReference<>(world),pure(p.getPos())));continue;
            }
            if(!drives(p)||feet.minus(l.state.feet()).length()>2) {
                release(p,l,pure(p.getVelocity()).scaled(20));continue;
            }
            if(!l.pending.isEmpty()) {
                var next=l.pending.removeFirst();l.sequence=next.sequence();l.input=next.input();l.inputTick=world.getTime();
            }
            var command=world.getTime()-l.inputTick<=4?l.input:DynamicBodyAttachment.Input.idle();
            Quat beforeFrame=l.state.frame();
            // Preserve external impulses (knockback/explosions); the contact motor owns gravity.
            // The real collision-checked actor is authoritative. Dimension/contact adjustments
            // and external moves can alter it between steps. Starting from stale cached feet
            // makes every subsequent displacement preserve that offset forever, separating
            // interaction eyes from the position sent to clients. Never teleport to the cache.
            l.state=new DynamicBodyAttachment.State(feet,pure(p.getVelocity()).scaled(20),l.state.grounded(),l.state.support(),beforeFrame,l.state.gravityOwner());
            var worldCommand=new DynamicBodyAttachment.Input(command.strafe(),command.forward(),command.jump(),beforeFrame.rotate(command.look()));
            l.state=DynamicBodyAttachment.step(l.state,worldCommand,near,p.getWidth(),p.getHeight(),.05,BoardingWorldMotion.capture(p,l.state,false));
            BoardingPose.publish(p,l.state.frame());
            Vec3 look=PassengerFrame.releaseLook(worldCommand.look(),beforeFrame,l.state.frame(),l.state.gravityOwner());
            var angles=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(look);
            p.setYaw((float)angles.yawDegrees());p.setPitch((float)angles.pitchDegrees());
            if(l.input.jump())l.input=new DynamicBodyAttachment.Input(l.input.strafe(),l.input.forward(),false,l.input.look());
            p.setVelocity(mc(l.state.velocity().scaled(.05)));p.setOnGround(l.state.grounded());p.fallDistance=0;
            if(p.networkHandler instanceof BoardingConnection connection)connection.drmd$bodyMovementOwned();
            if(!l.state.grounded()&&l.state.gravityOwner()<0&&l.state.frame().equals(Quat.IDENTITY))release(p,l,l.state.velocity());else publish.put(p,l);
            PREVIOUS.put(p.getUuid(),new Previous(new WeakReference<>(world),pure(p.getPos())));
        }
        for(var body:touched)body.publishPhysicsState();
        publish.forEach((p,l)->sync(p,l,true));
        LEASES.entrySet().removeIf(e->{var l=e.getValue();var p=l.owner.get();return p==null||p.isRemoved()||p.getWorld()!=l.world;});
        var present=new HashSet<UUID>();for(var p:players)present.add(p.getUuid());
        PREVIOUS.entrySet().removeIf(e->e.getValue().world.get()==world&&!present.contains(e.getKey()));
        READY.entrySet().removeIf(e->{var p=e.getValue().get();return p==null||p.isRemoved();});
    }
    private static Lease acquire(ServerPlayerEntity p,List<BodyMotion> bodies) {
        Vec3 feet=pure(p.getPos());
        for(var body:bodies)if(body.gravity(feet.plus(new Vec3(0,p.getHeight()/2,0)),1).isPresent()) {
            var l=new Lease(p,new DynamicBodyAttachment.State(feet,pure(p.getVelocity()).scaled(20),false,-1,Quat.IDENTITY,body.id),++epoch);
            BoardingPose.publish(p,Quat.IDENTITY);return l;
        }
        Previous prev=PREVIOUS.get(p.getUuid());
        Vec3 from=prev!=null&&prev.world.get()==p.getWorld()?prev.feet:feet;
        Vec3 delta=feet.minus(from);boolean swept=delta.length()<8;
        BodyMotion best=null;double fraction=Double.POSITIVE_INFINITY;
        for(var b:bodies) {
            if(swept) {
                var hit=b.query(0).sweep(DynamicBodyAttachment.box(from,p.getWidth(),p.getHeight()),delta,b.position(1).minus(b.position(0)));
                if(hit.isPresent()&&hit.get().normal().y()>.6&&hit.get().fraction()<fraction){best=b;fraction=hit.get().fraction();}
            }
            if(best==null)for(var c:b.query(1).contacts(DynamicBodyAttachment.box(feet,p.getWidth(),p.getHeight()),.002))
                if(c.normal().y()>.6&&c.penetration()<.15){best=b;fraction=1;break;}
        }
        if(best==null)return null;
        Vec3 at=from.plus(delta.scaled(Math.min(1,fraction)));Vec3 velocity=delta.lengthSquared()>1e-10&&swept?delta.scaled(20):pure(p.getVelocity()).scaled(20);
        // Contact correction only at impact; ordinary movement handles static-world obstructions.
        p.move(net.minecraft.entity.MovementType.SELF,mc(at.minus(feet)));at=pure(p.getPos());
        if(best.query(1).contacts(DynamicBodyAttachment.box(at,p.getWidth(),p.getHeight()),.16).stream().noneMatch(c->c.normal().y()>.6))return null;
        var actor=new com.terminaldetector.drmd.d6.D6PhysicsBody().withMass(1)
            .withInertia(com.terminaldetector.drmd.d6.D6Mat3.diagonal(1e12,1e12,1e12)).withPosition(at.plus(new Vec3(0,p.getHeight()/2,0))).withLinearVelocity(velocity);
        for(var c:best.query(1).contacts(DynamicBodyAttachment.box(at,p.getWidth(),p.getHeight()),.16))if(c.normal().y()>.6) {
            if(velocity.minus(best.surfaceVelocity(c.point(),1)).dot(c.normal())>.1)return null;
            var proxy=best.proxy(1);Vec3 before=proxy.linearVelocity();
            var constraint=new com.terminaldetector.drmd.physics.collision.BodyContactConstraint(actor,proxy,c.point(),c.normal(),.65);
            for(int i=0;i<8;i++)constraint.solve();best.react(proxy.linearVelocity().minus(before).scaled(proxy.mass()),c.point().minus(proxy.position()));
        }
        var l=new Lease(p,new DynamicBodyAttachment.State(at,actor.linearVelocity(),true,best.id),++epoch);
        BoardingPose.publish(p,l.state.frame());
        p.setOnGround(true);p.setVelocity(mc(l.state.velocity().scaled(.05)));p.fallDistance=0;
        if(p.networkHandler instanceof BoardingConnection connection)connection.drmd$bodyMovementOwned();return l;
    }
    private static void release(ServerPlayerEntity p,Lease l,Vec3 velocity) {
        LEASES.remove(p.getUuid());l.state=new DynamicBodyAttachment.State(pure(p.getPos()),velocity,false,-1);
        BoardingPose.clear(p);
        p.setVelocity(mc(velocity.scaled(.05)));p.setOnGround(false);
        if(p.networkHandler instanceof BoardingConnection c)c.drmd$bodyMovementOwned();sync(p,l,false);
    }
    private static void sync(ServerPlayerEntity p,Lease l,boolean active) {
        if(p.networkHandler==null||!ServerPlayNetworking.canSend(p,BoardingNetworking.State.ID))return;
        var s=l.state;BoardingNetworking.BodyState snapshot=null;
        if(active&&p.getServerWorld().getEntityById(s.gravityOwner()>=0?s.gravityOwner():s.support()) instanceof BlockBodyEntity body) {
            var q=body.rotation();var v=body.physics().linearVelocity();var m=body.physics().angularMomentum();
            var g=body.passengerGravity();
            var position=body.massPosition();
            snapshot=new BoardingNetworking.BodyState(position.x(),position.y(),position.z(),q.x(),q.y(),q.z(),q.w(),v.x(),v.y(),v.z(),m.x(),m.y(),m.z(),g.x(),g.y(),g.z(),body.boardingMotionPaused(),body.shapeRevision());
        }
        ServerPlayNetworking.send(p,new BoardingNetworking.State(l.epoch,l.sequence,active,s.support(),s.feet().x(),s.feet().y(),s.feet().z(),
            s.velocity().x(),s.velocity().y(),s.velocity().z(),s.grounded(),new BoardingNetworking.PassengerPose(s.frame().x(),s.frame().y(),s.frame().z(),s.frame().w(),s.gravityOwner()),snapshot,p.getWorld().getRegistryKey().getValue()));
    }
    public static Vec3 pure(Vec3d v){return new Vec3(v.x,v.y,v.z);}
    public static Vec3d mc(Vec3 v){return new Vec3d(v.x(),v.y(),v.z());}
}
