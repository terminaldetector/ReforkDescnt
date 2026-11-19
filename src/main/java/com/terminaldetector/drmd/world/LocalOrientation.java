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

    /** A finite direction is normalized without squaring its original magnitude. Invalid
     * legacy/network input retains the last valid frame; zero keeps the upright sentinel. */
    public static java.util.Optional<Vec3d> normalizeUp(Vec3d up) {
        if(up==null||!Double.isFinite(up.x)||!Double.isFinite(up.y)||!Double.isFinite(up.z))return java.util.Optional.empty();
        double scale=Math.max(Math.abs(up.x),Math.max(Math.abs(up.y),Math.abs(up.z)));
        if(scale<1e-3&&up.lengthSquared()<1e-6)return java.util.Optional.of(new Vec3d(0,1,0));
        var scaled=new Vec3d(up.x/scale,up.y/scale,up.z/scale);
        return java.util.Optional.of(scaled.multiply(1/Math.sqrt(scaled.lengthSquared())));
    }
    public static void setUp(UUID playerId,Vec3d up) {
        normalizeUp(up).ifPresent(value->PLAYER_UP.put(playerId,value));
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
