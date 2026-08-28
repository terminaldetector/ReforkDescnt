package com.terminaldetector.drmd.client.portal;

/**
 * Pure geometry for how a point or a direction transforms crossing from one portal to its paired
 * destination, or reflecting off a mirror — zero Minecraft/JOML imports, same idiom as
 * {@code SkirtGeometry}/{@code StructureFaceCuller}: the caller samples the facts (positions,
 * normals, scale — all of which need the game's classpath to obtain), this file only ever does
 * vector/quaternion arithmetic on numbers already in hand. Phase R0 of the native portal-rendering
 * plan: this is the one piece with zero rendering risk, fully exercised by
 * {@code PortalTransformTest} without a live client.
 *
 * <p>Rotation convention matches the one already proven in {@code ImmPtlMirrorBridge.linkPortals}:
 * the rotation maps THIS portal's own outward normal onto the <em>negation</em> of the destination
 * portal's outward normal — walking face-first into the source, you walk face-first back <em>out</em>
 * of the destination, continuing forward — not "source normal onto destination normal," which would
 * have you walking backward out the far side.
 */
public final class PortalTransform {
	private PortalTransform() {}

	public record Vec3(double x, double y, double z) {
		public Vec3 minus(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }
		public Vec3 plus(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
		public Vec3 scaled(double s) { return new Vec3(x * s, y * s, z * s); }
		public double dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }
		public Vec3 cross(Vec3 o) {
			return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
		}
		public double lengthSquared() { return dot(this); }
		public double length() { return Math.sqrt(lengthSquared()); }

		/** Returns this vector unchanged (rather than NaN) if it is too close to zero to have a direction. */
		public Vec3 normalized() {
			double len = length();
			return len < 1e-12 ? this : scaled(1.0 / len);
		}
	}

	/** Unit-quaternion rotation, {@code (x,y,z)} the vector part, {@code w} the scalar part. */
	public record Quat(double x, double y, double z, double w) {
		public static final Quat IDENTITY = new Quat(0, 0, 0, 1);

		public Quat normalized() {
			double len = Math.sqrt(x * x + y * y + z * z + w * w);
			if (len < 1e-12) return IDENTITY;
			return new Quat(x / len, y / len, z / len, w / len);
		}

		/** Unit quaternions only — the inverse rotation is the conjugate. */
		public Quat inverse() { return new Quat(-x, -y, -z, w); }

		/** Rotates {@code v} by this quaternion — {@code q * v * q⁻¹} expanded without building a matrix. */
		public Vec3 rotate(Vec3 v) {
			Vec3 qv = new Vec3(x, y, z);
			Vec3 t = qv.cross(v).scaled(2);
			return v.plus(t.scaled(w)).plus(qv.cross(t));
		}
	}

	private static final Vec3 WORLD_X = new Vec3(1, 0, 0);
	private static final Vec3 WORLD_Y = new Vec3(0, 1, 0);

	/**
	 * Shortest-arc rotation taking unit vector {@code from} onto unit vector {@code to}.
	 *
	 * <p>Degenerates when the two are exactly opposite: both the cross product and the usual
	 * construction's {@code w} term vanish together, and the shortest arc could turn about any axis
	 * perpendicular to {@code from} — genuinely reachable here, not just a theoretical edge: two
	 * portals mounted facing the <em>same</em> absolute direction (e.g. both on north-facing walls)
	 * pass exactly this pair to {@link #transformPoint}. Resolved by turning 180° about whichever
	 * world axis is least parallel to {@code from}, so the fallback cross product is never near-zero
	 * either.
	 */
	public static Quat rotationBetween(Vec3 from, Vec3 to) {
		double d = from.dot(to);
		Vec3 axis = from.cross(to);
		if (axis.lengthSquared() < 1e-12 && d < 0) {
			Vec3 fallbackAxis = Math.abs(from.dot(WORLD_X)) < 0.9 ? from.cross(WORLD_X) : from.cross(WORLD_Y);
			return new Quat(fallbackAxis.x, fallbackAxis.y, fallbackAxis.z, 0).normalized();
		}
		return new Quat(axis.x, axis.y, axis.z, 1 + d).normalized();
	}

