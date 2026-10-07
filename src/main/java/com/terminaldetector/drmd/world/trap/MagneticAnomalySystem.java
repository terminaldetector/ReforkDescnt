package com.terminaldetector.drmd.world.trap;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.flight.ShipAttitude;
import com.terminaldetector.drmd.world.LocalOrientation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime pulses and release state for magnetic anomalies. */
public final class MagneticAnomalySystem {
	public static final double RADIUS = 8.0;
	private static final double CAPTURE = 0.16;
	private static final double RELEASE = 0.20;
	private static final long PULSE_TICKS = 12;
	private record State(RegistryKey<World> world, BlockPos origin, long expiresAt) {}
	private static final Map<UUID, State> ACTIVE = new ConcurrentHashMap<>();

	private MagneticAnomalySystem() {}

	/** Refresh a smooth radial local-UP pulse for nearby 6DoF pilots. */
	public static void pulse(ServerWorld world, BlockPos origin) {
		Box volume = new Box(origin).expand(RADIUS);
		for (ServerPlayerEntity player : world.getEntitiesByClass(ServerPlayerEntity.class, volume,
				PlayerEntity::isAlive)) {
			if (!DescentPlayerData.get(player).isEnabled()) continue;
			Vec3d radial = Vec3d.ofCenter(origin).subtract(player.getPos());
			if (radial.lengthSquared() < 1.0e-6) continue;
			Vec3d blended = ShipAttitude.slerp(LocalOrientation.getUp(player.getUuid()), radial.normalize(), CAPTURE);
			LocalOrientation.setUp(player.getUuid(), blended);
			ACTIVE.put(player.getUuid(), new State(world.getRegistryKey(), origin.toImmutable(), world.getTime() + PULSE_TICKS));
			player.addVelocity(radial.normalize().multiply(0.018));
			player.velocityModified = true;
			world.spawnParticles(net.minecraft.particle.ParticleTypes.REVERSE_PORTAL,
					player.getX(), player.getY(), player.getZ(), 2, 0.15, 0.15, 0.15, 0.01);
		}
		world.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL,
				origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5,
				8, 0.45, 0.45, 0.45, 0.02);
	}

	/** Release pilots after a broken/out-of-range anomaly instead of leaving stale orientation. */
	public static void tick(MinecraftServer server) {
		ACTIVE.forEach((id, state) -> {
			ServerWorld world = server.getWorld(state.world());
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (world == null || player == null || !player.isAlive()
					|| !player.getWorld().getRegistryKey().equals(state.world())
					|| !DescentPlayerData.get(player).isEnabled()) {
				ACTIVE.remove(id, state);
				return;
			}
			boolean live = world.getBlockState(state.origin()).getBlock() == com.terminaldetector.drmd.entity.ModWorldBlocks.MAGNETIC_ANOMALY;
			boolean inside = player.squaredDistanceTo(Vec3d.ofCenter(state.origin())) <= RADIUS * RADIUS;
			if (live && inside && world.getTime() <= state.expiresAt()) return;
			Vec3d restored = ShipAttitude.slerp(LocalOrientation.getUp(id), new Vec3d(0, 1, 0), RELEASE);
			LocalOrientation.setUp(id, restored);
			if (restored.squaredDistanceTo(0, 1, 0) < 0.0025) {
				LocalOrientation.clear(id);
				ACTIVE.remove(id, state);
			} else {
				ACTIVE.put(id, new State(state.world(), state.origin(), world.getTime() + 1));
			}
		});
	}

	public static void clear() { ACTIVE.clear(); }
}
