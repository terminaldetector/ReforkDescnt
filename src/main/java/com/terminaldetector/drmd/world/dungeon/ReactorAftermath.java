package com.terminaldetector.drmd.world.dungeon;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.world.fire.FireSystem;
import com.terminaldetector.drmd.world.llod.planet.PlanetMapState;
import com.terminaldetector.drmd.world.smoke.SmokeSystem;
import com.terminaldetector.drmd.world.sync.DimensionSync;
import com.terminaldetector.drmd.world.sync.DimensionSyncState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Post-detonation: ash/smoke field, planet-map scars, scheduled meteor falls to surface.
 * Fall-view mode (client) lets pilots corkscrew down and inspect consequences.
 */
public final class ReactorAftermath {
	public record FallView(int blockX, int blockZ, int ticksLeft, int intensity) {}

	private static final class FallEvent {
		final int blockX, blockZ, intensity;
		int ticksLeft;

		FallEvent(int blockX, int blockZ, int ticksLeft, int intensity) {
			this.blockX = blockX;
			this.blockZ = blockZ;
			this.ticksLeft = ticksLeft;
			this.intensity = intensity;
		}
	}

	private static final List<FallEvent> FALLS = new ArrayList<>();

	private ReactorAftermath() {}

	public static void onFacilityDetonation(ServerWorld host, BlockPos core, boolean orbital) {
		SmokeSystem.emitExplosion(Vec3d.ofCenter(core), orbital ? 14f : 8f);
		FireSystem.igniteBlast(host, core, orbital ? 16 : 10, orbital ? 10 : 6);
		host.spawnParticles(ParticleTypes.ASH,
				core.getX() + 0.5, core.getY() + 2, core.getZ() + 0.5,
				80, 6, 4, 6, 0.02);
		host.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
				core.getX() + 0.5, core.getY() + 3, core.getZ() + 0.5,
				40, 5, 3, 5, 0.01);

		ServerWorld ow = host.getServer().getOverworld();
		if (ow == null) return;
		PlanetMapState map = PlanetMapState.get(ow);
		int x = core.getX();
		int z = core.getZ();
		int intensity = orbital ? 3 : 2;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (dx * dx + dz * dz > 5) continue;
				map.scarBlock(x + dx * 16, z + dz * 16, intensity);
			}
		}

		if (orbital) {
			for (int i = 0; i < 5; i++) {
				int ox = x + (host.getRandom().nextInt(97) - 48);
				int oz = z + (host.getRandom().nextInt(97) - 48);
				FALLS.add(new FallEvent(ox, oz, 20 * (8 + i * 3), intensity));
				map.scarBlock(ox, oz, intensity);
			}
			DescentMod.LOGGER.info("Orbital detonation — 5 meteor falls queued toward surface");
		}
	}

	/** Final End giga-reactor death — full station break + asteroid rain. */
	public static void onGigaReactorDestroyed(ServerWorld endOrHost, BlockPos pad) {
		onFacilityDetonation(endOrHost, pad, true);
		DimensionSync.recordDetonation(endOrHost.getServer(), pad, true);
		ServerWorld ow = endOrHost.getServer().getOverworld();
		if (ow == null) return;
		PlanetMapState map = PlanetMapState.get(ow);
		for (int i = 0; i < 12; i++) {
			int ox = pad.getX() + (endOrHost.getRandom().nextInt(161) - 80);
			int oz = pad.getZ() + (endOrHost.getRandom().nextInt(161) - 80);
			FALLS.add(new FallEvent(ox, oz, 20 * (6 + i * 2), 3));
			map.scarBlock(ox, oz, 3);
		}
	}

	public static void tick(MinecraftServer server) {
		if (FALLS.isEmpty()) return;
		ServerWorld ow = server.getOverworld();
		if (ow == null) return;
		for (int i = FALLS.size() - 1; i >= 0; i--) {
			FallEvent e = FALLS.get(i);
			e.ticksLeft--;
			if (e.ticksLeft > 0) continue;
			SmokeSystem.emit(new Vec3d(e.blockX + 0.5, 120, e.blockZ + 0.5),
					SmokeSystem.Source.REACTOR, 5f, 0.7f, 120);
			if (ow.isChunkLoaded(e.blockX >> 4, e.blockZ >> 4)) {
				BlockPos at = ow.getTopPosition(Heightmap.Type.MOTION_BLOCKING,
						new BlockPos(e.blockX, 0, e.blockZ));
				FireSystem.igniteBlast(ow, at, 5, 3);
				SmokeSystem.emitExplosion(Vec3d.ofCenter(at), 4f);
				ow.spawnParticles(ParticleTypes.ASH,
						at.getX() + 0.5, at.getY() + 2, at.getZ() + 0.5,
						40, 3, 2, 3, 0.02);
			}
			FALLS.remove(i);
		}
	}

	public static void clear() {
		FALLS.clear();
	}

	public static int pendingFalls() {
		return FALLS.size();
	}

	public static List<FallView> snapshotFalls() {
		List<FallView> out = new ArrayList<>(FALLS.size());
		for (FallEvent e : FALLS) {
			out.add(new FallView(e.blockX, e.blockZ, e.ticksLeft, e.intensity));
		}
		return out;
	}

	public static void loadFrom(DimensionSyncState state) {
		FALLS.clear();
		for (DimensionSyncState.FallNbt n : state.falls()) {
			FALLS.add(new FallEvent(n.x, n.z, n.ticks, n.intensity));
		}
		DescentMod.LOGGER.info("Restored {} meteor falls from dimension sync", FALLS.size());
	}

	public static void saveTo(DimensionSyncState state) {
		List<DimensionSyncState.FallNbt> list = new ArrayList<>(FALLS.size());
		for (FallEvent e : FALLS) {
			DimensionSyncState.FallNbt n = new DimensionSyncState.FallNbt();
			n.x = e.blockX;
			n.z = e.blockZ;
			n.ticks = e.ticksLeft;
			n.intensity = e.intensity;
			list.add(n);
		}
		state.replaceFalls(list);
	}
}
