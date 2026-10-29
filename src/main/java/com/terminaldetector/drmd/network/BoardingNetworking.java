package com.terminaldetector.drmd.network;

import com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class BoardingNetworking {
    public static final int PROTOCOL=4;
    public static final int PREDICTION_HISTORY=32;
    private BoardingNetworking() {}
    public record Ready(int version) implements CustomPayload {
        public static final Id<Ready> ID=new Id<>(Identifier.of("drmd","boarding_ready"));
        public static final PacketCodec<RegistryByteBuf,Ready> CODEC=PacketCodec.of((p,b)->b.writeVarInt(p.version),b->new Ready(b.readVarInt()));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Input(int epoch,int sequence,float strafe,float forward,boolean jump,float lookX,float lookY,float lookZ) implements CustomPayload {
        public static final Id<Input> ID=new Id<>(Identifier.of("drmd","boarding_input"));
        public static final PacketCodec<RegistryByteBuf,Input> CODEC=PacketCodec.of((p,b)->{
            b.writeVarInt(p.epoch);b.writeVarInt(p.sequence);b.writeFloat(p.strafe);b.writeFloat(p.forward);b.writeBoolean(p.jump);
            b.writeFloat(p.lookX);b.writeFloat(p.lookY);b.writeFloat(p.lookZ);
        },b->new Input(b.readVarInt(),b.readVarInt(),b.readFloat(),b.readFloat(),b.readBoolean(),b.readFloat(),b.readFloat(),b.readFloat()));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record BodyState(double x,double y,double z,double qx,double qy,double qz,double qw,
                            double vx,double vy,double vz,double lx,double ly,double lz,double gx,double gy,double gz,boolean paused,long shapeRevision) {
        private void write(RegistryByteBuf b) {
            b.writeDouble(x);b.writeDouble(y);b.writeDouble(z);b.writeDouble(qx);b.writeDouble(qy);b.writeDouble(qz);b.writeDouble(qw);
            b.writeDouble(vx);b.writeDouble(vy);b.writeDouble(vz);b.writeDouble(lx);b.writeDouble(ly);b.writeDouble(lz);b.writeDouble(gx);b.writeDouble(gy);b.writeDouble(gz);b.writeBoolean(paused);b.writeVarLong(shapeRevision);
        }
        private static BodyState read(RegistryByteBuf b) {
            return new BodyState(b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),
                b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readBoolean(),b.readVarLong());
        }
    }
    public record PassengerPose(double x,double y,double z,double w,int gravityOwner) {
        public static final PassengerPose WORLD=new PassengerPose(0,0,0,1,-1);
        public com.terminaldetector.drmd.client.portal.PortalTransform.Quat frame(){return new com.terminaldetector.drmd.client.portal.PortalTransform.Quat(x,y,z,w).normalized();}
    }
    public record State(int epoch,int acknowledgment,boolean active,int support,double x,double y,double z,double vx,double vy,double vz,boolean grounded,PassengerPose pose,BodyState body,Identifier dimension) implements CustomPayload {
        public static final Id<State> ID=new Id<>(Identifier.of("drmd","boarding_state"));
        public static final PacketCodec<RegistryByteBuf,State> CODEC=PacketCodec.of((p,b)->{
            b.writeVarInt(p.epoch);b.writeVarInt(p.acknowledgment);b.writeBoolean(p.active);b.writeVarInt(p.support);
            b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeDouble(p.vx);b.writeDouble(p.vy);b.writeDouble(p.vz);b.writeBoolean(p.grounded);
            b.writeDouble(p.pose.x);b.writeDouble(p.pose.y);b.writeDouble(p.pose.z);b.writeDouble(p.pose.w);b.writeVarInt(p.pose.gravityOwner);
            b.writeBoolean(p.body!=null);if(p.body!=null)p.body.write(b);
            b.writeIdentifier(p.dimension);
        },b->new State(b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readVarInt(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readBoolean(),new PassengerPose(b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readVarInt()),b.readBoolean()?BodyState.read(b):null,b.readIdentifier()));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public static void register() {
        PayloadTypeRegistry.playC2S().register(Ready.ID,Ready.CODEC);
        PayloadTypeRegistry.playC2S().register(Input.ID,Input.CODEC);
        PayloadTypeRegistry.playS2C().register(State.ID,State.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Input.ID,(p,c)->c.server().execute(()->BoardingSystem.receive(c.player(),p)));
        ServerPlayNetworking.registerGlobalReceiver(Ready.ID,(p,c)->c.server().execute(()->BoardingSystem.ready(c.player(),p.version)));
    }
}
