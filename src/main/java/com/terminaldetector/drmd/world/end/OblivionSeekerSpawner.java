package com.terminaldetector.drmd.world.end;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.mob.OblivionSeekerEntity;
import com.terminaldetector.drmd.world.layer.WorldLayer;
import com.terminaldetector.drmd.world.level.WorldLevels;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Sparse Oblivion Seeker encounters — one annoying solo pre-boss near End / high-column pilots.
 * Arena fights also inject escorts via {@link EndReactorSession}.
 */
public final class OblivionSeekerSpawner {
	private static final int CHECK_INTERVAL = 20 * 12;
	private static final double NEARBY = 96.0;

	private OblivionSeekerSpawner() {}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % CHECK_INTERVAL != 0) return;
		if (!com.terminaldetector.drmd.world.fate.WorldEndings.allowMachineSpawn(server)) return;
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (p.isCreative() || p.isSpectator()) continue;
			WorldLayer layer = WorldLayer.at(p.getWorld(), p.getY());
			boolean oblivion = layer == WorldLayer.OBLIVION
					|| p.getWorld().getRegistryKey() == World.END
					|| p.getY() >= WorldLevels.ORBITAL_TOP - 24;
			if (!oblivion) continue;
			trySpawnNear(p);
		}
	}

	private static void trySpawnNear(ServerPlayerEntity p) {
		ServerWorld world = p.getServerWorld();
		Box box = p.getBoundingBox().expand(NEARBY);
		long count = world.getEntitiesByType(ModEntities.OBLIVION_SEEKER, box, e -> e.isAlive()).size();
		if (count > 0) return; // solo — never stack patrols
		if (p.getRandom().nextFloat() > 0.35f) return;

		Vec3d look = p.getRotationVec(1f);
		double ox = (p.getRandom().nextDouble() - 0.5) * 40;
		double oz = (p.getRandom().nextDouble() - 0.5) * 40;
		BlockPos at = BlockPos.ofFloored(
				p.getX() + look.x * 28 + ox,
				p.getY() + 8 + p.getRandom().nextDouble() * 6,
				p.getZ() + look.z * 28 + oz);
		if (!world.isChunkLoaded(at.getX() >> 4, at.getZ() >> 4)) return;

		OblivionSeekerEntity seeker = ModEntities.OBLIVION_SEEKER.create(world);
		if (seeker == null) return;
		seeker.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYaw() + 180, 0);
		seeker.setTarget(p);
		world.spawnEntity(seeker);
		p.sendMessage(net.minecraft.text.Text.literal(
				"§d◉ Oblivion Seeker §7inbound — End-faction pre-boss."), true);
	}

	/** Arena escorts — 2–3 Seekers on approach pillars. */
	public static void spawnArenaEscorts(ServerWorld world, BlockPos center, int count) {
		for (int i = 0; i < count; i++) {
			double ang = (Math.PI * 2 * i) / Math.max(1, count) + 0.4;
			double r = 18 + (i % 2) * 6;
			OblivionSeekerEntity s = ModEntities.OBLIVION_SEEKER.create(world);
			if (s == null) continue;
			s.refreshPositionAndAngles(
					center.getX() + 0.5 + Math.cos(ang) * r,
					center.getY() + 14 + (i % 3),
					center.getZ() + 0.5 + Math.sin(ang) * r,
					0, 0);
			world.spawnEntity(s);
		}
	}
}
