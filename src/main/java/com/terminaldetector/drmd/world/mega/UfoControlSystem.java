package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Connection-scoped control ownership; the existing hull retains all physical state. */
public final class UfoControlSystem {
    public static final BlockPos CONSOLE=new BlockPos(3,-1,0);
    public record Input(int epoch,int sequence,float forward,float strafe,float vertical,float pitch,float yaw,float roll) implements CustomPayload {
        public static final Id<Input> ID=new Id<>(Identifier.of("drmd","ufo_control"));
        public static final PacketCodec<RegistryByteBuf,Input> CODEC=PacketCodec.of((p,b)->{b.writeVarInt(p.epoch);b.writeVarInt(p.sequence);b.writeFloat(p.forward);b.writeFloat(p.strafe);b.writeFloat(p.vertical);b.writeFloat(p.pitch);b.writeFloat(p.yaw);b.writeFloat(p.roll);},b->new Input(b.readVarInt(),b.readVarInt(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat()));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Fire(int epoch,int sequence,int mask) implements CustomPayload {
        public static final Id<Fire> ID=new Id<>(Identifier.of("drmd","ufo_fire"));
        public static final PacketCodec<RegistryByteBuf,Fire> CODEC=PacketCodec.of((p,b)->{b.writeVarInt(p.epoch);b.writeVarInt(p.sequence);b.writeByte(p.mask);},b->new Fire(b.readVarInt(),b.readVarInt(),b.readUnsignedByte()));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record TurretInput(int epoch,int sequence,float yaw) implements CustomPayload {
        public static final Id<TurretInput> ID=new Id<>(Identifier.of("drmd","ufo_turret"));
        public static final PacketCodec<RegistryByteBuf,TurretInput> CODEC=PacketCodec.of((p,b)->{b.writeVarInt(p.epoch);b.writeVarInt(p.sequence);b.writeFloat(p.yaw);},b->new TurretInput(b.readVarInt(),b.readVarInt(),b.readFloat()));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record State(UUID body,int epoch,boolean active,Identifier dimension) implements CustomPayload {
        public static final Id<State> ID=new Id<>(Identifier.of("drmd","ufo_control_state"));
        public static final PacketCodec<RegistryByteBuf,State> CODEC=PacketCodec.of((p,b)->{b.writeUuid(p.body);b.writeVarInt(p.epoch);b.writeBoolean(p.active);b.writeIdentifier(p.dimension);},b->new State(b.readUuid(),b.readVarInt(),b.readBoolean(),b.readIdentifier()));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    /** Generic-only capability state; the legacy four-field Sky packet stays unchanged. */
    public record CoreState(UUID body,int epoch,boolean active,Identifier dimension,int fireMask,boolean turret) implements CustomPayload {
        public CoreState {
            new ControlStateRules.Update(body,epoch,active,dimension.toString(),ControlStateRules.Kind.CORE,fireMask,turret);
        }
        public static final Id<CoreState> ID=new Id<>(Identifier.of("drmd","body_control_state"));
        public static final PacketCodec<RegistryByteBuf,CoreState> CODEC=PacketCodec.of((p,b)->{b.writeUuid(p.body);b.writeVarInt(p.epoch);b.writeBoolean(p.active);b.writeIdentifier(p.dimension);b.writeVarInt(p.fireMask);b.writeBoolean(p.turret);},b->new CoreState(b.readUuid(),b.readVarInt(),b.readBoolean(),b.readIdentifier(),b.readVarInt(),b.readBoolean()));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    private static final class Lease {
        final WeakReference<ServerPlayerEntity> pilot;final WeakReference<BlockBodyEntity> body;final BodyControlTarget target;final boolean previousAuto;final int epoch;
        int turretSequence=-1,turretCount;long turretTick=Long.MIN_VALUE,turretBudget=Long.MIN_VALUE;float turretYaw;
        int sequence=-1,count,fireSequence=-1,fireMask,fireCount;long fireTick=Long.MIN_VALUE,fireBudget=Long.MIN_VALUE,firedTick=Long.MIN_VALUE;long received,budgetTick=Long.MIN_VALUE,drivenTick=Long.MIN_VALUE;Input input;
        Lease(ServerPlayerEntity p,BlockBodyEntity b,BodyControlTarget t,int e){pilot=new WeakReference<>(p);body=new WeakReference<>(b);target=t;previousAuto=t.autopilot(b);epoch=e;received=p.getServer().getTicks();}
    }
    private static final Map<UUID,Lease> PILOTS=new HashMap<>();private static int epoch;
    private UfoControlSystem(){}
    public static void register() {
        PayloadTypeRegistry.playC2S().register(TurretInput.ID,TurretInput.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TurretInput.ID,(p,c)->c.server().execute(()->receiveTurret(c.player(),p)));
        PayloadTypeRegistry.playC2S().register(Fire.ID,Fire.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Fire.ID,(p,c)->c.server().execute(()->receiveFire(c.player(),p)));
        PayloadTypeRegistry.playC2S().register(Input.ID,Input.CODEC);PayloadTypeRegistry.playS2C().register(State.ID,State.CODEC);
        PayloadTypeRegistry.playS2C().register(CoreState.ID,CoreState.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Input.ID,(p,c)->c.server().execute(()->receive(c.player(),p)));
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->release(h.player));
        ServerTickEvents.END_SERVER_TICK.register(s->{for(var id:List.copyOf(PILOTS.keySet())){var l=PILOTS.get(id);if(l==null)continue;var p=l.pilot.get();var b=l.body.get();if(p==null||b==null||!valid(p,b,l.target)||s.getTicks()-l.received>60)drop(id,l);else if(!l.target.scheduledWeapons())fireBody(b);}});
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{for(var id:List.copyOf(PILOTS.keySet()))drop(id,PILOTS.get(id));});
    }
    private static boolean valid(ServerPlayerEntity p,BlockBodyEntity b,BodyControlTarget target) {
        return !p.isRemoved()&&p.isAlive()&&!p.isSpectator()&&!p.hasVehicle()&&!DescentPlayerData.get(p).isEnabled()
            &&p.getWorld()==b.getWorld()&&!b.isRemoved()&&target.valid(b)&&target.contains(b,p.getPos());
    }
    public static boolean claim(ServerPlayerEntity p,SkyUfoEntity b) {
        return claimTarget(p,b,BodyControlTarget.sky());
    }
    public static boolean claimCore(ServerPlayerEntity p,BlockBodyEntity b,BlockPos core) {
        return claimTarget(p,b,BodyControlTarget.core(core));
    }
    private static boolean claimTarget(ServerPlayerEntity p,BlockBodyEntity b,BodyControlTarget target) {
        if(!p.getServer().isOnThread()||!valid(p,b,target)||!target.canClaim(p,b))return false;
        var old=PILOTS.get(p.getUuid());if(old!=null)return old.pilot.get()==p&&old.body.get()==b&&old.target.sameStation(target)&&valid(p,b,old.target);
        for(var l:PILOTS.values())if(l.body.get()==b)return false;
        int next=++epoch;target=target.bind(p,b,next);
        var l=new Lease(p,b,target,next);PILOTS.put(p.getUuid(),l);com.terminaldetector.drmd.inventory.DroneArsenal.clear(p.getUuid());target.autopilot(b,false);sync(p,b,l,true);return true;
    }
    public static boolean controls(ServerPlayerEntity p){var l=PILOTS.get(p.getUuid());return l!=null&&l.pilot.get()==p;}
    public static boolean hasPilot(SkyUfoEntity b){for(var l:PILOTS.values()){var p=l.pilot.get();if(l.body.get()==b&&p!=null&&valid(p,b,l.target))return true;}return false;}
    public static int epoch(ServerPlayerEntity p){var l=PILOTS.get(p.getUuid());return l==null?-1:l.epoch;}
    public static boolean release(ServerPlayerEntity p){var l=PILOTS.get(p.getUuid());if(l==null||l.pilot.get()!=p)return false;drop(p.getUuid(),l);return true;}
    public static void releaseBody(SkyUfoEntity b){releaseBlockBody(b);}
    public static void releaseBlockBody(BlockBodyEntity b){if(b.getWorld().isClient)return;for(var id:List.copyOf(PILOTS.keySet())){var l=PILOTS.get(id);if(l!=null&&l.body.get()==b)drop(id,l);}}
    private static void drop(UUID id,Lease l){PILOTS.remove(id);var b=l.body.get();var p=l.pilot.get();if(b!=null){l.target.aim(b,0);l.target.autopilot(b,l.previousAuto);l.target.release(b,l.epoch);if(p!=null&&!p.isRemoved())sync(p,b,l,false);}}
    public static boolean savedAutopilot(SkyUfoEntity b,boolean fallback){if(b.getWorld().isClient)return fallback;for(var l:PILOTS.values())if(l.body.get()==b)return l.previousAuto;return fallback;}
    private static void sync(ServerPlayerEntity p,BlockBodyEntity b,Lease l,boolean active){
        // The lease retains its kind even after core removal/target invalidation.
        if(l.target.scheduledWeapons()){
            if(ServerPlayNetworking.canSend(p,CoreState.ID))ServerPlayNetworking.send(p,
                new CoreState(b.getUuid(),l.epoch,active,p.getWorld().getRegistryKey().getValue(),l.target.allowedFireMask(),l.target.supportsAim()));
        }else if(ServerPlayNetworking.canSend(p,State.ID))ServerPlayNetworking.send(p,new State(b.getUuid(),l.epoch,active,p.getWorld().getRegistryKey().getValue()));
    }
    public static boolean receive(ServerPlayerEntity p,Input input) {
        // This is a latest-input snapshot, never a queued simulation step. Allow sequence
        // gaps after a paused/lagged integrated server; epoch, replay, per-tick admission
        // and the single physical drive step still bound authority and work.
        var l=PILOTS.get(p.getUuid());if(l==null||l.pilot.get()!=p||l.body.get()==null||!p.getServer().isOnThread()||!valid(p,l.body.get(),l.target)||input.epoch!=l.epoch||input.sequence<0||input.sequence<=l.sequence)return false;
        for(float v:new float[]{input.forward,input.strafe,input.vertical,input.pitch,input.yaw,input.roll})if(!Float.isFinite(v)||Math.abs(v)>1)return false;
        long now=p.getServer().getTicks();if(l.budgetTick!=now){l.budgetTick=now;l.count=0;}if(l.count>=4)return false;l.count++;
        l.sequence=input.sequence;l.input=input;l.received=now;return true;
    }
    public static boolean controls(ServerPlayerEntity p,SkyUfoEntity b){return controlsBody(p,b);}
    public static boolean controlsBody(ServerPlayerEntity p,BlockBodyEntity b){var l=PILOTS.get(p.getUuid());return l!=null&&l.pilot.get()==p&&l.body.get()==b&&valid(p,b,l.target);}
    public static boolean receiveFire(ServerPlayerEntity p,Fire input) {
        var l=PILOTS.get(p.getUuid());if(l==null||!p.getServer().isOnThread()||l.body.get()==null||!controlsBody(p,l.body.get())||!l.target.supportsFire()||input.epoch!=l.epoch||input.sequence<0||input.sequence<=l.fireSequence||input.mask<0||(input.mask&~l.target.allowedFireMask())!=0)return false;
        long now=p.getServer().getTicks();if(l.fireBudget!=now){l.fireBudget=now;l.fireCount=0;}if(l.fireCount>=4)return false;l.fireCount++;
        l.fireSequence=input.sequence;l.fireMask=input.mask;l.fireTick=now;return true;
    }
    public static boolean receiveTurret(ServerPlayerEntity p,TurretInput input) {
        var l=PILOTS.get(p.getUuid());if(l==null||!p.getServer().isOnThread()||l.body.get()==null||!controlsBody(p,l.body.get())||!l.target.supportsAim()||input.epoch!=l.epoch||input.sequence<0||input.sequence<=l.turretSequence||!Float.isFinite(input.yaw)||Math.abs(input.yaw)>1)return false;
        long now=p.getServer().getTicks();if(l.turretBudget!=now){l.turretBudget=now;l.turretCount=0;}if(l.turretCount>=4)return false;l.turretCount++;
        l.turretSequence=input.sequence;l.turretYaw=input.yaw;l.turretTick=now;return true;
    }
    /** Launch after the hull has integrated: a newly ticked projectile starts at the same
     * material phase used as the next tick's previous collision pose. */
    public static void fire(SkyUfoEntity b) {
        fireBody(b);
    }
    public static void fireBody(BlockBodyEntity b) {
        if(b.getWorld().isClient)return;
        for(var l:PILOTS.values())if(l.body.get()==b){
            var p=l.pilot.get();if(p==null||!valid(p,b,l.target)||l.target.scheduledWeapons())return;long tick=p.getServer().getTicks();
            if(l.firedTick==tick||l.drivenTick!=tick)return;l.firedTick=tick;
            if(l.fireTick!=Long.MIN_VALUE&&tick-l.fireTick<=4)l.target.fire(b,p,l.fireMask);return;
        }
    }
    public static boolean drive(SkyUfoEntity b) {
        return driveBody(b);
    }
    public static boolean driveBody(BlockBodyEntity b) {
        if(b.getWorld().isClient)return false;
        if(b.controlCore().bound()&&!(b instanceof SkyUfoEntity))return b.controlCore().drive(b);
        for(var entry:List.copyOf(PILOTS.entrySet()))if(entry.getValue().body.get()==b)
            return driveLease(entry.getKey(),entry.getValue(),b);
        // Saved joint targets are not a persisted pilot lease. An unowned hull brakes
        // the module through its motor instead of replaying the previous connection.
        if(b instanceof SkyUfoEntity sky)sky.aimTurret(0);return false;
    }
    public static boolean driveCore(ServerPlayerEntity p,BlockBodyEntity b,int expectedEpoch) {
        if(b.getWorld().isClient||!p.getServer().isOnThread())return false;
        var l=PILOTS.get(p.getUuid());
        return l!=null&&l.pilot.get()==p&&l.body.get()==b&&l.epoch==expectedEpoch
                &&l.target.scheduledWeapons()&&driveLease(p.getUuid(),l,b);
    }
    private static boolean driveLease(UUID id,Lease l,BlockBodyEntity b) {
        var p=l.pilot.get();if(p==null||!valid(p,b,l.target)){drop(id,l);return false;}
        long tick=p.getServer().getTicks();if(l.drivenTick==tick)return true;l.drivenTick=tick;
        l.target.aim(b,l.turretTick!=Long.MIN_VALUE&&tick-l.turretTick<=4?l.turretYaw:0);
        var in=l.input;if(in!=null&&tick-l.received<=4){
            var thrust=new Vec3(-in.strafe,in.vertical,in.forward);if(thrust.length()>1)thrust=thrust.normalized();
            var force=b.rotation().rotate(thrust).scaled(b.physicsMass()*5);
            var turn=new Vec3(in.pitch,in.yaw,in.roll);if(turn.length()>1)turn=turn.normalized();
            var torque=b.physics().worldInertia().transform(b.rotation().rotate(turn));l.target.requestWrench(b,force,torque);
        }
        return true;
    }
    /** Read-only bridge into the existing post-contact mounted-weapon scheduler.
     * No emission, timer change, input consumption or pilot-map scan occurs here. */
    public static OptionalInt scheduledFireMask(ServerPlayerEntity p,BlockBodyEntity b,int expectedEpoch) {
        if(b.getWorld().isClient||!p.getServer().isOnThread())return OptionalInt.empty();
        var l=PILOTS.get(p.getUuid());
        if(l==null||l.pilot.get()!=p||l.body.get()!=b||l.epoch!=expectedEpoch
                ||!l.target.scheduledWeapons()||!valid(p,b,l.target))return OptionalInt.empty();
        long tick=p.getServer().getTicks();
        return OptionalInt.of(l.drivenTick==tick&&l.fireTick!=Long.MIN_VALUE&&tick-l.fireTick<=4?l.fireMask:0);
    }
}
