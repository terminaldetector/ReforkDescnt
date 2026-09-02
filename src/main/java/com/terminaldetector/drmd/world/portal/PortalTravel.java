package com.terminaldetector.drmd.world.portal;

import com.terminaldetector.drmd.client.portal.PortalTransform;
import com.terminaldetector.drmd.diag.DiagTrace;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Carries entities through a linked portal pair — the world-facing half of {@link PortalCrossing},
 * which owns the arithmetic and is tested on its own.
 *
 * <p>Shared by both linked block families rather than written twice. A charged mirror is a one-block
 * face and a portal panel is several blocks across, but nothing else about travelling through them
 * differs, so the span is a parameter and the rest is common. Copying it would have let the two drift.
 *
 * <p><b>Why this exists at all:</b> both blocks already stored their partner and did nothing with it.
 * The travel was performed by an Immersive Portals {@code Portal} entity spawned in
 * {@code ImmPtlMirrorBridge}, so with that mod absent a linked pair was decorative and said so. This
 * is that behaviour without the dependency.
 *
 * <p><b>Same dimension only, deliberately.</b> Auto-linking produces nothing else, and a
 * cross-dimension hop is a different call with its own failure modes — chunk loading at the far end,
 * the player's own dimension-change packet. A cross-dimension link carries nobody yet, exactly as
 * before.
 */
public final class PortalTravel {
	private PortalTravel() {}

	/**
	 * Ticks a traveller is ignored for after arriving.
	 *
	 * <p>Not a rate limit — a correctness guard. A teleport leaves {@code prevX/prevY/prevZ} back at the
	 * portal that was entered, so the very next tick presents a step reaching from one end of the link
	 * to the other. That segment can cross the far portal's plane on its own and throw the traveller
	 * straight back, forever. Ignoring them briefly makes the stale step harmless without depending on
	 * exactly how any one entity type refreshes its previous position.
	 */
	private static final long ARRIVAL_COOLDOWN_TICKS = 10;

	/** Traveller → world time they may travel again. Pruned as it grows; never swept on a schedule. */
	private static final Map<UUID, Long> RECENT_ARRIVALS = new HashMap<>();

	/**
	 * Move anyone who stepped through {@code pos}'s face this tick to {@code partnerPos}'s.
	 *
	 * @param halfSpan how far from the face centre, along each in-plane axis, a crossing may land and
	 *                 still count — half a block for a mirror, the panel's own half-span for a portal
	 *                 panel. Slightly generous is right for a mirror: it forgives the corners without
	 *                 reaching the block beyond. Measured as a rectangle rather than a radius, so a
	 *                 wide panel keeps its corners — see {@link PortalCrossing#withinFace}.
	 */
	public static void carry(ServerWorld world, BlockPos pos, Direction facing,
			BlockPos partnerPos, Direction partnerFacing, double halfSpan) {
		Vec3d normal = Vec3d.of(facing.getVector());
		Vec3d partnerNormal = Vec3d.of(partnerFacing.getVector());
		// The face, not the block centre: that is the plane a traveller actually passes through.
		Vec3d face = Vec3d.ofCenter(pos).add(normal.multiply(0.5));
		Vec3d partnerFace = Vec3d.ofCenter(partnerPos).add(partnerNormal.multiply(0.5));

		PortalTransform.Vec3 plane = toPure(face);
		PortalTransform.Vec3 n = toPure(normal);

		// The box only decides who is worth testing; the step test below is what catches anything moving
		// faster than the box is wide, which at this project's ship speeds is the normal case.
		Box reach = new Box(pos).expand(halfSpan + 1.0);
		long now = world.getTime();

		// Passengers are skipped rather than carried: moving one out from under its vehicle desyncs the
		// pair, and the vehicle is itself in this list and travels on its own.
		for (Entity entity : world.getEntitiesByClass(Entity.class, reach,
				e -> !e.isSpectator() && !e.hasVehicle())) {
			if (onCooldown(entity.getUuid(), now)) continue;

			PortalTransform.Vec3 prev = new PortalTransform.Vec3(entity.prevX, entity.prevY, entity.prevZ);
			PortalTransform.Vec3 nowPos = new PortalTransform.Vec3(entity.getX(), entity.getY(), entity.getZ());
			if (!PortalCrossing.crossedInward(prev, nowPos, plane, n)) continue;

			// Where the step met the plane, so someone passing through the wall beside the portal is not
			// carried: the crossing has to land on the portal's own face.
			PortalTransform.Vec3 hit = PortalCrossing.crossingPoint(prev, nowPos, plane, n);
			if (hit == null) continue;
			if (!PortalCrossing.withinFace(hit, plane, n, halfSpan)) continue;

			PortalCrossing.Exit exit = PortalCrossing.exitFor(
					nowPos, toPure(entity.getVelocity()),
					plane, n, toPure(partnerFace), toPure(partnerNormal));

			// The event the whole native-travel feature exists to produce. Written before the teleport so
			// the record survives even if the move itself throws.
			DiagTrace.record("portal", "carried " + entity.getType().toString() + " through " + pos
					+ " to " + partnerPos);
			DiagTrace.count("portal.carried");
			entity.requestTeleport(exit.position().x(), exit.position().y(), exit.position().z());
			entity.setVelocity(new Vec3d(exit.velocity().x(), exit.velocity().y(), exit.velocity().z()));
			// Without this the client keeps its own predicted velocity and fights the new one.
			entity.velocityModified = true;
			markArrived(entity.getUuid(), now);
		}
	}

	private static boolean onCooldown(UUID id, long now) {
		Long until = RECENT_ARRIVALS.get(id);
		if (until == null) return false;
		if (now >= until) {
			RECENT_ARRIVALS.remove(id);
			return false;
		}
		return true;
	}

	private static void markArrived(UUID id, long now) {
		// Bounded without a scheduled sweep: entries only ever expire, so clearing the stale ones
		// whenever the map grows keeps it the size of "travellers in flight" rather than of everyone
		// who has ever used a portal on this server.
		if (RECENT_ARRIVALS.size() > 64) {
			RECENT_ARRIVALS.entrySet().removeIf(e -> now >= e.getValue());
		}
		RECENT_ARRIVALS.put(id, now + ARRIVAL_COOLDOWN_TICKS);
	}

	private static PortalTransform.Vec3 toPure(Vec3d v) {
		return new PortalTransform.Vec3(v.x, v.y, v.z);
	}
}
