package com.terminaldetector.drmd.physics.ragdoll;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6Mat3;
import com.terminaldetector.drmd.d6.D6PhysicsBody;
import com.terminaldetector.drmd.d6.D6PortalTransform;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Plans one rigid world-frame change of an existing articulated simulation; never advances it. */
public final class RagdollPortalTransfer {
	private RagdollPortalTransfer() {}

	/** Immutable destination geometry. The residual is used for sweep validation, never added twice. */
	public record PartPose(Vec3 position, Quat rotation, Vec3 halfExtents) {}

	private record State(Vec3 position, Quat rotation, Vec3 velocity, Vec3 momentum,
			Vec3 force, Vec3 torque, double mass, D6Mat3 inertia) {
		static State read(D6PhysicsBody body) {
			return new State(body.position(), body.rotation(), body.linearVelocity(), body.angularMomentum(),
					body.accumulatedForce(), body.accumulatedTorque(), body.mass(), body.inertia());
		}
		boolean finite() {
			return RagdollPortalTransfer.finite(position) && valid(rotation)
					&& RagdollPortalTransfer.finite(velocity) && RagdollPortalTransfer.finite(momentum)
					&& RagdollPortalTransfer.finite(force) && RagdollPortalTransfer.finite(torque)
					&& Double.isFinite(mass) && mass > 0 && inertia != null;
		}
	}

	/**
	 * Pure preflight. The effective transform already contains the coordinator's shared exit offset;
	 * destinationRemainder is only the post-plane displacement whose sweep must be checked.
	 */
	public static Optional<Prepared> prepare(RagdollSimulation simulation, D6PortalTransform transform,
			Vec3 destinationRemainder) {
		if (simulation == null || transform == null || transform.scale() != 1
				|| !finite(transform.sourcePoint()) || !finite(transform.destPoint())
				|| !finite(destinationRemainder) || !valid(transform.rotation())) return Optional.empty();
		List<State> before = new ArrayList<>();
		List<PartPose> destination = new ArrayList<>(), sweepStart = new ArrayList<>();
		for (RagdollSimulation.Part part : simulation.parts()) {
			State state = State.read(part.body());
			if (!state.finite()) return Optional.empty();
			Vec3 position = transform.transformPoint(state.position());
			Quat rotation = transform.transformOrientation(state.rotation());
			if (!finite(position) || !finite(position.minus(destinationRemainder)) || !valid(rotation)
					|| !finite(transform.transformVelocity(state.velocity()))
					|| !finite(transform.transformAngularMomentum(state.momentum()))
					|| !finite(transform.rotation().rotate(state.force()))
					|| !finite(transform.rotation().rotate(state.torque()))) return Optional.empty();
			before.add(state);
			destination.add(new PartPose(position, rotation, part.segment().halfExtents()));
			sweepStart.add(new PartPose(position.minus(destinationRemainder), rotation, part.segment().halfExtents()));
		}
		return Optional.of(new Prepared(simulation, transform, before, destination, sweepStart));
	}

	/** Planned values are immutable; the commit token is single use and refuses a stale simulation. */
	public static final class Prepared {
		private final RagdollSimulation simulation;
		private final D6PortalTransform transform;
		private final List<State> before;
		private final List<D6PhysicsBody.Snapshot> snapshots;
		private final List<PartPose> destination, sweepStart;
		private final Vec3 destinationCentre;
		private boolean applied;

		private Prepared(RagdollSimulation simulation, D6PortalTransform transform, List<State> before,
				List<PartPose> destination, List<PartPose> sweepStart) {
			this.simulation = simulation;
			this.transform = transform;
			this.before = List.copyOf(before);
			this.snapshots = simulation.parts().stream().map(part -> part.body().snapshot()).toList();
			this.destination = List.copyOf(destination);
			this.sweepStart = List.copyOf(sweepStart);
			this.destinationCentre = transform.transformPoint(simulation.centreOfMass());
		}

		public List<PartPose> destinationParts() { return destination; }
		public List<PartPose> sweepStartParts() { return sweepStart; }
		public Vec3 destinationCentre() { return destinationCentre; }
		public Quat rotation() { return transform.rotation(); }
		public Vec3 transformPoint(Vec3 point) { return transform.transformPoint(point); }

		public boolean current() {
			if (applied || simulation.parts().size() != before.size()) return false;
			for (int i = 0; i < before.size(); i++)
				if (!simulation.parts().get(i).body().matches(snapshots.get(i))
						|| !before.get(i).equals(State.read(simulation.parts().get(i).body()))) return false;
			return true;
		}

		/** Call only after every member of the portal transaction has passed its world preflight. */
		public boolean apply() {
			if (!current()) return false;
			applied = true;
			for (RagdollSimulation.Part part : simulation.parts()) transform.apply(part.body(), null);
			return true;
		}
	}

	private static boolean finite(Vec3 v) {
		return v != null && Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z())
				&& Double.isFinite(v.lengthSquared());
	}
	private static boolean valid(Quat q) {
		if (q == null) return false;
		double length = q.x()*q.x() + q.y()*q.y() + q.z()*q.z() + q.w()*q.w();
		return Double.isFinite(length) && length > 1e-12;
	}
}
