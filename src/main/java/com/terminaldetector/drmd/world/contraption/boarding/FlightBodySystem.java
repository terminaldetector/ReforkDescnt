package com.terminaldetector.drmd.world.contraption.boarding;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.network.FlightBodyNetworking;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Suit contacts run after the hull/joint phase, before walking acquisition. The suit flight
 * integrator still owns thrust. Movement packets cannot overwrite a nearby physical contact. */
public final class FlightBodySystem {
    private record Deferred(WeakReference<ServerPlayerEntity> player,long tick,ServerWorld world) {}
    private record Command(int sequence, FlightSystem.Steering steering) {}
    private static final class Lease {
        final WeakReference<ServerPlayerEntity> player;final ServerWorld world;final int epoch;
        final ArrayDeque<Command> pending=new ArrayDeque<>();int accepted=-1,ack=-1;long lastNear;
        Lease(ServerPlayerEntity p,int e){player=new WeakReference<>(p);world=p.getServerWorld();epoch=e;lastNear=world.getTime();}
    }
    private static final Map<UUID,Deferred> PENDING=new HashMap<>();
    private static final Map<UUID,Lease> LEASES=new HashMap<>();
    private static int epoch;
    private FlightBodySystem(){}
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(FlightBodySystem::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{for(var w:s.getWorlds())FlightVolumeInterest.close(w);});
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{PENDING.clear();LEASES.clear();});
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{FlightVolumeInterest.release(h.player.getServerWorld(),h.player.getUuid());PENDING.remove(h.player.getUuid());LEASES.remove(h.player.getUuid());});
        ServerWorldEvents.UNLOAD.register((s,w)->{FlightVolumeInterest.close(w);PENDING.values().removeIf(d->d.world==w);LEASES.values().removeIf(l->l.world==w);});
    }
    private static boolean pilot(ServerPlayerEntity p) {
        return p.isAlive()&&!p.isSpectator()&&!p.hasVehicle()&&!p.isSleeping()&&DescentPlayerData.get(p).isEnabled();
    }
    private static boolean eligible(ServerPlayerEntity p){return pilot(p)&&BoardingSystem.ready(p);}
    public static boolean nativeAuthority(net.minecraft.entity.player.PlayerEntity p){
        return CubicAvailability.backend(p.getWorld())!=CubicAvailability.Backend.STOCK;
    }
    public static boolean usesCommandClock(ServerPlayerEntity pilot) {
        return eligible(pilot) && nativeAuthority(pilot);
    }
    /** Conservative full-radius hull index plus the maximum one-tick translation of a hull.
     * Rotation is already enclosed by the physical radius; no entity/chunk scanning. */
    public static List<BlockBodyEntity> nearby(net.minecraft.entity.player.PlayerEntity p,Vec3d velocity) {
        var swept=p.getBoundingBox().stretch(velocity.multiply(.05)).expand(4+512*.05);
        return BodySpatialIndex.query(p.getWorld(),swept).stream().filter(b->!b.cells().isEmpty()
            &&new Box(b.massPosition().x()-b.radius(),b.massPosition().y()-b.radius(),b.massPosition().z()-b.radius(),
                b.massPosition().x()+b.radius(),b.massPosition().y()+b.radius(),b.massPosition().z()+b.radius()).intersects(swept)).toList();
    }
    public static boolean owns(ServerPlayerEntity p) {
        if(!pilot(p))return false;
        // Reserve positional authority before the ready handshake too. Never fall back to
        // unchecked vanilla C2S movement while native cube availability is unknown.
        if(nativeAuthority(p))return true;
        if(!BoardingSystem.ready(p))return false;
        var l=LEASES.get(p.getUuid());return l!=null&&l.player.get()==p&&l.world==p.getWorld()
            ||!nearby(p,DescentPlayerData.get(p).getFlightVelocity()).isEmpty();
    }
    public static boolean defer(ServerPlayerEntity p) {
        if(!owns(p))return false;
        PENDING.put(p.getUuid(),new Deferred(new WeakReference<>(p),p.getWorld().getTime(),p.getServerWorld()));return true;
    }
    public static boolean receive(ServerPlayerEntity p,FlightBodyNetworking.Input input) {
        var l=LEASES.get(p.getUuid());
        if(l==null||l.player.get()!=p||l.world!=p.getWorld()||!eligible(p)||input.epoch()!=l.epoch
            ||input.sequence()<0||input.sequence()<=l.accepted||(long)input.sequence()-l.accepted>128||l.pending.size()>=32)return false;
        var steering = nativeAuthority(p) ? FlightSystem.captureSteering(p) : null;
        l.accepted=input.sequence();l.pending.addLast(new Command(input.sequence(),steering));return true;
    }
    public static int epoch(ServerPlayerEntity p){var l=LEASES.get(p.getUuid());return l!=null&&l.player.get()==p?l.epoch:-1;}
    public static int acknowledgment(ServerPlayerEntity p){var l=LEASES.get(p.getUuid());return l!=null&&l.player.get()==p?l.ack:-1;}
    /** A portal is an explicit simulation-frame discontinuity, not another prediction command.
     * Momentum and entity pose belong to PortalTravel; only the obsolete clock is released here. */
    public static void discontinuity(ServerPlayerEntity p){LEASES.remove(p.getUuid());PENDING.remove(p.getUuid());}
    public static void tick(ServerWorld world) {
        FlightVolumeInterest.tick(world,BoardingSystem.players(world).stream().filter(FlightBodySystem::eligible).toList());
        // A network player's travel can run later than the world's body phase. Enroll real
        // pilots here too, independently of explicit travel calls from vehicles/fixtures.
        // owns() already rejects positional packet overwrite.
        // ServerPlayer travel may defer after END_WORLD_TICK. Its previous-tick request
        // contains no pose/velocity; refresh that enrollment for the CURRENT world phase.
        for(var player:BoardingSystem.players(world))if(owns(player))PENDING.put(player.getUuid(),
            new Deferred(new WeakReference<>(player),world.getTime(),world));
        var touched=new HashSet<BlockBodyEntity>();var motions=new HashMap<Integer,BodyMotion>();
        for(var d:PENDING.values().stream().filter(d->d.world==world).sorted(Comparator.comparing(d->{var p=d.player.get();return p==null?"":p.getUuid().toString();})).toList()) {
            var p=d.player.get();if(p==null||p.getWorld()!=world||!eligible(p)||d.tick!=world.getTime())continue;
            var data=DescentPlayerData.get(p);
            var l=LEASES.get(p.getUuid());if(l==null||l.player.get()!=p||l.world!=world){l=new Lease(p,++epoch);LEASES.put(p.getUuid(),l);}
            boolean nativeWorld = nativeAuthority(p);
            var command = l.pending.pollFirst();
            if (command != null) {
                l.ack = command.sequence();
                if (command.steering() != null) FlightSystem.tick(p, data, command.steering());
            }
            var candidates = nearby(p, data.getFlightVelocity());
            if(nativeWorld||!candidates.isEmpty())l.lastNear=world.getTime();
            if(nativeWorld&&candidates.isEmpty()&&command==null) {
                // A native free-flight step belongs to an actual client prediction command.
                // Advancing on empty server ticks outruns a lagging client's cube subscription
                // and unloads its current volume. Hold translation, not model momentum.
                p.setVelocity(data.getFlightVelocity().multiply(.05));p.setOnGround(false);p.fallDistance=0;
                if(p.networkHandler instanceof BoardingConnection c)c.drmd$bodyMovementOwned();
                sync(p,l,true);continue;
            }
            var bodies=new ArrayList<BodyMotion>();for(var b:candidates){bodies.add(motions.computeIfAbsent(b.getId(),id->b.boardingMotion()));touched.add(b);}
            var state=new DynamicBodyAttachment.State(BoardingSystem.pure(p.getPos()),BoardingSystem.pure(data.getFlightVelocity()),false,-1);
            var after=DynamicBodyAttachment.freeFlight(state,bodies,p.getWidth(),p.getHeight(),.05,BoardingWorldMotion.captureFlight(p,state,false));
            float damage=com.terminaldetector.drmd.flight.CrashDamage.damageFor(state.velocity().minus(after.velocity()).length());
            if(damage>0)p.damage(world.getDamageSources().flyIntoWall(),damage);
            p.setVelocity(BoardingSystem.mc(after.velocity().scaled(.05)));data.setFlightVelocity(BoardingSystem.mc(after.velocity()));
            p.setOnGround(false);p.fallDistance=0;
            if(p.networkHandler instanceof BoardingConnection c)c.drmd$bodyMovementOwned();
            boolean active=world.getTime()-l.lastNear<=4;sync(p,l,active);
            if(!active)LEASES.remove(p.getUuid());
        }
        PENDING.values().removeIf(d->d.world==world);
        for(var b:touched)b.publishPhysicsState();
        for(var entry:List.copyOf(LEASES.entrySet())) {
            var l=entry.getValue();if(l.world!=world)continue;var p=l.player.get();
            if(p==null||p.isRemoved()||p.getWorld()!=world||!eligible(p)) {
                if(p!=null&&!p.isRemoved()&&p.getWorld()==world)sync(p,l,false);
                LEASES.remove(entry.getKey());
            }
        }
    }
    private static void sync(ServerPlayerEntity p,Lease l,boolean active) {
        if(!ServerPlayNetworking.canSend(p,FlightBodyNetworking.State.ID))return;
        var v=DescentPlayerData.get(p).getFlightVelocity();
        var geometry=nearby(p,v).stream().map(b->new FlightBodyNetworking.Geometry(b.getId(),b.shapeRevision())).toList();
        ServerPlayNetworking.send(p,new FlightBodyNetworking.State(l.epoch,l.ack,active,p.getX(),p.getY(),p.getZ(),v.x,v.y,v.z,p.getWorld().getRegistryKey().getValue(),geometry));
    }
}
