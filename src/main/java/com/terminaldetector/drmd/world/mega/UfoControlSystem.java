package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
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
    public record State(UUID body,int epoch,boolean active,Identifier dimension) implements CustomPayload {
        public static final Id<State> ID=new Id<>(Identifier.of("drmd","ufo_control_state"));
        public static final PacketCodec<RegistryByteBuf,State> CODEC=PacketCodec.of((p,b)->{b.writeUuid(p.body);b.writeVarInt(p.epoch);b.writeBoolean(p.active);b.writeIdentifier(p.dimension);},b->new State(b.readUuid(),b.readVarInt(),b.readBoolean(),b.readIdentifier()));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    private static final class Lease {
        final WeakReference<ServerPlayerEntity> pilot;final WeakReference<SkyUfoEntity> body;final boolean previousAuto;final int epoch;
        int sequence=-1,count;long received,budgetTick=Long.MIN_VALUE,drivenTick=Long.MIN_VALUE;Input input;
        Lease(ServerPlayerEntity p,SkyUfoEntity b,int e){pilot=new WeakReference<>(p);body=new WeakReference<>(b);previousAuto=b.autopilotEnabled();epoch=e;received=p.getServer().getTicks();}
    }
    private static final Map<UUID,Lease> PILOTS=new HashMap<>();private static int epoch;
    private UfoControlSystem(){}
    public static void register() {
        PayloadTypeRegistry.playC2S().register(Input.ID,Input.CODEC);PayloadTypeRegistry.playS2C().register(State.ID,State.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Input.ID,(p,c)->c.server().execute(()->receive(c.player(),p)));
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->release(h.player));
        ServerTickEvents.END_SERVER_TICK.register(s->{for(var id:List.copyOf(PILOTS.keySet())){var l=PILOTS.get(id);if(l==null)continue;var p=l.pilot.get();var b=l.body.get();if(p==null||b==null||!valid(p,b)||s.getTicks()-l.received>60)drop(id,l);}});
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{for(var id:List.copyOf(PILOTS.keySet()))drop(id,PILOTS.get(id));});
    }
    private static boolean valid(ServerPlayerEntity p,SkyUfoEntity b) {
        return !p.isRemoved()&&p.isAlive()&&!p.isSpectator()&&!p.hasVehicle()&&!DescentPlayerData.get(p).isEnabled()
            &&p.getWorld()==b.getWorld()&&!b.isRemoved()&&!b.isCrashing()&&b.containsPos(p.getPos())&&b.hasControlConsole();
    }
    public static boolean claim(ServerPlayerEntity p,SkyUfoEntity b) {
        if(!p.getServer().isOnThread()||!valid(p,b)||p.getEyePos().distanceTo(b.localToWorld(Vec3d.ofCenter(CONSOLE)))>p.getBlockInteractionRange())return false;
        var old=PILOTS.get(p.getUuid());if(old!=null)return old.body.get()==b;
        for(var l:PILOTS.values())if(l.body.get()==b)return false;
        var l=new Lease(p,b,++epoch);PILOTS.put(p.getUuid(),l);b.setAutopilot(false);sync(p,b,l,true);return true;
    }
    public static boolean controls(ServerPlayerEntity p){var l=PILOTS.get(p.getUuid());return l!=null&&l.pilot.get()==p;}
    public static int epoch(ServerPlayerEntity p){var l=PILOTS.get(p.getUuid());return l==null?-1:l.epoch;}
    public static boolean release(ServerPlayerEntity p){var l=PILOTS.get(p.getUuid());if(l==null||l.pilot.get()!=p)return false;drop(p.getUuid(),l);return true;}
    public static void releaseBody(SkyUfoEntity b){if(b.getWorld().isClient)return;for(var id:List.copyOf(PILOTS.keySet())){var l=PILOTS.get(id);if(l!=null&&l.body.get()==b)drop(id,l);}}
    private static void drop(UUID id,Lease l){PILOTS.remove(id);var b=l.body.get();var p=l.pilot.get();if(b!=null){b.setAutopilot(l.previousAuto);if(p!=null&&!p.isRemoved())sync(p,b,l,false);}}
    public static boolean savedAutopilot(SkyUfoEntity b,boolean fallback){if(b.getWorld().isClient)return fallback;for(var l:PILOTS.values())if(l.body.get()==b)return l.previousAuto;return fallback;}
    private static void sync(ServerPlayerEntity p,SkyUfoEntity b,Lease l,boolean active){if(ServerPlayNetworking.canSend(p,State.ID))ServerPlayNetworking.send(p,new State(b.getUuid(),l.epoch,active,p.getWorld().getRegistryKey().getValue()));}
    public static boolean receive(ServerPlayerEntity p,Input input) {
        // This is a latest-input snapshot, never a queued simulation step. Allow sequence
        // gaps after a paused/lagged integrated server; epoch, replay, per-tick admission
        // and the single physical drive step still bound authority and work.
        var l=PILOTS.get(p.getUuid());if(l==null||l.pilot.get()!=p||l.body.get()==null||!p.getServer().isOnThread()||!valid(p,l.body.get())||input.epoch!=l.epoch||input.sequence<0||input.sequence<=l.sequence)return false;
        for(float v:new float[]{input.forward,input.strafe,input.vertical,input.pitch,input.yaw,input.roll})if(!Float.isFinite(v)||Math.abs(v)>1)return false;
        long now=p.getServer().getTicks();if(l.budgetTick!=now){l.budgetTick=now;l.count=0;}if(l.count>=4)return false;l.count++;
        l.sequence=input.sequence;l.input=input;l.received=now;return true;
    }
    public static boolean drive(SkyUfoEntity b) {
        if(b.getWorld().isClient)return false;
        for(var entry:List.copyOf(PILOTS.entrySet())){var l=entry.getValue();if(l.body.get()!=b)continue;var p=l.pilot.get();if(p==null||!valid(p,b)){drop(entry.getKey(),l);return false;}
            long tick=p.getServer().getTicks();if(l.drivenTick==tick)return true;l.drivenTick=tick;
            var in=l.input;if(in!=null&&tick-l.received<=4){
                var thrust=new Vec3(-in.strafe,in.vertical,in.forward);if(thrust.length()>1)thrust=thrust.normalized();
                var impulse=b.rotation().rotate(thrust).scaled(b.physicsMass()*5*.05);b.applyPhysicsImpulse(b.physicsCentre(),new Vec3d(impulse.x(),impulse.y(),impulse.z()));
                var turn=new Vec3(in.pitch,in.yaw,in.roll);if(turn.length()>1)turn=turn.normalized();
                var torque=b.physics().worldInertia().transform(b.rotation().rotate(turn));b.applyPhysicsTorque(new Vec3d(torque.x(),torque.y(),torque.z()));
            }return true;
        }return false;
    }
}
