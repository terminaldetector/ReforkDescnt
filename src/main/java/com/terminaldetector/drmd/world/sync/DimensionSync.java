package com.terminaldetector.drmd.world.sync;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.dungeon.FacilityReactorFight;
import com.terminaldetector.drmd.world.dungeon.ReactorAftermath;
import com.terminaldetector.drmd.world.level.WorldLevels;
import com.terminaldetector.drmd.world.smoke.SmokeSystem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Full column · orbit · End synchronization hub.
 *
 * <ul>
 *   <li>Persist facility breaches + meteor falls on Overworld {@link DimensionSyncState}</li>
 *   <li>Broadcast smoke clouds to every client (dedicated MP)</li>
 *   <li>Broadcast reactor/aftermath HUD snapshot to OW / End / high orbit pilots</li>
 * </ul>
 */
public final class DimensionSync {
	private static boolean loaded;
	private static int smokeSeq;
	private static volatile boolean smokeDirty = true;

	private DimensionSync() {}

	public static void markSmokeDirty() {
		smokeDirty = true;
	}

	/** Load persistent fight/fall state after server start clears in-memory maps. */
	public static void load(MinecraftServer server) {
		loaded = false;
		ServerWorld ow = server.getOverworld();
		if (ow == null) return;
		DimensionSyncState state = DimensionSyncState.get(ow);
		FacilityReactorFight.loadFrom(state, server);
		ReactorAftermath.loadFrom(state);
		loaded = true;
		smokeDirty = true;
		DescentMod.LOGGER.info("Dimension sync loaded — facilities={} falls={} lastDet={}",
				state.facilities().size(), state.falls().size(), state.lastDetonationGameTime());
	}

	public static void tick(MinecraftServer server) {
		if (!loaded) return;
		ServerWorld ow = server.getOverworld();
		if (ow == null) return;

		int tick = server.getTicks();
		// Persist every second
		if (tick % 20 == 0) {
			persist(server, ow);
		}
		// Smoke sync ~5 Hz when dirty / always light refresh
		if (smokeDirty || tick % 8 == 0) {
			broadcastSmoke(server);
			smokeDirty = false;
		}
		// Reactor HUD snapshot ~2 Hz
		if (tick % 10 == 0) {
			broadcastReactor(server);
		}
	}

	public static void persist(MinecraftServer server, ServerWorld ow) {
		DimensionSyncState state = DimensionSyncState.get(ow);
		FacilityReactorFight.saveTo(state, server);
		ReactorAftermath.saveTo(state);
	}

	public static void recordDetonation(MinecraftServer server, BlockPos at, boolean orbital) {
		ServerWorld ow = server.getOverworld();
		if (ow == null) return;
		DimensionSyncState state = DimensionSyncState.get(ow);
		state.recordDetonation(ow.getTime(), at, orbital);
		markSmokeDirty();
		// Immediate reactor push so End pilots see the stamp
		broadcastReactor(server);
	}

