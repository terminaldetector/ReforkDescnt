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
	public static final Identifier LLOD_ID = Identifier.of(DescentMod.MOD_ID, "llod");
	public static final Identifier PLANET_MAP_ID = Identifier.of(DescentMod.MOD_ID, "planet_map");

	public record InputPayload(float forward, float strafe, float vertical, float roll,
							   boolean dash, boolean hook, boolean afterburner,
							   boolean attitude, float fx, float fy, float fz,
							   float ux, float uy, float uz) implements CustomPayload {
		public static final Id<InputPayload> ID = new Id<>(INPUT_ID);
		public static final PacketCodec<RegistryByteBuf, InputPayload> CODEC = PacketCodec.of(
				(payload, buf) -> {
					buf.writeFloat(payload.forward);
					buf.writeFloat(payload.strafe);
					buf.writeFloat(payload.vertical);
					buf.writeFloat(payload.roll);
					buf.writeBoolean(payload.dash);
					buf.writeBoolean(payload.hook);
					buf.writeBoolean(payload.afterburner);
					buf.writeBoolean(payload.attitude);
					buf.writeFloat(payload.fx);
					buf.writeFloat(payload.fy);
					buf.writeFloat(payload.fz);
					buf.writeFloat(payload.ux);
					buf.writeFloat(payload.uy);
					buf.writeFloat(payload.uz);
				},
				buf -> new InputPayload(
						buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
						buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
						buf.readBoolean(),
						buf.readFloat(), buf.readFloat(), buf.readFloat(),
						buf.readFloat(), buf.readFloat(), buf.readFloat()
				)
		);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	public record SyncPayload(boolean enabled, float energy, float energyMax, float shield, float shieldMax,
							  float roll, float speed, int rocketSub, String preset, float gravy,
							  float dashCd, float gravityFactor, boolean alwaysRun, boolean flightAssist,
							  boolean radar, boolean footGravity, float localUx, float localUy, float localUz,
							  float velX, float velY, float velZ,
							  float accel, float drag, float maxSpeed, float allocEngines,
							  int afterburnerTier
	) implements CustomPayload {
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
					buf.writeBoolean(payload.footGravity);
					buf.writeFloat(payload.localUx);
					buf.writeFloat(payload.localUy);
					buf.writeFloat(payload.localUz);
					buf.writeFloat(payload.velX);
					buf.writeFloat(payload.velY);
					buf.writeFloat(payload.velZ);
					buf.writeFloat(payload.accel);
					buf.writeFloat(payload.drag);
					buf.writeFloat(payload.maxSpeed);
					buf.writeFloat(payload.allocEngines);
					buf.writeVarInt(payload.afterburnerTier);
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
						buf.readBoolean(),
						buf.readBoolean(),
						buf.readFloat(),
						buf.readFloat(),
						buf.readFloat(),
						buf.readFloat(),
						buf.readFloat(),
						buf.readFloat(),
						buf.readFloat(),
						buf.readFloat(),
						buf.readFloat(),
						buf.readFloat(),
						buf.readVarInt()
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

	/** Server → client Voxel LLOD catalogue (compact; client expands LLOD0/1/2 meshes). */
	public record LlodPayload(java.util.List<LlodEntry> entries) implements CustomPayload {
		public record LlodEntry(java.util.UUID id, String kind, double x, double y, double z,
								float rx, float ry, float rz, String level, int color, String label, long seed) {}

		public static final Id<LlodPayload> ID = new Id<>(LLOD_ID);
		public static final PacketCodec<RegistryByteBuf, LlodPayload> CODEC = PacketCodec.of(
				(payload, buf) -> {
					buf.writeVarInt(payload.entries.size());
					for (LlodEntry e : payload.entries) {
						buf.writeUuid(e.id);
						buf.writeString(e.kind);
						buf.writeDouble(e.x);
						buf.writeDouble(e.y);
						buf.writeDouble(e.z);
						buf.writeFloat(e.rx);
						buf.writeFloat(e.ry);
						buf.writeFloat(e.rz);
						buf.writeString(e.level);
						buf.writeInt(e.color);
						buf.writeString(e.label);
						buf.writeLong(e.seed);
					}
				},
				buf -> {
					int n = buf.readVarInt();
					java.util.ArrayList<LlodEntry> list = new java.util.ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						list.add(new LlodEntry(
								buf.readUuid(),
								buf.readString(),
								buf.readDouble(), buf.readDouble(), buf.readDouble(),
								buf.readFloat(), buf.readFloat(), buf.readFloat(),
								buf.readString(),
								buf.readInt(),
								buf.readString(),
								buf.readLong()
						));
					}
					return new LlodPayload(list);
				}
		);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	/** Server → client planetary map viewport (explored + procedural fog-of-war cells). */
	public record PlanetMapPayload(int originCx, int originCz, long seed, java.util.List<Cell> cells) implements CustomPayload {
		public record Cell(int cx, int cz, int height, int tint, int flags) {}

		public static final Id<PlanetMapPayload> ID = new Id<>(PLANET_MAP_ID);
		public static final PacketCodec<RegistryByteBuf, PlanetMapPayload> CODEC = PacketCodec.of(
				(payload, buf) -> {
					buf.writeVarInt(payload.originCx);
					buf.writeVarInt(payload.originCz);
					buf.writeLong(payload.seed);
					buf.writeVarInt(payload.cells.size());
					for (Cell c : payload.cells) {
						buf.writeVarInt(c.cx);
						buf.writeVarInt(c.cz);
						buf.writeByte(c.height);
						buf.writeInt(c.tint);
						buf.writeByte(c.flags);
					}
				},
				buf -> {
					int ox = buf.readVarInt();
					int oz = buf.readVarInt();
					long seed = buf.readLong();
					int n = buf.readVarInt();
					java.util.ArrayList<Cell> list = new java.util.ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						list.add(new Cell(buf.readVarInt(), buf.readVarInt(),
								buf.readUnsignedByte(), buf.readInt(), buf.readUnsignedByte()));
					}
					return new PlanetMapPayload(ox, oz, seed, list);
				}
		);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	/** Client → server construction apply (empty modules list = clear override). */
	public record ConstructionPayload(String weaponId, net.minecraft.nbt.NbtList modules) implements CustomPayload {
		public static final Identifier CONSTRUCTION_ID = Identifier.of(DescentMod.MOD_ID, "construction");
		public static final Id<ConstructionPayload> ID = new Id<>(CONSTRUCTION_ID);
		public static final PacketCodec<RegistryByteBuf, ConstructionPayload> CODEC = PacketCodec.of(
				(payload, buf) -> {
					buf.writeString(payload.weaponId);
					buf.writeNbt(payload.modules);
				},
				buf -> {
					String id = buf.readString();
					net.minecraft.nbt.NbtElement el = buf.readNbt();
					net.minecraft.nbt.NbtList list = el instanceof net.minecraft.nbt.NbtList l ? l : new net.minecraft.nbt.NbtList();
					return new ConstructionPayload(id, list);
				}
		);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	private ModNetworking() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(InputPayload.ID, InputPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ConstructionPayload.ID, ConstructionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ConstructionPayload.ID, ConstructionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(LlodPayload.ID, LlodPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PlanetMapPayload.ID, PlanetMapPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(InputPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			FlightSystem.InputState in = FlightSystem.input(player);
			in.forward = payload.forward();
			in.strafe = payload.strafe();
			in.vertical = payload.vertical();
			in.roll = payload.roll();
			in.afterburner = payload.afterburner();
			if (payload.dash()) in.dash = true;
			if (payload.hook()) FlightSystem.toggleHook(player);
			if (payload.attitude()) {
				DescentPlayerData data = DescentPlayerData.get(player);
				data.setShipAttitude(
						payload.fx(), payload.fy(), payload.fz(),
						payload.ux(), payload.uy(), payload.uz());
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			DescentPlayerData data = DescentPlayerData.get(player);
			switch (payload.action()) {
				case "toggle" -> FlightSystem.toggle(player);
				case "dash" -> FlightSystem.tryDash(player);
				// Legacy toggle kept for /d6 alwaysrun; hold-R is the Descent path via InputPayload.
				case "alwaysrun" -> data.setAlwaysRun(!data.isAlwaysRun());
				case "flightassist" -> data.setFlightAssist(!data.isFlightAssist());
				case "radar" -> data.setRadarEnabled(!data.isRadarEnabled());
				case "rocket_next" -> data.setRocketSubmode(data.getRocketSubmode() + 1);
				case "reset_roll" -> {
					data.levelShipAttitude(player);
					data.setRoll(0);
					data.setRollVel(0);
				}
				// Creative helpers from the options screen. Gated here rather than on the client:
				// the sender decides nothing, the server does.
				case "creative_ship" -> {
					if (player.isCreative()) {
						player.giveItemStack(new net.minecraft.item.ItemStack(
								com.terminaldetector.drmd.weapon.items.ModItems.PYRO_GX));
					}
				}
				case "creative_kit" -> {
					if (player.isCreative()) {
						player.giveItemStack(new net.minecraft.item.ItemStack(
								com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH, 16));
						player.giveItemStack(new net.minecraft.item.ItemStack(
								com.terminaldetector.drmd.entity.ModWorldBlocks.DRILL_RIG, 4));
					}
				}
				case "preset_balanced" -> EnergySystem.setPreset(data, EnergyPreset.BALANCED);
				case "preset_assault" -> EnergySystem.setPreset(data, EnergyPreset.ASSAULT);
				case "preset_interceptor" -> EnergySystem.setPreset(data, EnergyPreset.INTERCEPTOR);
				case "preset_siege" -> EnergySystem.setPreset(data, EnergyPreset.SIEGE);
				case "ab_1" -> data.setAfterburnerTier(1);
				case "ab_2" -> data.setAfterburnerTier(2);
				case "ab_3" -> data.setAfterburnerTier(3);
				case "ab_4" -> data.setAfterburnerTier(4);
				default -> {
					String a = payload.action();
					// Creative ship physics from ShipCustomizeScreen — server gates creative.
					if (player.isCreative() && a != null && a.contains(":")) {
						int colon = a.indexOf(':');
						String key = a.substring(0, colon);
						try {
							float val = Float.parseFloat(a.substring(colon + 1));
							switch (key) {
								case "accel" -> data.setAccel(net.minecraft.util.math.MathHelper.clamp(val, 500f, 12000f));
								case "drag" -> data.setDrag(net.minecraft.util.math.MathHelper.clamp(val, 0.1f, 8f));
								case "maxspeed" -> data.setMaxSpeed(net.minecraft.util.math.MathHelper.clamp(val, 400f, 6000f));
								default -> {}
							}
						} catch (NumberFormatException ignored) {}
					}
				}
			}
			syncPlayer(player, data);
		});

		ServerPlayNetworking.registerGlobalReceiver(ConstructionPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (!player.hasPermissionLevel(2) && !player.getServer().isSingleplayer()) {
				return; // only ops / SP can publish constructions
			}
			String id = com.terminaldetector.drmd.workshop.ConstructionRegistry.normalize(payload.weaponId());
			if (payload.modules() == null || payload.modules().isEmpty()) {
				com.terminaldetector.drmd.workshop.ConstructionRegistry.clearOverride(id);
			} else {
				var mods = com.terminaldetector.drmd.workshop.ClusterModule.listFromNbt(payload.modules());
				com.terminaldetector.drmd.workshop.ConstructionRegistry.setOverride(id, mods);
			}
			// Broadcast to all players so clients update FP view + muzzles
			for (ServerPlayerEntity p : player.getServer().getPlayerManager().getPlayerList()) {
				ServerPlayNetworking.send(p, payload);
			}
		});
	}

	public static void syncPlayer(ServerPlayerEntity player, DescentPlayerData data) {
		// Ship velocity in blocks/tick, ready for the client to move on.
		//
		// It rides this payload rather than vanilla's EntityVelocityUpdateS2CPacket for two reasons:
		// a player is never in its own entity tracker, so that packet reaches every client EXCEPT
		// the pilot's; and its wire format is a short scaled by 8000, capping at 4.09 blocks/tick,
		// which a dash overruns.
		net.minecraft.util.math.Vec3d shipVel = data.getFlightVelocity()
				.multiply(1.0 / com.terminaldetector.drmd.DescentMod.TICKS_PER_SECOND);
		float speed = (float) data.getFlightVelocity().length();
		var up = com.terminaldetector.drmd.world.LocalOrientation.getUp(player.getUuid());
		boolean foot = com.terminaldetector.drmd.world.gravity.FootGravitySystem.isActive(player.getUuid());
		if (foot) {
			up = com.terminaldetector.drmd.world.gravity.FootGravitySystem.getUp(player.getUuid());
		}
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
				data.isRadarEnabled(),
				foot,
				(float) up.x, (float) up.y, (float) up.z,
				(float) shipVel.x, (float) shipVel.y, (float) shipVel.z,
				data.getAccel(), data.getDrag(), data.getMaxSpeed(), data.getAllocEngines(),
				data.getAfterburnerTier()
		));
	}

	public static void syncLlod(ServerPlayerEntity player) {
		var silhouettes = com.terminaldetector.drmd.world.llod.LlodRegistry.queryVisible(player.getBlockPos(), 64);
		java.util.ArrayList<LlodPayload.LlodEntry> entries = new java.util.ArrayList<>(silhouettes.size());
		for (var s : silhouettes) {
			entries.add(new LlodPayload.LlodEntry(
					s.id(),
					s.kind().name(),
					s.center().x, s.center().y, s.center().z,
					s.radiusX(), s.radiusY(), s.radiusZ(),
					s.level().name(),
					s.colorRgb(),
					s.label(),
					s.seed()
			));
		}
		ServerPlayNetworking.send(player, new LlodPayload(entries));
	}
}
