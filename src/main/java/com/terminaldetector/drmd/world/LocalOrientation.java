package com.terminaldetector.drmd.world;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local orientation — any surface can be floor/wall/ceiling.
 * Magnetic anomalies and surface-snap building redefine local "up".
 */
public final class LocalOrientation {
	private static final Map<UUID, Vec3d> PLAYER_UP = new ConcurrentHashMap<>();

	private LocalOrientation() {}

	public static Vec3d getUp(UUID playerId) {
		return PLAYER_UP.getOrDefault(playerId, new Vec3d(0, 1, 0));
	}

	public static void setUp(UUID playerId, Vec3d up) {
		if (up.lengthSquared() < 1e-6) {
			PLAYER_UP.put(playerId, new Vec3d(0, 1, 0));
		} else {
			PLAYER_UP.put(playerId, up.normalize());
		}
	}

	public static void clear(UUID playerId) {
		PLAYER_UP.remove(playerId);
	}

	public static void setFromDirection(UUID playerId, Direction dir) {
		setUp(playerId, Vec3d.of(dir.getVector()));
	}

	/** Convert world gravity direction for flight idle-grav / micro-grav. */
	public static Vec3d gravityDir(UUID playerId) {
		return getUp(playerId).negate();
	}
}
