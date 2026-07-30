package com.terminaldetector.drmd.world.gravity;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.LocalOrientation;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * On-foot local gravity — torch / generator redefine "down".
 * Pyro GX passengers are never affected; pilots keep thruster 6DoF.
 * Mutually exclusive with flight mode ({@link DescentPlayerData#isEnabled()}).
 *
 * <p>State is keyed by client/server side so integrated SP does not cross-wire modes.
 */
public final class FootGravitySystem {
	public static final double GRAVITY = 0.08;
	public static final double STICK_DIST = 1.4;

	public record State(Vec3d up, boolean active, boolean grounded, String label) {}

	private static final Map<String, State> STATES = new ConcurrentHashMap<>();

	private FootGravitySystem() {}

	private static String key(UUID id, boolean client) {
		return (client ? "c:" : "s:") + id;
	}

	private static String key(PlayerEntity player) {
		return key(player.getUuid(), player.getWorld().isClient);
	}

	public static State get(PlayerEntity player) {
		return STATES.get(key(player));
	}

	public static boolean isActive(PlayerEntity player) {
		State s = STATES.get(key(player));
		return s != null && s.active();
	}

	/** @deprecated prefer {@link #isActive(PlayerEntity)} — UUID alone is side-ambiguous on SP. */
	@Deprecated
	public static boolean isActive(UUID id) {
		State s = STATES.get(key(id, false));
		if (s != null && s.active()) return true;
		s = STATES.get(key(id, true));
		return s != null && s.active();
	}

	public static Vec3d getUp(PlayerEntity player) {
		State s = STATES.get(key(player));
		if (s != null && s.active()) return s.up();
		return LocalOrientation.getUp(player);
	}

	public static Vec3d getUp(UUID id) {
		State s = STATES.get(key(id, false));
		if (s != null && s.active()) return s.up();
		s = STATES.get(key(id, true));
		if (s != null && s.active()) return s.up();
		return LocalOrientation.getUp(id);
	}

	/** Clear both sides (flight arming / hard reset). */
	public static void clear(UUID id) {
		STATES.remove(key(id, true));
		STATES.remove(key(id, false));
	}

	public static void clear(PlayerEntity player) {
		STATES.remove(key(player));
	}

	/** Server tick — orientation / activation only (motion via {@link #travel}). */
	public static void tick(ServerPlayerEntity player) {
		if (player.getVehicle() instanceof PyroShipEntity) {
			clear(player);
			return;
		}
		DescentPlayerData data = DescentPlayerData.get(player);
		if (data.isEnabled()) {
			// Flight owns the body — never keep foot-gravity active underneath.
			clear(player);
			LocalOrientation.setUp(player, new Vec3d(0, 1, 0));
			return;
		}

		GravityFields.Sample field = GravityFields.sample(player.getWorld(), player.getPos());
		Vec3d up;
		String label;
		if (field != null) {
			up = field.upDir().normalize();
			label = field.label() != null ? field.label() : "Local Gravity";
		} else {
			up = LocalOrientation.getUp(player);
			label = "Local";
			if (isWorldUp(up)) {
				player.setNoGravity(false);
				clear(player);
				LocalOrientation.setUp(player, new Vec3d(0, 1, 0));
				if (player.age % 10 == 0) ModNetworking.syncPlayer(player, data);
				return;
			}
		}

		LocalOrientation.setUp(player, up);
		player.setNoGravity(true);
		player.fallDistance = 0f;

		boolean grounded = probeGround(player, up);
		STATES.put(key(player), new State(up, true, grounded, label));
		if (player.age % 2 == 0) ModNetworking.syncPlayer(player, data);
	}

	/**
	 * Remapped travel — replaces vanilla {@code LivingEntity.travel} while active.
	 * movementInput: strafe / jump / forward.
	 */
	public static void travel(PlayerEntity player, Vec3d movementInput) {
		// Never run foot travel while thrusters claim the player (mode exclusivity).
		if (DescentPlayerData.get(player).isEnabled()) {
			clear(player);
			return;
		}

		Vec3d up = getUp(player);
		if (up.lengthSquared() < 1e-6) up = new Vec3d(0, 1, 0);
		up = up.normalize();

		float yawRad = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d prefer = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
		Vec3d forward = prefer.subtract(up.multiply(prefer.dotProduct(up)));
		if (forward.lengthSquared() < 1e-6) {
			prefer = Math.abs(up.y) > 0.9 ? new Vec3d(0, 0, 1) : new Vec3d(0, 1, 0);
			forward = prefer.subtract(up.multiply(prefer.dotProduct(up)));
		}
		forward = forward.normalize();
		Vec3d right = up.crossProduct(forward).normalize();
		forward = right.crossProduct(up).normalize();

		float speed = player.getMovementSpeed();
		Vec3d wish = right.multiply(movementInput.x * speed * 2.5)
				.add(forward.multiply(movementInput.z * speed * 2.5));

		boolean grounded = probeGround(player, up);
		Vec3d vel = projectTangent(player.getVelocity(), up);

		if (movementInput.y > 0 && grounded) {
			vel = vel.add(up.multiply(0.42));
			grounded = false;
		}

		vel = vel.multiply(grounded ? 0.84 : 0.98);
		vel = vel.add(wish);
		if (!grounded) {
			vel = vel.add(up.negate().multiply(GRAVITY));
		} else {
			double into = vel.dotProduct(up.negate());
			if (into > 0) vel = vel.subtract(up.negate().multiply(into));
			snapToSurface(player, up);
		}

		player.setVelocity(vel);
		player.move(MovementType.SELF, vel);
		player.velocityDirty = true;
		player.setOnGround(grounded);
		player.fallDistance = 0f;

		State prev = STATES.get(key(player));
		String label = prev != null ? prev.label() : "Local";
		STATES.put(key(player), new State(up, true, grounded, label));
	}

	private static boolean probeGround(PlayerEntity player, Vec3d up) {
		Vec3d down = up.negate();
		Vec3d from = player.getPos().add(up.multiply(0.08));
		var hit = player.getWorld().raycast(new RaycastContext(
				from, from.add(down.multiply(STICK_DIST)),
				RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE,
				player));
		return hit.getType() == HitResult.Type.BLOCK
				&& hit.getPos().distanceTo(from) < STICK_DIST;
	}

	private static void snapToSurface(PlayerEntity player, Vec3d up) {
		Vec3d down = up.negate();
		Vec3d from = player.getPos().add(up.multiply(0.08));
		var hit = player.getWorld().raycast(new RaycastContext(
				from, from.add(down.multiply(STICK_DIST)),
				RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE,
				player));
		if (hit.getType() != HitResult.Type.BLOCK) return;
		Vec3d target = hit.getPos();
		Vec3d delta = target.subtract(player.getPos());
		double along = delta.dotProduct(up);
		if (Math.abs(along) > 0.02) {
			player.setPosition(player.getPos().add(up.multiply(along * 0.55)));
		}
	}

	private static Vec3d projectTangent(Vec3d v, Vec3d up) {
		return v.subtract(up.multiply(v.dotProduct(up)));
	}

	public static boolean isWorldUp(Vec3d up) {
		return up.squaredDistanceTo(0, 1, 0) < 0.04;
	}

	/** Adopt gravity field at a position (dismount / torch) — server side. */
	public static void adoptAt(ServerPlayerEntity player, Vec3d pos) {
		if (DescentPlayerData.get(player).isEnabled()) return;
		GravityFields.Sample field = GravityFields.sample(player.getWorld(), pos);
		if (field != null) {
			Vec3d up = field.upDir().normalize();
			LocalOrientation.setUp(player, up);
			STATES.put(key(player), new State(up, true, false,
					field.label() != null ? field.label() : "Local Gravity"));
			player.setNoGravity(true);
		}
	}

	/** Mirror active foot-gravity onto this side (client sync or server torch). */
	public static void adoptClient(PlayerEntity player, Vec3d up) {
		if (DescentPlayerData.get(player).isEnabled()) {
			clear(player);
			return;
		}
		if (up.lengthSquared() < 1e-6) up = new Vec3d(0, 1, 0);
		up = up.normalize();
		LocalOrientation.setUp(player, up);
		STATES.put(key(player), new State(up, true, false, "Local"));
	}

	/** @deprecated use {@link #adoptClient(PlayerEntity, Vec3d)} */
	@Deprecated
	public static void adoptClient(UUID id, Vec3d up) {
		if (up.lengthSquared() < 1e-6) up = new Vec3d(0, 1, 0);
		up = up.normalize();
		LocalOrientation.setUp(id, up);
		// Legacy: write client slot (sync path). Server torch should pass PlayerEntity.
		STATES.put(key(id, true), new State(up, true, false, "Local"));
	}
}
