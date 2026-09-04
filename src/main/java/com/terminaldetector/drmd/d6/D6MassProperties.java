package com.terminaldetector.drmd.d6;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;

/**
 * Mass, centre of mass and inertia tensor, accumulated one lump at a time.
 *
 * <p>A body made of Minecraft blocks does not know its own inertia in advance and does not keep the
 * same one: blocks are added, shot off, and chiselled away. So this accumulates incrementally rather
 * than integrating over a known shape — each addition shifts the running centre of mass and carries
 * the tensor across to it.
 *
 * <p><b>The parallel-axis theorem, applied twice per addition.</b> An inertia tensor is only
 * meaningful about a particular point, and adding mass moves the centre of mass, so the tensor
 * accumulated about the old centre has to be transported to the new one before the new mass's own
 * contribution about that centre is added. Both terms appear in every line below: {@code shift} is
 * how far the centre moved, weighted by all the mass already there, and {@code r} is the new lump's
 * offset from the new centre, weighted by its own.
 *
 * <p>The algorithm is Valkyrien Skies 1's, read from source during the PHASE 0 audit and written up
 * in {@code docs/source-audit/algorithm-map.md}; this is DRMD's own implementation of it, checked
 * against a direct computation over a forty-point cloud to 1e-13. VS1 cites
 * {@code kwon3d.com/theory/moi/triten.html} for the derivation.
 *
 * <p>Mutable on purpose — it is built up and then read once into a {@link D6PhysicsBody}.
 */
public final class D6MassProperties {

	/**
	 * Half-offset of the eight corner samples a block is modelled by, in blocks.
	 *
	 * <p>A block enters as nine point masses of a ninth each — one at the centre, eight at the
	 * corners of a cube this far out. Nine rather than one because a point mass has no moment of its
	 * own, and a hull made of single points would spin about its own centre with nothing resisting.
	 *
	 * <p><b>This number is not the donor's.</b> Valkyrien Skies uses 0.4, which with this layout gives
	 * a tensor 1.7067 times that of the solid cube the block actually is — measured, not estimated.
	 * The value here is the one that reproduces a solid unit cube exactly: eight corners at {@code d}
	 * and a centre point, each of mass {@code m/9}, give {@code I = 16d²m/9}, and a solid unit cube is
	 * {@code m/6}, so {@code d = √(9/96) = 0.30618621784789724}. Nothing was gained by keeping 0.4
	 * except agreement with a number that looks round.
	 */
	public static final double BLOCK_SAMPLE_OFFSET = 0.30618621784789724;

	/** Below this the body is treated as massless, and adding to it starts from scratch. */
	private static final double MASS_EPSILON = 1e-4;

	private double mass;
	private Vec3 centreOfMass = new Vec3(0, 0, 0);
	private D6Mat3 inertia = D6Mat3.ZERO;

	public double mass() {
		return mass;
	}

	/** In the same space the lumps were given in — body space, for a structure. */
	public Vec3 centreOfMass() {
		return centreOfMass;
	}

	/** About the centre of mass, in body axes. */
	public D6Mat3 inertia() {
		return inertia;
	}

	/**
	 * Add one point mass.
	 *
	 * <p>A negative mass removes one, which is how a block being broken is handled — the same call
	 * with the sign flipped, rather than a second code path that has to agree with this one.
	 */
	public D6MassProperties addPointMass(Vec3 position, double addedMass) {
		if (Math.abs(addedMass) < 1e-12) return this;

		Vec3 previousCentre = centreOfMass;
		double previousMass = mass;

		if (previousMass > MASS_EPSILON) {
			centreOfMass = centreOfMass.scaled(previousMass)
					.plus(position.scaled(addedMass))
					.scaled(1.0 / (previousMass + addedMass));
		} else {
			// Nothing here yet: the first lump defines the centre, and there is no tensor to carry.
			centreOfMass = position;
			inertia = D6Mat3.ZERO;
		}

		Vec3 shift = previousCentre.minus(centreOfMass);
		Vec3 r = position.minus(centreOfMass);

		double xx = (shift.y() * shift.y() + shift.z() * shift.z()) * previousMass
				+ (r.y() * r.y() + r.z() * r.z()) * addedMass;
		double yy = (shift.x() * shift.x() + shift.z() * shift.z()) * previousMass
				+ (r.x() * r.x() + r.z() * r.z()) * addedMass;
		double zz = (shift.x() * shift.x() + shift.y() * shift.y()) * previousMass
				+ (r.x() * r.x() + r.y() * r.y()) * addedMass;
		double xy = -shift.x() * shift.y() * previousMass - r.x() * r.y() * addedMass;
		double xz = -shift.x() * shift.z() * previousMass - r.x() * r.z() * addedMass;
		double yz = -shift.y() * shift.z() * previousMass - r.y() * r.z() * addedMass;

		inertia = inertia.plus(new D6Mat3(
				new Vec3(xx, xy, xz),
				new Vec3(xy, yy, yz),
				new Vec3(xz, yz, zz)));

		mass = previousMass + addedMass;
		if (Math.abs(mass) < MASS_EPSILON) mass = 0;
		return this;
	}

	/**
	 * Add a whole block, sampled at its centre and its eight corners.
	 *
	 * @param blockCentre the block's centre, so {@code pos + (0.5, 0.5, 0.5)} for a block position
	 */
	public D6MassProperties addBlock(Vec3 blockCentre, double blockMass) {
		double share = blockMass / 9.0;
		addPointMass(blockCentre, share);
		double d = BLOCK_SAMPLE_OFFSET;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					addPointMass(blockCentre.plus(new Vec3(sx * d, sy * d, sz * d)), share);
				}
			}
		}
		return this;
	}
}