	/**
	 * Where a point crossing this portal ends up, in world space. {@code scale} multiplies distance
	 * from the portal's own centre before rotating and translating to the destination — {@code 1.0}
	 * for an ordinary same-size portal, matching {@code Portal.transformPoint}'s shape.
	 */
	public static Vec3 transformPoint(Vec3 point, Vec3 portalPos, Vec3 portalNormal,
			Vec3 destPos, Vec3 destNormal, double scale) {
		Quat rotation = rotationBetween(portalNormal, destNormal.scaled(-1));
		Vec3 relative = point.minus(portalPos).scaled(scale);
		return destPos.plus(rotation.rotate(relative));
	}

	/**
	 * Rotation to apply to the camera's own orientation when it crosses this portal — the same
	 * rotation {@link #transformPoint} uses for position, since a portal (unlike a mirror) does not
	 * flip handedness: the destination-side view should turn exactly as a position vector would.
	 */
	public static Quat cameraRotation(Vec3 portalNormal, Vec3 destNormal) {
		return rotationBetween(portalNormal, destNormal.scaled(-1));
	}

	/**
	 * Reflection of a world-space point across the plane through {@code planePoint} with unit
	 * {@code normal} — a mirror's position transform. Negates exactly the component of the point's
	 * offset from the plane along {@code normal}; the two components tangential to the plane pass
	 * through unchanged.
	 */
	public static Vec3 reflectPoint(Vec3 point, Vec3 planePoint, Vec3 normal) {
		Vec3 relative = point.minus(planePoint);
		double alongNormal = relative.dot(normal);
		return point.minus(normal.scaled(2 * alongNormal));
	}

	/**
	 * Reflection of a direction (no position, e.g. a camera's look vector) across a plane with unit
	 * {@code normal} — a mirror's orientation transform. Same negate-the-normal-component shape as
	 * {@link #reflectPoint}, but a direction has no location to offset from first.
	 */
	public static Vec3 reflectVector(Vec3 v, Vec3 normal) {
		return v.minus(normal.scaled(2 * v.dot(normal)));
	}

	/** Yaw/pitch pair in degrees, vanilla {@code Camera}/{@code CameraAccessor} convention. */
	public record YawPitch(double yawDegrees, double pitchDegrees) {}

	/**
	 * Yaw/pitch (degrees) to a unit forward vector — mirrored exactly from
	 * {@code ShipAttitude.yawDegrees()}/{@code pitchDegrees()}'s own formula (the convention already
	 * driving DRMD's live 6DoF camera every frame), not re-derived independently, since
	 * {@code WorldRenderer.render()} reads the vanilla {@code Camera} basis that
	 * {@code CameraAccessor.drmd$invokeSetRotation(yaw, pitch)} builds from exactly this shape. Needed
	 * because a mirror only ever has a look <em>direction</em> to reflect ({@link #reflectVector}), but
	 * the real camera can only be told a yaw/pitch, not a vector.
	 */
	public static Vec3 yawPitchToVector(double yawDegrees, double pitchDegrees) {
		double yaw = Math.toRadians(yawDegrees);
		double pitch = Math.toRadians(pitchDegrees);
		double cosPitch = Math.cos(pitch);
		return new Vec3(-cosPitch * Math.sin(yaw), -Math.sin(pitch), cosPitch * Math.cos(yaw));
	}

	/**
	 * Exact inverse of {@link #yawPitchToVector} — same source of truth, same convention. Normalizes
	 * {@code forward} first so a reflected-but-not-renormalized direction still round-trips cleanly.
	 */
	public static YawPitch vectorToYawPitch(Vec3 forward) {
		Vec3 f = forward.normalized();
		double clampedY = Math.max(-1.0, Math.min(1.0, f.y()));
		double pitch = -Math.asin(clampedY);
		double yaw = Math.atan2(-f.x(), f.z());
		return new YawPitch(Math.toDegrees(yaw), Math.toDegrees(pitch));
	}
}
