package com.terminaldetector.drmd.network;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.energy.EnergyPreset;
import com.terminaldetector.drmd.energy.EnergySystem;
import com.terminaldetector.drmd.entity.PyroShipEntity;
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
	public static final Identifier PLANET_ID = Identifier.of(DescentMod.MOD_ID, "planet");
	public static final Identifier SURFACE_ID = Identifier.of(DescentMod.MOD_ID, "surface");

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

	/**
	 * Server → client planet snapshot: the world seed, the cells a reactor burned, and the
	 * catalogue of built landmarks the horizon stands up as silhouettes.
	 *
	 * <p>The surface itself follows from the seed, so this goes out once on join and again only when
	 * one of the other two changes. Its predecessor streamed a viewport of six hundred cells —
	 * height, tint and flags each — on every player tick.
	 *
	 * <p>Only what a silhouette needs is sent: where a thing is, how big it is, its colour and its
	 * kind. The shape is rebuilt on the client from the kind, because a mast and a dish cost two
	 * boxes to describe and kilobytes to transmit.
	 */
	public record PlanetPayload(long seed, java.util.List<Long> scarCells,
								java.util.List<Landmark> landmarks) implements CustomPayload {
		public record Landmark(int x, int y, int z, int sizeX, int sizeY, int sizeZ,
							   int colour, int kind) {}

		/** Cap so a heavily bombed world cannot turn one join into a megabyte. */
		public static final int MAX_SCARS = 2048;
		/** Cap on landmarks — well past what a horizon can distinguish. */
		public static final int MAX_LANDMARKS = 512;

		public static final Id<PlanetPayload> ID = new Id<>(PLANET_ID);
		public static final PacketCodec<RegistryByteBuf, PlanetPayload> CODEC = PacketCodec.of(
				(payload, buf) -> {
					buf.writeLong(payload.seed);
					int n = Math.min(payload.scarCells.size(), MAX_SCARS);
					buf.writeVarInt(n);
					for (int i = 0; i < n; i++) {
						buf.writeLong(payload.scarCells.get(i));
					}
					int m = Math.min(payload.landmarks.size(), MAX_LANDMARKS);
					buf.writeVarInt(m);
					for (int i = 0; i < m; i++) {
						Landmark l = payload.landmarks.get(i);
						buf.writeInt(l.x());
						buf.writeInt(l.y());
						buf.writeInt(l.z());
						buf.writeVarInt(l.sizeX());
						buf.writeVarInt(l.sizeY());
						buf.writeVarInt(l.sizeZ());
						buf.writeInt(l.colour());
						buf.writeVarInt(l.kind());
					}
				},
				buf -> {
					long seed = buf.readLong();
					int n = Math.min(buf.readVarInt(), MAX_SCARS);
					java.util.ArrayList<Long> cells = new java.util.ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						cells.add(buf.readLong());
					}
					int m = Math.min(buf.readVarInt(), MAX_LANDMARKS);
					java.util.ArrayList<Landmark> marks = new java.util.ArrayList<>(m);
					for (int i = 0; i < m; i++) {
						marks.add(new Landmark(
								buf.readInt(), buf.readInt(), buf.readInt(),
								buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
								buf.readInt(), buf.readVarInt()));
					}
					return new PlanetPayload(seed, cells, marks);
				}
		);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	/**
	 * Server → client: one observed surface section.
	 *
	 * <p>Sent a few at a time, only for the area a pilot's horizon would want, and only once — the
	 * client keeps what it has been given. Terrain that nobody has changed never needs sending
	 * again, so this converges to nothing rather than repeating itself.
	 */
	public record SurfacePayload(long key, byte[] data) implements CustomPayload {
		public static final Id<SurfacePayload> ID = new Id<>(SURFACE_ID);
		public static final PacketCodec<RegistryByteBuf, SurfacePayload> CODEC = PacketCodec.of(
				(payload, buf) -> {
					buf.writeLong(payload.key);
					buf.writeVarInt(payload.data.length);
					buf.writeBytes(payload.data);
				},
				buf -> {
					long key = buf.readLong();
					int n = buf.readVarInt();
					// A section is a fixed size; anything else is a build mismatch, not a section.
					if (n < 0 || n > 1 << 20) {
						buf.skipBytes(buf.readableBytes());
						return new SurfacePayload(key, new byte[0]);
					}
					byte[] data = new byte[n];
					buf.readBytes(data);
					return new SurfacePayload(key, data);
				}
		);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	public static final Identifier SCAFFOLD_ID = Identifier.of(DescentMod.MOD_ID, "scaffold");
	public static final Identifier SMOKE_ID = Identifier.of(DescentMod.MOD_ID, "smoke");
	public static final Identifier REACTOR_SYNC_ID = Identifier.of(DescentMod.MOD_ID, "reactor_sync");
	public static final Identifier FATE_ID = Identifier.of(DescentMod.MOD_ID, "fate");

	/** Server → client volumetric smoke snapshot (dedicated MP). */
	public record SmokePayload(int seq, java.util.List<Cloud> clouds) implements CustomPayload {
		public record Cloud(long idMsb, long idLsb, float x, float y, float z,
							float radius, float density, int life, int sourceOrdinal, int colorRgb) {}

		public static final Id<SmokePayload> ID = new Id<>(SMOKE_ID);
		public static final PacketCodec<RegistryByteBuf, SmokePayload> CODEC = PacketCodec.of(
				(payload, buf) -> {
					buf.writeVarInt(payload.seq);
					buf.writeVarInt(payload.clouds.size());
					for (Cloud c : payload.clouds) {
						buf.writeLong(c.idMsb);
						buf.writeLong(c.idLsb);
						buf.writeFloat(c.x);
						buf.writeFloat(c.y);
						buf.writeFloat(c.z);
						buf.writeFloat(c.radius);
						buf.writeFloat(c.density);
						buf.writeVarInt(c.life);
						buf.writeVarInt(c.sourceOrdinal);
						buf.writeInt(c.colorRgb);
					}
				},
				buf -> {
					int seq = buf.readVarInt();
					int n = buf.readVarInt();
					java.util.ArrayList<Cloud> list = new java.util.ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						list.add(new Cloud(
								buf.readLong(), buf.readLong(),
								buf.readFloat(), buf.readFloat(), buf.readFloat(),
								buf.readFloat(), buf.readFloat(),
								buf.readVarInt(), buf.readVarInt(), buf.readInt()));
					}
					return new SmokePayload(seq, list);
				}
		);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	/** Server → client reactor breaches, meteor falls, last detonation stamp. */
	public record ReactorSyncPayload(
			java.util.List<Breach> breaches,
			java.util.List<Fall> falls,
			int lastX, int lastY, int lastZ,
			long lastGameTime,
			boolean lastOrbital
	) implements CustomPayload {
		public record Breach(int x, int y, int z, String phase, int ticksLeft, boolean orbital, String vitality) {}
		public record Fall(int x, int z, int ticksLeft, int intensity) {}

		public static final Id<ReactorSyncPayload> ID = new Id<>(REACTOR_SYNC_ID);
		public static final PacketCodec<RegistryByteBuf, ReactorSyncPayload> CODEC = PacketCodec.of(
				(payload, buf) -> {
					buf.writeVarInt(payload.breaches.size());
					for (Breach b : payload.breaches) {
						buf.writeInt(b.x);
						buf.writeInt(b.y);
						buf.writeInt(b.z);
						buf.writeString(b.phase == null ? "" : b.phase);
						buf.writeVarInt(b.ticksLeft);
						buf.writeBoolean(b.orbital);
						buf.writeString(b.vitality == null ? "" : b.vitality);
					}
					buf.writeVarInt(payload.falls.size());
					for (Fall f : payload.falls) {
						buf.writeInt(f.x);
						buf.writeInt(f.z);
						buf.writeVarInt(f.ticksLeft);
						buf.writeVarInt(f.intensity);
					}
					buf.writeInt(payload.lastX);
					buf.writeInt(payload.lastY);
					buf.writeInt(payload.lastZ);
					buf.writeLong(payload.lastGameTime);
					buf.writeBoolean(payload.lastOrbital);
				},
				buf -> {
					int bn = buf.readVarInt();
					java.util.ArrayList<Breach> breaches = new java.util.ArrayList<>(bn);
					for (int i = 0; i < bn; i++) {
						breaches.add(new Breach(
								buf.readInt(), buf.readInt(), buf.readInt(),
								buf.readString(), buf.readVarInt(), buf.readBoolean(), buf.readString()));
					}
					int fn = buf.readVarInt();
					java.util.ArrayList<Fall> falls = new java.util.ArrayList<>(fn);
					for (int i = 0; i < fn; i++) {
						falls.add(new Fall(buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt()));
					}
					return new ReactorSyncPayload(
							breaches, falls,
							buf.readInt(), buf.readInt(), buf.readInt(),
							buf.readLong(), buf.readBoolean());
				}
		);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	/** Server → client world fate (silence / void endings). */
	public record FatePayload(String fate, int decayTicks) implements CustomPayload {
		public static final Id<FatePayload> ID = new Id<>(FATE_ID);
		public static final PacketCodec<RegistryByteBuf, FatePayload> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, FatePayload::fate,
				PacketCodecs.VAR_INT, FatePayload::decayTicks,
				FatePayload::new
		);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	/** Server → client construction scaffold frame (locked positions). */
	public record ScaffoldPayload(boolean active, String shapeId, java.util.List<net.minecraft.util.math.BlockPos> positions)
			implements CustomPayload {
		public static final Id<ScaffoldPayload> ID = new Id<>(SCAFFOLD_ID);
		public static final PacketCodec<RegistryByteBuf, ScaffoldPayload> CODEC = PacketCodec.of(
				(payload, buf) -> {
					buf.writeBoolean(payload.active);
					buf.writeString(payload.shapeId == null ? "" : payload.shapeId);
					buf.writeVarInt(payload.positions.size());
					for (net.minecraft.util.math.BlockPos p : payload.positions) {
						buf.writeBlockPos(p);
					}
				},
				buf -> {
					boolean active = buf.readBoolean();
					String shape = buf.readString();
					int n = buf.readVarInt();
					java.util.ArrayList<net.minecraft.util.math.BlockPos> list = new java.util.ArrayList<>(n);
					for (int i = 0; i < n; i++) list.add(buf.readBlockPos());
					return new ScaffoldPayload(active, shape, list);
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
		PayloadTypeRegistry.playS2C().register(ScaffoldPayload.ID, ScaffoldPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SmokePayload.ID, SmokePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ReactorSyncPayload.ID, ReactorSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FatePayload.ID, FatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PlanetPayload.ID, PlanetPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SurfacePayload.ID, SurfacePayload.CODEC);

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
				case "enable" -> FlightSystem.enable(player, data);
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
						player.giveItemStack(new net.minecraft.item.ItemStack(
								com.terminaldetector.drmd.weapon.items.ModItems.PYRO_GX));
						for (var w : com.terminaldetector.drmd.weapon.registry.ArsenalCatalog.creativeWeapons()) {
							player.giveItemStack(new net.minecraft.item.ItemStack(w));
						}
						for (var egg : com.terminaldetector.drmd.weapon.items.ModItems.creativeSpawnEggs()) {
							player.giveItemStack(new net.minecraft.item.ItemStack(egg));
						}
					}
				}
				case "preset_balanced" -> EnergySystem.setPreset(data, EnergyPreset.BALANCED);
				case "preset_assault" -> EnergySystem.setPreset(data, EnergyPreset.ASSAULT);
				case "preset_interceptor" -> EnergySystem.setPreset(data, EnergyPreset.INTERCEPTOR);
				case "preset_siege" -> EnergySystem.setPreset(data, EnergyPreset.SIEGE);
				case "ab_1" -> setAfterburnerTier(player, data, 1);
				case "ab_2" -> setAfterburnerTier(player, data, 2);
				case "ab_3" -> setAfterburnerTier(player, data, 3);
				case "ab_4" -> setAfterburnerTier(player, data, 4);
				default -> {
					String a = payload.action();
					// Creative ship physics from ShipCustomizeScreen — server gates creative.
					if (player.isCreative() && a != null && a.contains(":")) {
						int colon = a.indexOf(':');
						String key = a.substring(0, colon);
						try {
							float val = Float.parseFloat(a.substring(colon + 1));
							PyroShipEntity ship = pilotedShip(player);
							switch (key) {
								case "accel" -> {
									float v = net.minecraft.util.math.MathHelper.clamp(val, 500f, 12000f);
									if (ship != null) ship.setAccel(v); else data.setAccel(v);
								}
								case "drag" -> {
									float v = net.minecraft.util.math.MathHelper.clamp(val, 0.1f, 8f);
									if (ship != null) ship.setDrag(v); else data.setDrag(v);
								}
								case "maxspeed" -> {
									float v = net.minecraft.util.math.MathHelper.clamp(val, 400f, 6000f);
									if (ship != null) ship.setMaxSpeed(v); else data.setMaxSpeed(v);
								}
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

	/** The Pyro GX this player is currently riding, or {@code null} — the ship-vs-pilot resolution
	 *  point shared by every action/sync path that touches accel/drag/maxSpeed/afterburnerTier. */
	private static PyroShipEntity pilotedShip(ServerPlayerEntity player) {
		return player.getVehicle() instanceof PyroShipEntity ship ? ship : null;
	}

	private static void setAfterburnerTier(ServerPlayerEntity player, DescentPlayerData data, int tier) {
		PyroShipEntity ship = pilotedShip(player);
		if (ship != null) ship.setAfterburnerTier(tier); else data.setAfterburnerTier(tier);
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
		PyroShipEntity ship = pilotedShip(player);
		float syncAccel = ship != null ? ship.getAccel() : data.getAccel();
		float syncDrag = ship != null ? ship.getDrag() : data.getDrag();
		float syncMaxSpeed = ship != null ? ship.getMaxSpeed() : data.getMaxSpeed();
		int syncAbTier = ship != null ? ship.getAfterburnerTier() : data.getAfterburnerTier();
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
				syncAccel, syncDrag, syncMaxSpeed, data.getAllocEngines(),
				syncAbTier
		));
	}
}