	private static ModNetworking.SmokePayload buildSmokePayload() {
		List<SmokeSystem.Cloud> all = SmokeSystem.all();
		// Cap wire size — densest/longest-lived 48
		all.sort((a, b) -> Float.compare(b.density * b.life, a.density * a.life));
		int n = Math.min(48, all.size());
		ArrayList<ModNetworking.SmokePayload.Cloud> wire = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			SmokeSystem.Cloud c = all.get(i);
			wire.add(new ModNetworking.SmokePayload.Cloud(
					c.id.getMostSignificantBits(), c.id.getLeastSignificantBits(),
					(float) c.pos.x, (float) c.pos.y, (float) c.pos.z,
					c.radius, c.density, c.life, c.source.ordinal(), c.colorRgb));
		}
		smokeSeq++;
		return new ModNetworking.SmokePayload(smokeSeq, wire);
	}

	private static void broadcastSmoke(MinecraftServer server) {
		ModNetworking.SmokePayload payload = buildSmokePayload();
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	private static ModNetworking.ReactorSyncPayload buildReactorPayload(MinecraftServer server) {
		ServerWorld ow = server.getOverworld();
		DimensionSyncState state = DimensionSyncState.get(ow);

		ArrayList<ModNetworking.ReactorSyncPayload.Breach> breaches = new ArrayList<>();
		for (FacilityReactorFight.Facility f : FacilityReactorFight.all()) {
			if (f.phase != FacilityReactorFight.Phase.BREACH
					&& f.phase != FacilityReactorFight.Phase.ARMED) continue;
			breaches.add(new ModNetworking.ReactorSyncPayload.Breach(
					f.core.getX(), f.core.getY(), f.core.getZ(),
					f.phase.name(), f.ticksLeft, f.orbital, f.vitality.name()));
		}

		ArrayList<ModNetworking.ReactorSyncPayload.Fall> falls = new ArrayList<>();
		for (ReactorAftermath.FallView fv : ReactorAftermath.snapshotFalls()) {
			falls.add(new ModNetworking.ReactorSyncPayload.Fall(
					fv.blockX(), fv.blockZ(), fv.ticksLeft(), fv.intensity()));
		}

		BlockPos last = state.lastDetonationPos();
		return new ModNetworking.ReactorSyncPayload(
				breaches, falls,
				last != null ? last.getX() : 0,
				last != null ? last.getY() : 0,
				last != null ? last.getZ() : 0,
				state.lastDetonationGameTime(),
				state.lastDetonationOrbital());
	}

	private static void broadcastReactor(MinecraftServer server) {
		if (server.getOverworld() == null) return;
		ModNetworking.ReactorSyncPayload payload = buildReactorPayload(server);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (!needsReactorHud(p)) continue;
			ServerPlayNetworking.send(p, payload);
		}
	}

	/** OW / End / high orbit — anyone who cares about column aftermath. */
	public static boolean needsReactorHud(ServerPlayerEntity p) {
		if (p.getWorld().getRegistryKey() == World.END) return true;
		if (p.getWorld().getRegistryKey() == World.OVERWORLD) {
			return p.getY() >= WorldLevels.SKY_TOP
					|| FacilityReactorFight.activeBreaches() > 0
					|| ReactorAftermath.pendingFalls() > 0
					|| stateHasRecentDetonation(p.getServerWorld());
		}
		return true;
	}

	private static boolean stateHasRecentDetonation(ServerWorld world) {
		ServerWorld ow = world.getServer().getOverworld();
		if (ow == null) return false;
		DimensionSyncState state = DimensionSyncState.get(ow);
		long t = state.lastDetonationGameTime();
		return t >= 0 && ow.getTime() - t < 20L * 60 * 10;
	}

	/** Immediate push on join so dedicated clients get smoke/reactor without waiting a tick. */
	public static void pushTo(ServerPlayerEntity player) {
		if (!loaded) return;
		MinecraftServer server = player.getServer();
		if (server == null || server.getOverworld() == null) return;
		ServerPlayNetworking.send(player, buildSmokePayload());
		if (needsReactorHud(player)) {
			ServerPlayNetworking.send(player, buildReactorPayload(server));
		}
	}

	public static RegistryKey<World> dimKey(Identifier id) {
		return RegistryKey.of(RegistryKeys.WORLD, id);
	}

	public static Identifier dimId(ServerWorld world) {
		return world.getRegistryKey().getValue();
	}

	public static ServerWorld worldOf(MinecraftServer server, Identifier dim) {
		ServerWorld w = server.getWorld(dimKey(dim));
		return w != null ? w : server.getOverworld();
	}

	/** Distance helper for HUD — nearest breach to a player (any dim uses XZ+Y). */
	public static double distSq(Vec3d a, BlockPos b) {
		double dx = a.x - (b.getX() + 0.5);
		double dy = a.y - (b.getY() + 0.5);
		double dz = a.z - (b.getZ() + 0.5);
		return dx * dx + dy * dy + dz * dz;
	}
}
