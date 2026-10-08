package com.terminaldetector.drmd.physics.ragdoll;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable, data-driven collection of rigid parts and ball-socket joints. */
public final class RagdollRig {
	public record Segment(String id, double mass, Vec3 halfExtents, Vec3 restPosition) {
		public Segment {
			if (id == null || id.isBlank()) throw new IllegalArgumentException("segment id is blank");
			if (!Double.isFinite(mass) || mass <= 0) throw new IllegalArgumentException("segment mass must be positive");
			if (halfExtents == null || restPosition == null
					|| !finite(halfExtents) || !finite(restPosition)
					|| halfExtents.x() <= 0 || halfExtents.y() <= 0 || halfExtents.z() <= 0)
				throw new IllegalArgumentException("invalid segment geometry");
		}
	}

	/** Anchors are expressed in each segment's local axes and should meet in the rest pose. */
	public record Joint(int first, int second, Vec3 firstAnchor, Vec3 secondAnchor, double compliance) {
		public Joint {
			if (first == second || first < 0 || second < 0) throw new IllegalArgumentException("invalid joint endpoints");
			if (firstAnchor == null || secondAnchor == null || !finite(firstAnchor) || !finite(secondAnchor))
				throw new IllegalArgumentException("invalid joint anchors");
			if (!Double.isFinite(compliance) || compliance < 0) throw new IllegalArgumentException("invalid joint compliance");
		}
	}

	private final List<Segment> segments;
	private final List<Joint> joints;

	public RagdollRig(List<Segment> segments, List<Joint> joints) {
		if (segments == null || segments.isEmpty()) throw new IllegalArgumentException("ragdoll has no segments");
		if (joints == null) throw new IllegalArgumentException("ragdoll joints are null");
		this.segments = List.copyOf(segments);
		this.joints = List.copyOf(joints);
		Set<String> ids = new HashSet<>();
		for (Segment segment : this.segments)
			if (!ids.add(segment.id())) throw new IllegalArgumentException("duplicate segment " + segment.id());
		for (Joint joint : this.joints)
			if (joint.first() >= this.segments.size() || joint.second() >= this.segments.size())
				throw new IllegalArgumentException("joint endpoint outside rig");
	}

	public List<Segment> segments() { return segments; }
	public List<Joint> joints() { return joints; }

	/** Five-part version of the current scout: core plus four spars that can fold after destruction. */
	public static RagdollRig scoutDrone() {
		Segment core = new Segment("core", 4.0, new Vec3(.22, .22, .22), new Vec3(0, 0, 0));
		Segment north = new Segment("north", .7, new Vec3(.07, .07, .18), new Vec3(0, 0, -.40));
		Segment south = new Segment("south", .7, new Vec3(.07, .07, .18), new Vec3(0, 0, .40));
		Segment east = new Segment("east", .7, new Vec3(.18, .07, .07), new Vec3(.40, 0, 0));
		Segment west = new Segment("west", .7, new Vec3(.18, .07, .07), new Vec3(-.40, 0, 0));
		return new RagdollRig(List.of(core, north, south, east, west), List.of(
				new Joint(0, 1, new Vec3(0, 0, -.22), new Vec3(0, 0, .18), 0),
				new Joint(0, 2, new Vec3(0, 0, .22), new Vec3(0, 0, -.18), 0),
				new Joint(0, 3, new Vec3(.22, 0, 0), new Vec3(-.18, 0, 0), 0),
				new Joint(0, 4, new Vec3(-.22, 0, 0), new Vec3(.18, 0, 0), 0)));
	}

	private static boolean finite(Vec3 value) {
		return Double.isFinite(value.x()) && Double.isFinite(value.y()) && Double.isFinite(value.z());
	}
}
