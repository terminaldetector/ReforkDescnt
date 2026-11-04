package com.terminaldetector.drmd.network;

import com.terminaldetector.drmd.world.contraption.boarding.FlightBodySystem;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.List;

/** A bounded prediction clock only. Clients never supply authoritative flight velocity or pose. */
public final class FlightBodyNetworking {
    private FlightBodyNetworking(){}
    public record Input(int epoch,int sequence) implements CustomPayload {
        public static final Id<Input> ID=new Id<>(Identifier.of("drmd","flight_body_input"));
        public static final PacketCodec<RegistryByteBuf,Input> CODEC=PacketCodec.of((p,b)->{b.writeVarInt(p.epoch);b.writeVarInt(p.sequence);},b->new Input(b.readVarInt(),b.readVarInt()));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Geometry(int entity,long revision) {}
    public record State(int epoch,int acknowledgment,boolean active,double x,double y,double z,double vx,double vy,double vz,Identifier dimension,List<Geometry> geometry) implements CustomPayload {
        public State {geometry=List.copyOf(geometry);}
        public static final Id<State> ID=new Id<>(Identifier.of("drmd","flight_body_state"));
        public static final PacketCodec<RegistryByteBuf,State> CODEC=PacketCodec.of((p,b)->{
            b.writeVarInt(p.epoch);b.writeVarInt(p.acknowledgment);b.writeBoolean(p.active);
            b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeDouble(p.vx);b.writeDouble(p.vy);b.writeDouble(p.vz);b.writeIdentifier(p.dimension);
            b.writeCollection(p.geometry,(buf,g)->{buf.writeVarInt(g.entity);buf.writeVarLong(g.revision);});
        },b->new State(b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readIdentifier(),b.readList(buf->new Geometry(buf.readVarInt(),buf.readVarLong()))));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public static void register() {
        PayloadTypeRegistry.playC2S().register(Input.ID,Input.CODEC);PayloadTypeRegistry.playS2C().register(State.ID,State.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Input.ID,(p,c)->c.server().execute(()->FlightBodySystem.receive(c.player(),p)));
    }
}
