package com.terminaldetector.drmd.world.gravity;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.LocalOrientation;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
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
	/** Per-tick arc toward a field's up when it takes hold — ~0.4 s to settle. */
	private static final double CAPTURE_RATE = 0.18;
	/** Faster on release so stepping off a walkway reads as letting go, not sliding off. */
	private static final double RELEASE_RATE = 0.26;
	/** Multiplier on movement speed that reproduces vanilla's 0.216 blocks/tick walk. */
	private static final float WALK_GAIN = 0.35f;

	public record State(Vec3d up, boolean active, boolean grounded, String label) {}

	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, State> CLIENT_STATES = new ConcurrentHashMap<>();
    private static final GravityNoticeGate NOTICES = new GravityNoticeGate();

	private FootGravitySystem() {}

    public static State get(PlayerEntity player) {
        return (player.getWorld().isClient?CLIENT_STATES:STATES).get(player.getUuid());
    }
    public static boolean isActive(PlayerEntity player) {
        State s=get(player); return s!=null && s.active();
    }
    public static Vec3d getUp(PlayerEntity player) {
        State s=get(player); return s!=null&&s.active()?s.up():LocalOrientation.getUp(player.getUuid());
    }
    public static void clearAllClient() { CLIENT_STATES.clear(); }
    public static void clearClient(UUID id) { CLIENT_STATES.remove(id); }
    public static void forget(UUID id) { STATES.remove(id); CLIENT_STATES.remove(id); NOTICES.forget(id); }
    public static void clearAll() { STATES.clear(); CLIENT_STATES.clear(); NOTICES.clear(); }

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
        if(com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.drives(player)) {
            clear(player.getUuid());NOTICES.observe(player.getUuid(),null,player.getWorld().getTime());return;
        }
		if (player.getVehicle() instanceof PyroShipEntity || player.isSpectator() || !player.isAlive()) {
            NOTICES.observe(player.getUuid(),null,player.getWorld().getTime());
			clear(player.getUuid());
			return;
		}
		DescentPlayerData data = DescentPlayerData.get(player);
		if (data.isEnabled()) {
            NOTICES.observe(player.getUuid(),null,player.getWorld().getTime());
			clear(player.getUuid());
			return;
		}

		GravityFields.Sample field = GravityFields.sample(player.getWorld(), player.getPos());
        boolean notify=NOTICES.observe(player.getUuid(),field==null?null:field.sourceId(),player.getWorld().getTime());
		Vec3d up;
		String label;
		boolean hardMount = false;
		boolean wasActive = isActive(player.getUuid());
		if (field != null) {
			Vec3d target = field.upDir().normalize();
			Vec3d current = getUp(player);
			double align = current.dotProduct(target);
			// Big reorient (wall/ceiling capture, ceiling flip): snap + safe place so the standing
			// AABB is not crushed into the mount face (vanilla crawl ≈ 1-cube).
			if (!wasActive || align < 0.5) {
				up = target;
				hardMount = true;
			} else {
				// Ease onto the surface: walking into reach should roll the world under you.
				up = com.terminaldetector.drmd.flight.ShipAttitude.slerp(current, target, CAPTURE_RATE);
			}
			label = field.label() != null ? field.label() : "Local Gravity";
			// Walking is only the default here, not the only option — this field never blocks the
			// 6DoF toggle (tick's own isEnabled() guard above hands off to FlightSystem entirely,
			// and a piloted Pyro GX ignores station fields altogether — see FlightSystem.tick's
			// onPyro guard), so say so the first time a field takes hold, not every tick.
			if (notify) player.sendMessage(Text.translatable("message.drmd.local_gravity_active"),true);
		} else {
			// Out of reach — bleed back to world up and hand the player to vanilla gravity. Holding
			// the last surface's up forever left them walking on thin air off the end of a walkway.
			up = com.terminaldetector.drmd.flight.ShipAttitude.slerp(
					getUp(player), new Vec3d(0, 1, 0), RELEASE_RATE);
			label = "Local";
			if (isWorldUp(up)) {
				// Upright and out of reach: nothing to do. Guarded on having actually been held,
				// because this is the branch every walking player falls through every tick — an
				// unconditional sync here would be a packet per tick, per player, forever.
				if (isActive(player.getUuid())) {
					player.setNoGravity(false);
					clear(player.getUuid());
					LocalOrientation.setUp(player.getUuid(), new Vec3d(0, 1, 0));
                    player.calculateDimensions();
					ModNetworking.syncPlayer(player, data);
				}
				return;
			}
		}

		LocalOrientation.setUp(player.getUuid(), up);
		player.setNoGravity(true);
		player.fallDistance = 0f;
		// Mount/pose changes recalculate the actual box. Publish the new frame first,
		// so those calculations cannot temporarily use the previous wall or world-up.
		STATES.put(player.getUuid(), new State(up, true, false, label));
		if (hardMount) {
			GravityMount.safeMount(player, up);
		}
		// Prefer standing size while local gravity holds and there is clearance.
		if (GravityMount.hasStandingClearance(player)
				&& player.getPose() != net.minecraft.entity.EntityPose.STANDING) {
			player.setPose(net.minecraft.entity.EntityPose.STANDING);
			player.setSwimming(false);
		}

		boolean grounded = probeGround(player, up);
		STATES.put(player.getUuid(), new State(up, true, grounded, label));
        player.calculateDimensions();
		if (player.age % 2 == 0) ModNetworking.syncPlayer(player, data);
	}

	/**
	 * Remapped travel — replaces vanilla {@code LivingEntity.travel} while active.
	 * movementInput: strafe / jump / forward.
	 */
	public static void travel(PlayerEntity player, Vec3d movementInput) {
		// This runs from inside PlayerEntity.travel's super call, so a creative pilot who is still
		// flying would get Y rewritten as `previousY * 0.6` the moment we return — enough to peel
		// them off a wall every tick. You walk surfaces here; H gives creative flight back.
		if (player.getAbilities().flying) {
			player.getAbilities().flying = false;
		}
		Vec3d up = getUp(player);
		if (up.lengthSquared() < 1e-6) up = new Vec3d(0, 1, 0);
		up = up.normalize();

		// Walk direction comes from the full look vector flattened onto the surface, not from world
		// yaw. Yaw alone rotates about world Y: flatten that against a vertical wall and every
		// heading collapses onto one axis, leaving the player able to face exactly two directions.
		// The look vector already lives in the surface's frame (FootLook keeps it there), so
		// projecting it out is the whole job — and on a floor it reduces to plain vanilla forward.
		Vec3d prefer = player.getRotationVec(1f);
		Vec3d forward = prefer.subtract(up.multiply(prefer.dotProduct(up)));
		if (forward.lengthSquared() < 1e-6) {
			// Looking straight along local up: fall back to a stable tangent so movement still works.
			float yawRad = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
			prefer = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
			forward = prefer.subtract(up.multiply(prefer.dotProduct(up)));
			if (forward.lengthSquared() < 1e-6) {
				prefer = Math.abs(up.y) > 0.9 ? new Vec3d(0, 0, 1) : new Vec3d(0, 1, 0);
				forward = prefer.subtract(up.multiply(prefer.dotProduct(up)));
			}
		}
		forward = forward.normalize();
		Vec3d right = up.crossProduct(forward).normalize();
		forward = right.crossProduct(up).normalize();

		// Ground friction is 0.84, so terminal speed is accel/0.16. The old 2.5 gain put that at
		// 1.56 blocks/tick — seven times vanilla walking, which read as skating rather than walking
		// on the wall. 0.35 lands on 0.219, which is vanilla's 0.216 to within a rounding error.
		float speed = player.getMovementSpeed() * (player.isSprinting() ? WALK_GAIN * 1.3f : WALK_GAIN);
		Vec3d wish = right.multiply(movementInput.x * speed)
				.add(forward.multiply(movementInput.z * speed));

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
		Vec3d delta = hit.getPos().subtract(player.getPos());
		double along = delta.dotProduct(up);

		// Correct along local up only, and go through move() rather than setPosition().
		//
		// setPosition ignores collision, so a correction pointing into the surface pushed the hitbox
		// inside the block — the model sank into the wall. The next tick's move() shoved it back out,
		// the tick after that put it in again, and that alternation is the jitter. move() clips
		// against the same geometry the walk does, so the two agree.
		//
		// The deadband is wide enough to sit still on a flat surface: at 0.02 the raycast's own
		// rounding was enough to keep it correcting every single tick, and a correction every tick
		// is a shake however small each one is.
		if (Math.abs(along) <= SNAP_DEADBAND) return;
		double step = MathHelper.clamp(along * SNAP_GAIN, -SNAP_MAX_STEP, SNAP_MAX_STEP);
		player.move(MovementType.SELF, up.multiply(step));
	}

	/** Below this the player is close enough to the surface to leave alone. */
	private static final double SNAP_DEADBAND = 0.06;
	/** Fraction of the remaining gap closed per tick — under-damped on purpose, so it settles. */
	private static final double SNAP_GAIN = 0.35;
	/** Never move more than this in one tick; a big correction is a teleport, and it reads as one. */
	private static final double SNAP_MAX_STEP = 0.25;

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
			GravityMount.safeMount(player, up);
		}
	}

    public static void adoptServer(UUID id,Vec3d up,String label) {
        Vec3d normalized=up.lengthSquared()<1e-6?new Vec3d(0,1,0):up.normalize();
        LocalOrientation.setUp(id,normalized);
        STATES.put(id,new State(normalized,true,false,label));
    }

	/** Client mirror of active foot-gravity state (from sync). */
	public static void adoptClient(UUID id, Vec3d up) {
		if (up.lengthSquared() < 1e-6) up = new Vec3d(0, 1, 0);
		up = up.normalize();
		LocalOrientation.setUp(id, up);
		CLIENT_STATES.put(id, new State(up, true, false, "Local"));
	}
}
