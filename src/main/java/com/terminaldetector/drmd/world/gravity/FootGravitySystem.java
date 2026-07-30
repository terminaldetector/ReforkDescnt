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
 * After dismount (flight off), players walk on walls/ceilings with stable local UP.
 */
public final class FootGravitySystem {
	public static final double GRAVITY = 0.08;
	public static final double STICK_DIST = 1.4;

	public record State(Vec3d up, boolean active, boolean grounded, String label) {}

	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

	private FootGravitySystem() {}

	public static State get(UUID id) {
		return STATES.get(id);
	}

	public static boolean isActive(UUID id) {
		State s = STATES.get(id);
		return s != null && s.active();
	}

	public static Vec3d getUp(UUID id) {
		State s = STATES.get(id);
		if (s != null && s.active()) return s.up();
		return LocalOrientation.getUp(id);
	}

	public static void clear(UUID id) {
		STATES.remove(id);
	}

	/** Server tick — orientation / activation only (motion via {@link #travel}). */
	public static void tick(ServerPlayerEntity player) {
		if (player.getVehicle() instanceof PyroShipEntity) {
			clear(player.getUuid());
			return;
		}
		DescentPlayerData data = DescentPlayerData.get(player);
		if (data.isEnabled()) {
			clear(player.getUuid());
			return;
		}

		GravityFields.Sample field = GravityFields.sample(player.getWorld(), player.getPos());
		Vec3d up;
		String label;
		if (field != null) {
			up = field.upDir().normalize();
			label = field.label() != null ? field.label() : "Local Gravity";
		} else {
			up = LocalOrientation.getUp(player.getUuid());
			label = "Local";
			if (isWorldUp(up)) {
				player.setNoGravity(false);
				clear(player.getUuid());
				LocalOrientation.setUp(player.getUuid(), new Vec3d(0, 1, 0));
				if (player.age % 10 == 0) ModNetworking.syncPlayer(player, data);
				return;
			}
		}

		LocalOrientation.setUp(player.getUuid(), up);
		player.setNoGravity(true);
		player.fallDistance = 0f;

		boolean grounded = probeGround(player, up);
		STATES.put(player.getUuid(), new State(up, true, grounded, label));
		if (player.age % 2 == 0) ModNetworking.syncPlayer(player, data);
	}

	/**
	 * Remapped travel — replaces vanilla {@code LivingEntity.travel} while active.
	 * movementInput: strafe / jump / forward.
	 */
	public static void travel(PlayerEntity player, Vec3d movementInput) {
		Vec3d up = getUp(player.getUuid());
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
			// Kill into-floor component and keep contact
			double into = vel.dotProduct(up.negate());
			if (into > 0) vel = vel.subtract(up.negate().multiply(into));
			snapToSurface(player, up);
		}

		player.setVelocity(vel);
		player.move(MovementType.SELF, vel);
		player.velocityDirty = true;
		player.setOnGround(grounded);
		player.fallDistance = 0f;

		if (player instanceof ServerPlayerEntity sp) {
			State prev = STATES.get(sp.getUuid());
			String label = prev != null ? prev.label() : "Local";
			STATES.put(sp.getUuid(), new State(up, true, grounded, label));
		}
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
		double stand = 0.0;
		Vec3d target = hit.getPos().add(up.multiply(stand));
		Vec3d delta = target.subtract(player.getPos());
		double along = delta.dotProduct(up);
		// Only correct along up/down, keep tangential freedom
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

	/** Adopt gravity field at a position (dismount / torch). */
	public static void adoptAt(ServerPlayerEntity player, Vec3d pos) {
		GravityFields.Sample field = GravityFields.sample(player.getWorld(), pos);
		if (field != null) {
			Vec3d up = field.upDir().normalize();
			LocalOrientation.setUp(player.getUuid(), up);
			STATES.put(player.getUuid(), new State(up, true, false,
					field.label() != null ? field.label() : "Local Gravity"));
			player.setNoGravity(true);
		}
	}

	/** Client mirror of active foot-gravity state (from sync). */
	public static void adoptClient(UUID id, Vec3d up) {
		if (up.lengthSquared() < 1e-6) up = new Vec3d(0, 1, 0);
		up = up.normalize();
		LocalOrientation.setUp(id, up);
		STATES.put(id, new State(up, true, false, "Local"));
	}
}
