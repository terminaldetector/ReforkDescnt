package com.terminaldetector.drmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors the six box faces {@code ProjectileRenderer.drawBox} / {@code WeaponViewRenderer.drawBox}
 * (and the five other hand-built-cube renderers touched by the same fix) all emit, plus the one
 * camera-facing quad {@code ProjectileRenderer.billboard} emits — every {@code MESH_BOLT}/
 * {@code MESH_ORB} round's entire visible shape, and the gap left when the box fix shipped without
 * it: a billboard is one hand-emitted quad through the same unverified culling convention as a cube
 * face, just one face instead of six, and a wrong winding there does not read as "one face missing,"
 * it reads as "no projectiles" — every laser and plasma round draws nothing at all.
 * ({@code VertexConsumer}/{@code MatrixStack.Entry} are unavailable here, same mirroring approach as
 * {@code TunnelCarvingCapsuleTest}). {@code quad()}/{@code billboard()}'s fix for the unresolved
 * {@code getEntitySolid}/{@code getEntityTranslucent} culling question emits every face in both
 * winding orders ({@code v0,v1,v2,v3} then {@code v0,v3,v2,v1}). This pins that the two orderings
 * really are exact opposites (not two accidental copies of the same thing), and that of the two, the
 * original {@code v0,v1,v2,v3} order — the one every touched renderer already used before the
 * reversed copy was added alongside it — is the one that actually points outward.
 */
class ProjectileBoxWindingTest {
	private static double[] sub(double[] a, double[] b) {
		return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[]{
				a[1] * b[2] - a[2] * b[1],
				a[2] * b[0] - a[0] * b[2],
				a[0] * b[1] - a[1] * b[0]
		};
	}

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static double length(double[] a) {
		return Math.sqrt(dot(a, a));
	}

	/** Mirrors quad()'s own normal-from-corners shape: cross of the first two edges from v0. */
	private static double[] faceNormal(double[] v0, double[] v1, double[] v2) {
		return cross(sub(v1, v0), sub(v2, v0));
	}

	private record Face(double[] v0, double[] v1, double[] v2, double[] v3, double[] expectedOutward) {}

	/** The six faces of drawBox(hx=1, hy=2, hz=3) — deliberately unequal extents — in the exact
	 *  order and per-face vertex layout the renderers emit them. */
	private static Face[] boxFaces() {
		double x0 = -1, y0 = -2, z0 = -3, x1 = 1, y1 = 2, z1 = 3;
		return new Face[]{
				new Face(new double[]{x0, y0, z0}, new double[]{x1, y0, z0}, new double[]{x1, y0, z1}, new double[]{x0, y0, z1}, new double[]{0, -1, 0}),
				new Face(new double[]{x0, y1, z0}, new double[]{x0, y1, z1}, new double[]{x1, y1, z1}, new double[]{x1, y1, z0}, new double[]{0, 1, 0}),
				new Face(new double[]{x0, y0, z0}, new double[]{x0, y1, z0}, new double[]{x1, y1, z0}, new double[]{x1, y0, z0}, new double[]{0, 0, -1}),
				new Face(new double[]{x0, y0, z1}, new double[]{x1, y0, z1}, new double[]{x1, y1, z1}, new double[]{x0, y1, z1}, new double[]{0, 0, 1}),
				new Face(new double[]{x0, y0, z0}, new double[]{x0, y0, z1}, new double[]{x0, y1, z1}, new double[]{x0, y1, z0}, new double[]{-1, 0, 0}),
				new Face(new double[]{x1, y0, z0}, new double[]{x1, y1, z0}, new double[]{x1, y1, z1}, new double[]{x1, y0, z1}, new double[]{1, 0, 0}),
		};
	}

	/** {@code billboard(len=5, wide=2)}'s single camera-facing quad — normal points at the viewer, +Z. */
	private static Face billboardFace() {
		float len = 5, wide = 2;
		return new Face(new double[]{-len, -wide, 0}, new double[]{len, -wide, 0},
				new double[]{len, wide, 0}, new double[]{-len, wide, 0}, new double[]{0, 0, 1});
	}

	/** Every hand-emitted face this fix covers: the six box faces plus the one billboard quad. */
	private static Face[] allFaces() {
		Face[] box = boxFaces();
		Face[] all = Arrays.copyOf(box, box.length + 1);
		all[box.length] = billboardFace();
		return all;
	}

	@Test
	@DisplayName("reversing a face's winding (v0,v3,v2,v1) flips its normal to the exact opposite")
	void reversedWindingIsAntiparallel() {
		for (Face f : allFaces()) {
			double[] forward = faceNormal(f.v0(), f.v1(), f.v2());
			double[] reversed = faceNormal(f.v0(), f.v3(), f.v2());
			double cosAngle = dot(forward, reversed) / (length(forward) * length(reversed));
			assertEquals(-1.0, cosAngle, 1e-9,
					"reversed winding should point exactly opposite the original for the face toward "
							+ Arrays.toString(f.expectedOutward()));
		}
	}

	@Test
	@DisplayName("the original v0,v1,v2,v3 order already points outward, before any reversed copy is added")
	void originalOrderIsOutward() {
		for (Face f : allFaces()) {
			double[] forward = faceNormal(f.v0(), f.v1(), f.v2());
			double[] unit = {forward[0] / length(forward), forward[1] / length(forward), forward[2] / length(forward)};
			assertEquals(1.0, dot(unit, f.expectedOutward()), 1e-9,
					"face toward " + Arrays.toString(f.expectedOutward()) + " should already wind outward");
		}
	}

	@Test
	@DisplayName("exactly one of the two emitted orderings matches the true outward normal — never both, never neither")
	void exactlyOneOrderingIsOutward() {
		for (Face f : allFaces()) {
			double[] forward = faceNormal(f.v0(), f.v1(), f.v2());
			double[] reversed = faceNormal(f.v0(), f.v3(), f.v2());
			boolean forwardMatches = dot(forward, f.expectedOutward()) > 0;
			boolean reversedMatches = dot(reversed, f.expectedOutward()) > 0;
			assertTrue(forwardMatches ^ reversedMatches,
					"exactly one winding should match the true outward normal for the face toward "
							+ Arrays.toString(f.expectedOutward()));
		}
	}
}
