package com.terminaldetector.drmd.network;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.energy.EnergyPreset;
import com.terminaldetector.drmd.energy.EnergySystem;
import com.terminaldetector.drmd.flight.FlightSystem;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class ModNetworking {
	public static final Identifier INPUT_ID = Identifier.of(DescentMod.MOD_ID, "input");
	public static final Identifier SYNC_ID = Identifier.of(DescentMod.MOD_ID, "sync");
	public static final Identifier ACTION_ID = Identifier.of(DescentMod.MOD_ID, "action");

	public record InputPayload(float forward, float strafe, float vertical, float roll,
							   boolean dash, boolean hook) implements CustomPayload {
		public static final Id<InputPayload> ID = new Id<>(INPUT_ID);
		public static final PacketCodec<RegistryByteBuf, InputPayload> CODEC = PacketCodec.tuple(
				PacketCodecs.FLOAT, InputPayload::forward,
				PacketCodecs.FLOAT, InputPayload::strafe,
				PacketCodecs.FLOAT, InputPayload::vertical,
				PacketCodecs.FLOAT, InputPayload::roll,
				PacketCodecs.BOOL, InputPayload::dash,
				PacketCodecs.BOOL, InputPayload::hook,
				InputPayload::new
		);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	public record SyncPayload(boolean enabled, float energy, float energyMax, float shield, float shieldMax,
							  float roll, float speed, int rocketSub, String preset, float gravy,
							  float dashCd, float gravityFactor, boolean alwaysRun, boolean flightAssist,
							  boolean radar) implements CustomPayload {
		public static final Id<SyncPayload> ID = new Id<>(SYNC_ID);
		public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = PacketCodec.of(
				(payload, buf) -> {
					buf.writeBoolean(payload.enabled);
					buf.writeFloat(payload.energy);
					buf.writeFloat(payload.energyMax);
					buf.writeFloat(payload.shield);
					buf.writeFloat(payload.shieldMax);
					buf.writeFloat(payload.roll);
					buf.writeFloat(payload.speed);
					buf.writeVarInt(payload.rocketSub);
					buf.writeString(payload.preset);
					buf.writeFloat(payload.gravy);
					buf.writeFloat(payload.dashCd);
					buf.writeFloat(payload.gravityFactor);
					buf.writeBoolean(payload.alwaysRun);
					buf.writeBoolean(payload.flightAssist);
					buf.writeBoolean(payload.radar);
				},
				buf -> new SyncPayload(
						buf.readBoolean(),
						buf.readFloat(),
						buf.readFloat(),
						buf.readFloat(),
						buf.readFloat(),
						buf.readFloat(),
						buf.readFloat(),
						buf.readVarInt(),
						buf.readString(),
						buf.readFloat(),
						buf.readFloat(),
						buf.readFloat(),
						buf.readBoolean(),
						buf.readBoolean(),
						buf.readBoolean()
				)
		);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	public record ActionPayload(String action) implements CustomPayload {
		public static final Id<ActionPayload> ID = new Id<>(ACTION_ID);
		public static final PacketCodec<RegistryByteBuf, ActionPayload> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, ActionPayload::action, ActionPayload::new
		);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	private ModNetworking() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(InputPayload.ID, InputPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(InputPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			FlightSystem.InputState in = FlightSystem.input(player);
			in.forward = payload.forward();
			in.strafe = payload.strafe();
			in.vertical = payload.vertical();
			in.roll = payload.roll();
			if (payload.dash()) in.dash = true;
			if (payload.hook()) FlightSystem.toggleHook(player);
		});

		ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			DescentPlayerData data = DescentPlayerData.get(player);
			switch (payload.action()) {
				case "toggle" -> FlightSystem.toggle(player);
				case "dash" -> FlightSystem.tryDash(player);
				case "alwaysrun" -> data.setAlwaysRun(!data.isAlwaysRun());
				case "flightassist" -> data.setFlightAssist(!data.isFlightAssist());
				case "radar" -> data.setRadarEnabled(!data.isRadarEnabled());
				case "rocket_next" -> data.setRocketSubmode(data.getRocketSubmode() + 1);
				case "reset_roll" -> { data.setRoll(0); data.setRollVel(0); }
				case "preset_balanced" -> EnergySystem.setPreset(data, EnergyPreset.BALANCED);
				case "preset_assault" -> EnergySystem.setPreset(data, EnergyPreset.ASSAULT);
				case "preset_interceptor" -> EnergySystem.setPreset(data, EnergyPreset.INTERCEPTOR);
				case "preset_siege" -> EnergySystem.setPreset(data, EnergyPreset.SIEGE);
				default -> {}
			}
			syncPlayer(player, data);
		});
	}

	public static void syncPlayer(ServerPlayerEntity player, DescentPlayerData data) {
		float speed = (float) data.getFlightVelocity().length();
		ServerPlayNetworking.send(player, new SyncPayload(
				data.isEnabled(),
				data.getEnergy(), data.getEnergyMax(),
				data.getShield(), data.getShieldMax(),
				data.getRoll(), speed,
				data.getRocketSubmode(),
				data.getPreset().id,
				data.getGravyEnergy(),
				data.getDashCooldown(),
				data.getGravityFactor(),
				data.isAlwaysRun(),
				data.isFlightAssist(),
				data.isRadarEnabled()
		));
	}
}
