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

	public record InputPayload(float forward, float strafe, float vertical, float roll,
							   boolean dash, boolean hook,
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
						buf.readBoolean(), buf.readBoolean(),
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
							  boolean construction, float vx, float vy, float vz
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
					buf.writeBoolean(payload.construction);
					buf.writeFloat(payload.vx);
					buf.writeFloat(payload.vy);
					buf.writeFloat(payload.vz);
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
						buf.readBoolean(),
						buf.readFloat(),
						buf.readFloat(),
						buf.readFloat()
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

		ServerPlayNetworking.registerGlobalReceiver(InputPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			FlightSystem.InputState in = FlightSystem.input(player);
			in.forward = payload.forward();
			in.strafe = payload.strafe();
			in.vertical = payload.vertical();
			in.roll = payload.roll();
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
				case "enable" -> FlightSystem.enable(player);
				case "disable" -> FlightSystem.disable(player, data);
				case "repair_flight" -> FlightSystem.repair(player);
				case "weapon_use" -> {
					com.terminaldetector.drmd.weapon.items.DescentWeaponItem.tryUseChannel(player, false);
					return; // high-frequency — skip full HUD sync
				}
				case "weapon_alt" -> {
					com.terminaldetector.drmd.weapon.items.DescentWeaponItem.tryUseChannel(player, true);
					return;
				}
				case "dash" -> FlightSystem.tryDash(player);
				case "alwaysrun" -> data.setAlwaysRun(!data.isAlwaysRun());
				case "flightassist" -> data.setFlightAssist(!data.isFlightAssist());
				case "radar" -> data.setRadarEnabled(!data.isRadarEnabled());
				case "rocket_next" -> data.setRocketSubmode(data.getRocketSubmode() + 1);
				case "reset_roll" -> {
					data.levelShipAttitude(player);
					data.setRoll(0);
					data.setRollVel(0);
				}
				case "preset_balanced" -> EnergySystem.setPreset(data, EnergyPreset.BALANCED);
				case "preset_assault" -> EnergySystem.setPreset(data, EnergyPreset.ASSAULT);
				case "preset_interceptor" -> EnergySystem.setPreset(data, EnergyPreset.INTERCEPTOR);
				case "preset_siege" -> EnergySystem.setPreset(data, EnergyPreset.SIEGE);
				case "energy_cycle" -> {
					EnergyPreset[] presets = EnergyPreset.values();
					int idx = 0;
					for (int i = 0; i < presets.length; i++) {
						if (presets[i] == data.getPreset()) {
							idx = (i + 1) % presets.length;
							break;
						}
					}
					EnergySystem.setPreset(data, presets[idx]);
					player.sendMessage(net.minecraft.text.Text.literal("§eEnergy: §f" + data.getPreset().id), false);
				}
				case "construct" -> com.terminaldetector.drmd.world.build.ConstructionMode.toggle(player);
				case "level_lift" -> {
					// Cycle inside the live buildable column (−64…320), not speculative WorldLevels Y.
					int bot = player.getWorld().getBottomY();
					int top = bot + player.getWorld().getHeight() - 1;
					int[] stops = {
							Math.max(bot + 12, -40),
							64,
							Math.min(top - 16, 180),
							Math.min(top - 8, 280)
					};
					double y = player.getY();
					int next = stops[0];
					for (int i = 0; i < stops.length; i++) {
						if (y < stops[i] - 2) {
							next = stops[i];
							break;
						}
						next = stops[(i + 1) % stops.length];
					}
					player.requestTeleport(player.getX(), next, player.getZ());
					FlightSystem.enable(player);
					player.sendMessage(net.minecraft.text.Text.literal(
							"§bAltitude → §fY " + next), false);
				}
				case "reactor_start" ->
						com.terminaldetector.drmd.world.base.ReactorRoomStarter.activate(player);
				case "starter_kit" -> {
					FlightSystem.enable(player);
					data.setEnergy(100);
					data.setShield(100);
					com.terminaldetector.drmd.world.base.ReactorRoomStarter.giveKit(player);
					player.sendMessage(net.minecraft.text.Text.literal("§aStarter kit issued"), false);
				}
				default -> {}
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
		var flightVel = data.getFlightVelocity();
		float speed = (float) flightVel.length();
		var up = com.terminaldetector.drmd.world.LocalOrientation.getUp(player.getUuid());
		boolean foot = !data.isEnabled()
				&& com.terminaldetector.drmd.world.gravity.FootGravitySystem.isActive(player);
		if (foot) {
			up = com.terminaldetector.drmd.world.gravity.FootGravitySystem.getUp(player);
		} else if (data.isEnabled()) {
			up = new net.minecraft.util.math.Vec3d(0, 1, 0);
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
				com.terminaldetector.drmd.world.build.ConstructionMode.isActive(player.getUuid()),
				(float) flightVel.x, (float) flightVel.y, (float) flightVel.z
		));
	}

	public static void syncLlod(ServerPlayerEntity player) {
		DescentPlayerData data = DescentPlayerData.get(player);
		net.minecraft.util.math.Vec3d vel = data.isEnabled() ? data.getFlightVelocity() : player.getVelocity();
		// Seeded ghost macros so high-speed / unloaded chunks still feed Voxel LLOD
		com.terminaldetector.drmd.world.gen2.MacroCatalogue.ensureAround(
				player.getServerWorld(), player.getBlockPos(), vel);
		var silhouettes = com.terminaldetector.drmd.world.llod.LlodRegistry.queryVisible(
				player.getBlockPos(), vel, 96);
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
