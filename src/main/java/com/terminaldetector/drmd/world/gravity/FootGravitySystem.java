package com.terminaldetector.drmd.world.gravity;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.LocalOrientation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;
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
	private static final double CONTACT_DEPTH = .08;
    private static final double CONTACT_SKIN = .02;
	private static final Vec3d WORLD_UP = new Vec3d(0,1,0);
	/** Per-tick arc toward a field's up when it takes hold — ~0.4 s to settle. */
	private static final double CAPTURE_RATE = 0.18;
	/** Multiplier on movement speed that reproduces vanilla's 0.216 blocks/tick walk. */
	private static final float WALK_GAIN = 0.35f;

	public record State(Vec3d up, boolean active, boolean grounded, String label) {}

	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, State> CLIENT_STATES = new ConcurrentHashMap<>();
    private static final GravityNoticeGate NOTICES = new GravityNoticeGate();
    private record GravityFlag(java.lang.ref.WeakReference<ServerPlayerEntity> owner,boolean original,java.lang.ref.WeakReference<DescentPlayerData> mode) {}
    private static final Map<UUID,GravityFlag> GRAVITY_FLAGS=new ConcurrentHashMap<>();
    private static void holdGravity(ServerPlayerEntity player) {
        var flag=GRAVITY_FLAGS.get(player.getUuid());
        if(flag==null||flag.owner().get()!=player)
            GRAVITY_FLAGS.put(player.getUuid(),new GravityFlag(new java.lang.ref.WeakReference<>(player),player.hasNoGravity(),new java.lang.ref.WeakReference<>(DescentPlayerData.get(player))));
        player.setNoGravity(true);
    }
    private static void releaseGravity(UUID id) {
        var flag=GRAVITY_FLAGS.remove(id);if(flag==null)return;
        var player=flag.owner().get();var mode=flag.mode().get();
        // Restore only our actual object's unchanged flag. New mode owners and external
        // false writes win; UUID aliases never receive another entity's cleanup.
        if(player==null||!player.hasNoGravity()||player.isSpectator()
            ||player.getVehicle() instanceof PyroShipEntity||mode!=null&&mode.isEnabled()
            ||com.terminaldetector.drmd.world.contraption.boarding.BoardingPose.active(player))return;
        player.setNoGravity(flag.original());
    }

	private FootGravitySystem() {}

    public static State get(PlayerEntity player) {
        if(!player.getWorld().isClient) {
            var flag=GRAVITY_FLAGS.get(player.getUuid());
            if(flag!=null&&flag.owner().get()!=player)return null;
        }
        return (player.getWorld().isClient?CLIENT_STATES:STATES).get(player.getUuid());
    }
    /** Only the actual flag owner may substitute its original flag at native save time. */
    public static boolean savedNoGravity(PlayerEntity player,boolean actual) {
        if(player.getWorld().isClient||!actual)return actual;
        var flag=GRAVITY_FLAGS.get(player.getUuid());
        return flag!=null&&flag.owner().get()==player?flag.original():actual;
    }
    public static boolean isActive(PlayerEntity player) {
        State s=get(player); return s!=null && s.active();
    }
    public static Vec3d getUp(PlayerEntity player) {
        State s=get(player); return s!=null&&s.active()?s.up():LocalOrientation.getUp(player.getUuid());
    }
    public static void clearAllClient() { CLIENT_STATES.clear(); }
    public static void clearClient(UUID id) { CLIENT_STATES.remove(id); }
    public static void forget(UUID id) { clear(id); CLIENT_STATES.remove(id); NOTICES.forget(id); }
    public static void clearAll() {
        for(var id:java.util.List.copyOf(GRAVITY_FLAGS.keySet()))releaseGravity(id);
        STATES.clear(); CLIENT_STATES.clear(); NOTICES.clear();
    }

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
		STATES.remove(id);releaseGravity(id);
	}

	/** Server tick — orientation / activation only (motion via {@link #travel}). */
	public static void tick(ServerPlayerEntity player) {
        if(com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.drives(player)) {
            clear(player.getUuid());NOTICES.observe(player.getUuid(),player.getWorld().getRegistryKey().getValue().toString(),null,player.getWorld().getTime());return;
        }
		if (player.getVehicle() instanceof PyroShipEntity || player.isSpectator() || !player.isAlive()) {
            NOTICES.observe(player.getUuid(),player.getWorld().getRegistryKey().getValue().toString(),null,player.getWorld().getTime());
			clear(player.getUuid());
			return;
		}
		DescentPlayerData data = DescentPlayerData.get(player);
		if (data.isEnabled()) {
            NOTICES.observe(player.getUuid(),player.getWorld().getRegistryKey().getValue().toString(),null,player.getWorld().getTime());
			clear(player.getUuid());
			return;
		}

        var previous = get(player);
        var field = GravityFields.sample(player.getWorld(), player.getPos());
        if (field == null) {
            observe(player, null);
            if (previous == null || !releaseSafely(player)) return;
            // Keep the camera's smooth recovery separate from collision geometry. Turning
            // the physical box in steps can wedge it between the ceiling and a nearby wall.
            player.sendMessage(Text.translatable("message.drmd.local_gravity_released"), true);
            ModNetworking.syncPlayer(player, data);
            return;
        }

        Vec3d current = previous == null ? WORLD_UP : previous.up();
        Vec3d target = field.upDir();
        Vec3d up = previous == null || current.dotProduct(target) < .5 ? target
            : com.terminaldetector.drmd.flight.ShipAttitude.slerp(current, target, CAPTURE_RATE);
        String label = field.label() == null ? "Local Gravity" : field.label();

        Vec3d previousFeet = player.getPos();
        if (!adoptFrame(player, current, up, label)) {
            observe(player, null);
            return;
        }
        if (observe(player, field)) {
            player.sendMessage(Text.translatable("message.drmd.local_gravity_active"), true);
            player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sound.SoundEvents.BLOCK_BEACON_ACTIVATE, net.minecraft.sound.SoundCategory.BLOCKS, .35f, 1.7f);
        }
        if (player.age % 2 == 0 || previous == null || !previousFeet.equals(player.getPos()))
            ModNetworking.syncPlayer(player, data);
    }

    private static boolean observe(ServerPlayerEntity player, GravityFields.Sample field) {
        return NOTICES.observe(player.getUuid(), player.getWorld().getRegistryKey().getValue().toString(),
            field == null ? null : field.sourceId(), player.getWorld().getTime());
    }

    private static boolean adoptFrame(ServerPlayerEntity player, Vec3d current, Vec3d up, String label) {
        Vec3d feet = player.getPos();
        if (current.squaredDistanceTo(up) > 1e-12) {
            var safe = GravityMount.framePosition(player, current, up);
            if (safe.isEmpty()) return false;
            feet = safe.get();
        }
        var id = player.getUuid();
        LocalOrientation.setUp(id, up);
        holdGravity(player);
        STATES.put(id, new State(up, true, false, label));
        applyFramePosition(player, feet);
        if (!player.isTouchingWater() && player.getPose() == net.minecraft.entity.EntityPose.SWIMMING
            && GravityMount.hasStandingClearance(player)) {
            player.setPose(net.minecraft.entity.EntityPose.STANDING);
            player.setSwimming(false);
        }
        STATES.put(id, new State(up, true, probeGround(player, up), label));
        player.fallDistance = 0;
        return true;
    }

    /** Used before handing the body to vanilla or free flight. */
    public static boolean releaseSafely(ServerPlayerEntity player) {
        if (!isActive(player)) return true;
        Vec3d current = getUp(player);
        Vec3d feet = player.getPos();
        if (current.squaredDistanceTo(WORLD_UP) > 1e-12) {
            var safe = GravityMount.framePosition(player, current, WORLD_UP);
            if (safe.isEmpty()) return false;
            feet = safe.get();
        }
        clear(player.getUuid());
        LocalOrientation.setUp(player.getUuid(), WORLD_UP);
        applyFramePosition(player, feet);
        return true;
    }

    private static void applyFramePosition(ServerPlayerEntity player, Vec3d feet) {
        if (!feet.equals(player.getPos())) player.requestTeleport(feet.x, feet.y, feet.z);
        player.calculateDimensions();
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

        Vec3d velocity = player.getVelocity();
        boolean grounded = velocity.dotProduct(up) <= 0 && probeGround(player, up);
        if (grounded) {
            velocity = velocity.subtract(up.multiply(velocity.dotProduct(up))).multiply(.84).add(wish);
            if (movementInput.y > 0) velocity = velocity.add(up.multiply(.42));
        } else {
            velocity = velocity.multiply(.98).add(wish).subtract(up.multiply(GRAVITY));
        }

        // Unknown cube volumes do not provide air for a local-gravity move.
        var box = player.getBoundingBox();
        var motionWithSkin = velocity.subtract(up.multiply(CONTACT_SKIN));
        if (!GravityMount.loadedSpace(player.getWorld(), box.stretch(velocity).union(box.stretch(motionWithSkin)))) return;
        double normal = velocity.dotProduct(up);
        if (!grounded && normal < 0) {
            // Keep a small clearance against the actual support. A reported position right at an
            // axis-aligned wall can fall on opposite sides of the contact due to rounding.
            var allowed = Entity.adjustMovementForCollisions(player, motionWithSkin, box, player.getWorld(), List.of());
            double safe = Math.min(0, allowed.dotProduct(up) + CONTACT_SKIN);
            if (safe > normal) velocity = velocity.add(up.multiply(safe - normal));
        }
        player.setVelocity(velocity);
        player.move(MovementType.SELF, velocity);
        player.velocityDirty = true;
        grounded = velocity.dotProduct(up) <= 0 && probeGround(player, up);
        player.setOnGround(grounded);
        player.fallDistance = 0;

        if (player instanceof ServerPlayerEntity sp) {
            State previous = get(sp);
            STATES.put(sp.getUuid(), new State(up, true, grounded, previous == null ? "Local" : previous.label()));
        }
    }

    private static boolean probeGround(PlayerEntity player, Vec3d up) {
        var normal = up.negate().multiply(CONTACT_DEPTH);
        var box = player.getBoundingBox();
        if (!GravityMount.loadedSpace(player.getWorld(), box.stretch(normal))) return false;
        var allowed = Entity.adjustMovementForCollisions(player, normal, box, player.getWorld(), List.of());
        return allowed.dotProduct(up.negate()) < CONTACT_DEPTH - 1e-6;
    }

    public static boolean isWorldUp(Vec3d up) {
        return up.squaredDistanceTo(WORLD_UP) < .04;
    }

	/** Adopt gravity field at a position (dismount / torch). */
    public static void adoptAt(ServerPlayerEntity player, Vec3d pos) {
        var field = GravityFields.sample(player.getWorld(), pos);
        if (field == null || DescentPlayerData.get(player).isEnabled()) return;
        Vec3d current = isActive(player) ? getUp(player) : WORLD_UP;
        if (adoptFrame(player, current, field.upDir(), field.label() == null ? "Local Gravity" : field.label()))
            ModNetworking.syncPlayer(player, DescentPlayerData.get(player));
    }


    public static void adoptServer(UUID id,Vec3d up,String label) {
        var decoded=LocalOrientation.normalizeUp(up);if(decoded.isEmpty())return;
        Vec3d normalized=decoded.orElseThrow();
        LocalOrientation.setUp(id,normalized);
        STATES.put(id,new State(normalized,true,false,label));
    }

	/** Client mirror of active foot-gravity state (from sync). */
	public static void adoptClient(UUID id, Vec3d up) {
        var decoded=LocalOrientation.normalizeUp(up);if(decoded.isEmpty())return;
        var normalized=decoded.orElseThrow();
        LocalOrientation.setUp(id,normalized);
        CLIENT_STATES.put(id,new State(normalized,true,false,"Local"));
	}
}
