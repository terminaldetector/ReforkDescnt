package com.terminaldetector.drmd.world;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local orientation — any surface can be floor/wall/ceiling.
 * Magnetic anomalies and surface-snap building redefine local "up".
 *
 * <p>Keyed by client/server side so integrated SP flight vs foot-gravity do not share UP.
 */
public final class LocalOrientation {
	private static final Map<String, Vec3d> PLAYER_UP = new ConcurrentHashMap<>();

	private LocalOrientation() {}

	private static String key(UUID id, boolean client) {
		return (client ? "c:" : "s:") + id;
	}

	private static String key(PlayerEntity player) {
		return key(player.getUuid(), player.getWorld().isClient);
	}

	public static Vec3d getUp(PlayerEntity player) {
		return PLAYER_UP.getOrDefault(key(player), new Vec3d(0, 1, 0));
	}

	public static Vec3d getUp(UUID playerId) {
		Vec3d s = PLAYER_UP.get(key(playerId, false));
		if (s != null) return s;
		return PLAYER_UP.getOrDefault(key(playerId, true), new Vec3d(0, 1, 0));
	}

	public static void setUp(PlayerEntity player, Vec3d up) {
		setUpKey(key(player), up);
	}

	public static void setUp(UUID playerId, Vec3d up) {
		// Legacy callers: set both sides so neither mode reads a stale opposite-side UP.
		setUpKey(key(playerId, false), up);
		setUpKey(key(playerId, true), up);
	}

	private static void setUpKey(String k, Vec3d up) {
		if (up == null || up.lengthSquared() < 1e-6) {
			PLAYER_UP.put(k, new Vec3d(0, 1, 0));
		} else {
			PLAYER_UP.put(k, up.normalize());
		}
	}

	public static void clear(UUID playerId) {
		PLAYER_UP.remove(key(playerId, true));
		PLAYER_UP.remove(key(playerId, false));
	}

	public static void clear(PlayerEntity player) {
		PLAYER_UP.remove(key(player));
	}

	public static void setFromDirection(UUID playerId, Direction dir) {
		setUp(playerId, Vec3d.of(dir.getVector()));
	}

	/** Convert world gravity direction for flight idle-grav / micro-grav. */
	public static Vec3d gravityDir(UUID playerId) {
		return getUp(playerId).negate();
	}
}
