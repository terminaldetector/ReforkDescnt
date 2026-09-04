package com.terminaldetector.drmd.d6;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;

/**
 * A 3×3 matrix of doubles, stored as its three rows.
 *
 * <p>Exists because an inertia tensor is one and DRMD had none. Deliberately small: this is what
 * {@link D6PhysicsBody} needs to turn a torque into an angular acceleration and nothing else, so it
 * has no LU decomposition, no eigenvalues and no general linear algebra ambitions.
 *
 * <p><b>Column-vector convention</b>, {@code M · v}, which is what the physics literature and the
 * {@code R I Rᵀ} below both assume. Note this is the opposite of the row-vector convention the
 * vendored {@link com.terminaldetector.drmd.vendor.immptl.ImmPtlIntMatrix3} states for itself; both
 * are ordinary, and mixing them silently is how a rotation ends up inverted.
 *
 * <p>Pure — no Minecraft, no JOML — so it is testable the way the rest of DRMD's geometry is.
 */
public record D6Mat3(Vec3 row0, Vec3 row1, Vec3 row2) {

	public static final D6Mat3 ZERO = new D6Mat3(new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(0, 0, 0));
	public static final D6Mat3 IDENTITY =
			new D6Mat3(new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1));

	/** A diagonal matrix — what an inertia tensor is when the body's own axes are its principal axes. */
	public static D6Mat3 diagonal(double xx, double yy, double zz) {
		return new D6Mat3(new Vec3(xx, 0, 0), new Vec3(0, yy, 0), new Vec3(0, 0, zz));
	}

	/**
	 * A matrix from its three columns.
	 *
	 * <p>The columns of a rotation matrix are where it sends each basis vector, which is how
	 * {@link #rotationOf} builds one out of a quaternion.
	 */
	public static D6Mat3 fromColumns(Vec3 c0, Vec3 c1, Vec3 c2) {
		return new D6Mat3(
				new Vec3(c0.x(), c1.x(), c2.x()),
				new Vec3(c0.y(), c1.y(), c2.y()),
				new Vec3(c0.z(), c1.z(), c2.z()));
	}

	/** The rotation matrix for a unit quaternion. */
	public static D6Mat3 rotationOf(Quat rotation) {
		return fromColumns(
				rotation.rotate(new Vec3(1, 0, 0)),
				rotation.rotate(new Vec3(0, 1, 0)),
				rotation.rotate(new Vec3(0, 0, 1)));
	}

	/** {@code this · v}. */
	public Vec3 transform(Vec3 v) {
		return new Vec3(row0.dot(v), row1.dot(v), row2.dot(v));
	}

	/** {@code this · other} — the right factor applies first, as the column-vector convention says. */
	public D6Mat3 multiply(D6Mat3 other) {
		D6Mat3 t = other.transposed();
		return new D6Mat3(
				new Vec3(row0.dot(t.row0), row0.dot(t.row1), row0.dot(t.row2)),
				new Vec3(row1.dot(t.row0), row1.dot(t.row1), row1.dot(t.row2)),
				new Vec3(row2.dot(t.row0), row2.dot(t.row1), row2.dot(t.row2)));
	}

	public D6Mat3 transposed() {
		return new D6Mat3(
				new Vec3(row0.x(), row1.x(), row2.x()),
				new Vec3(row0.y(), row1.y(), row2.y()),
				new Vec3(row0.z(), row1.z(), row2.z()));
	}

	public D6Mat3 scaled(double factor) {
		return new D6Mat3(row0.scaled(factor), row1.scaled(factor), row2.scaled(factor));
	}

	public D6Mat3 plus(D6Mat3 other) {
		return new D6Mat3(row0.plus(other.row0), row1.plus(other.row1), row2.plus(other.row2));
	}

	public double determinant() {
		return row0.dot(row1.cross(row2));
	}

	/**
	 * The inverse, or null when there isn't one.
	 *
	 * <p>Null rather than an exception, and the caller has to decide: a body whose inertia tensor is
	 * singular has all its mass on a line or at a point, which is a legitimate state to be in briefly
	 * (one block, nothing yet) and not one to crash on. {@link D6PhysicsBody} treats it as "cannot be
	 * turned about that axis" and leaves the angular velocity alone.
	 */
	public D6Mat3 inverse() {
		double det = determinant();
		if (Math.abs(det) < 1e-12) return null;
		// Adjugate over determinant, written as three cross products rather than nine cofactors.
		//
		// They are the adjugate's COLUMNS, not its rows — which is worth stating because for a
		// symmetric matrix the two are identical, an inertia tensor is always symmetric, and so the
		// wrong one would have worked everywhere this class is currently used and been wrong for the
		// first caller that inverted anything else. Checked against 200 random matrices.
		Vec3 a = row1.cross(row2);
		Vec3 b = row2.cross(row0);
		Vec3 c = row0.cross(row1);
		return fromColumns(a, b, c).scaled(1.0 / det);
	}

	/**
	 * This tensor expressed in world axes, given the body's rotation: {@code R · this · Rᵀ}.
	 *
	 * <p>Recomputed every step, because {@code R} changes every step. The tensor itself is stored in
	 * the body's own axes precisely so that it does not have to be.
	 */
	public D6Mat3 rotatedBy(Quat rotation) {
		D6Mat3 r = rotationOf(rotation);
		return r.multiply(this).multiply(r.transposed());
	}
}
