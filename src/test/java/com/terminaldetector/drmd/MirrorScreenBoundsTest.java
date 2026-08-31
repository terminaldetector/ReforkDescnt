package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.MirrorScreenBounds;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises {@link MirrorScreenBounds} directly — pure projection, the {@code SkirtGeometryTest} idiom. */
class MirrorScreenBoundsTest {
	private static final int W = 1920;
	private static final int H = 1080;

	/**
	 * A plain perspective matrix in the column-major layout JOML writes, looking down −Z.
	 * Built here rather than imported so the test needs no graphics library.
	 */
	private static float[] perspective(double fovYDegrees, double aspect, double near, double far) {
		double f = 1.0 / Math.tan(Math.toRadians(fovYDegrees) / 2.0);
		float[] m = new float[16];
		m[0] = (float) (f / aspect);   // col 0, row 0
		m[5] = (float) f;              // col 1, row 1
		m[10] = (float) ((far + near) / (near - far));
		m[11] = -1f;                   // col 2, row 3 — puts −z into w
		m[14] = (float) (2 * far * near / (near - far));
		return m;
	}

	@Test
	@DisplayName("tangents are unit length and perpendicular to the normal and to each other")
	void tangentsAreOrthonormal() {
		Vec3[] normals = {
				new Vec3(0, 0, 1), new Vec3(1, 0, 0), new Vec3(0, 1, 0),
				new Vec3(0, -1, 0), new Vec3(-1, 0, 0), new Vec3(0.3, 0.6, -0.74),
		};
		for (Vec3 n : normals) {
			Vec3[] t = MirrorScreenBounds.tangents(n);
			Vec3 unit = n.normalized();
			assertEquals(1.0, t[0].length(), 1e-9, "u not unit for " + n);
			assertEquals(1.0, t[1].length(), 1e-9, "v not unit for " + n);
			assertEquals(0.0, t[0].dot(unit), 1e-9, "u not perpendicular to normal " + n);
			assertEquals(0.0, t[1].dot(unit), 1e-9, "v not perpendicular to normal " + n);
			assertEquals(0.0, t[0].dot(t[1]), 1e-9, "u and v not perpendicular for " + n);
		}
	}

	@Test
	@DisplayName("a floor or ceiling mirror still gets usable tangents — the case a fixed axis collapses on")
	void verticalNormalDoesNotCollapse() {
		for (Vec3 n : new Vec3[] {new Vec3(0, 1, 0), new Vec3(0, -1, 0)}) {
			Vec3[] t = MirrorScreenBounds.tangents(n);
			assertTrue(t[0].length() > 0.99, "degenerate u for " + n);
			assertTrue(t[1].length() > 0.99, "degenerate v for " + n);
		}
	}

	@Test
	@DisplayName("face corners are square, centred, and lie in the mirror's own plane")
	void faceCornersFormASquareInPlane() {
		Vec3 centre = new Vec3(2, 3, -5);
		Vec3 normal = new Vec3(0, 0, 1);
		Vec3[] c = MirrorScreenBounds.faceCorners(centre, normal, 1.0);

		assertEquals(4, c.length);
		Vec3 sum = c[0].plus(c[1]).plus(c[2]).plus(c[3]);
		Vec3 mean = sum.scaled(0.25);
		assertEquals(centre.x(), mean.x(), 1e-9, "corners not centred");
		assertEquals(centre.y(), mean.y(), 1e-9, "corners not centred");
		assertEquals(centre.z(), mean.z(), 1e-9, "corners not centred");

		for (Vec3 p : c) {
			// In-plane: the offset from the centre has no component along the normal.
			assertEquals(0.0, p.minus(centre).dot(normal), 1e-9, "corner off the mirror plane");
			// Half-diagonal of a unit square is sqrt(2)/2.
			assertEquals(Math.sqrt(2) / 2, p.minus(centre).length(), 1e-9, "not a unit square");
		}
	}

	@Test
	@DisplayName("a face straight ahead projects to a box centred on the screen")
	void centredFaceProjectsToCentredBox() {
		float[] m = perspective(70, (double) W / H, 0.05, 1000);
		// Camera-relative: 5 blocks straight ahead, down −Z.
		Vec3[] corners = MirrorScreenBounds.faceCorners(new Vec3(0, 0, -5), new Vec3(0, 0, 1), 1.0);

		MirrorScreenBounds.Box box = MirrorScreenBounds.project(corners, m, W, H, 0);

		assertTrue(box.valid(), "should project");
		int centreX = box.x() + box.width() / 2;
		int centreY = box.y() + box.height() / 2;
		assertTrue(Math.abs(centreX - W / 2) <= 1, "box not horizontally centred: " + centreX);
		assertTrue(Math.abs(centreY - H / 2) <= 1, "box not vertically centred: " + centreY);
		assertTrue(box.width() > 0 && box.height() > 0);
		assertTrue(box.width() < W && box.height() < H, "a 1-block face 5 blocks out filled the screen");
	}

	@Test
	@DisplayName("the same face twice as far away projects to about half the size")
	void boxShrinksWithDistance() {
		float[] m = perspective(70, (double) W / H, 0.05, 1000);
		MirrorScreenBounds.Box near = MirrorScreenBounds.project(
				MirrorScreenBounds.faceCorners(new Vec3(0, 0, -5), new Vec3(0, 0, 1), 1.0), m, W, H, 0);
		MirrorScreenBounds.Box far = MirrorScreenBounds.project(
				MirrorScreenBounds.faceCorners(new Vec3(0, 0, -10), new Vec3(0, 0, 1), 1.0), m, W, H, 0);

		assertTrue(near.valid() && far.valid());
		double ratio = (double) far.width() / near.width();
		assertTrue(ratio > 0.45 && ratio < 0.55, "expected roughly half the width, ratio " + ratio);
	}

	@Test
	@DisplayName("a face crossing the eye plane reports the part in front, not nothing")
	void faceStraddlingTheEyePlaneIsClipped() {
		float[] m = perspective(70, (double) W / H, 0.05, 1000);
		// A 4-wide portal panel you have almost walked into: its plane is beside the camera, so two of
		// its corners are behind the eye and two in front. Throwing the whole face away here — which is
		// what a per-corner rejection does — makes a portal vanish exactly as you get close enough to
		// use it.
		Vec3[] corners = MirrorScreenBounds.faceCorners(new Vec3(0.1, 0, -1), new Vec3(1, 0, 0), 4.0);

		MirrorScreenBounds.Box box = MirrorScreenBounds.project(corners, m, W, H, 0);
		assertTrue(box.valid(), "a partly visible face must still report a box");
		assertTrue(box.width() > 0 && box.height() > 0, "empty box: " + box);
		// It reaches the screen edge, because an edge cut at the eye plane projects arbitrarily far.
		assertEquals(H, box.height(), "a face through the eye plane should span the screen vertically");
	}

	@Test
	@DisplayName("a face behind the camera is rejected rather than projected through infinity")
	void behindCameraIsInvalid() {
		float[] m = perspective(70, (double) W / H, 0.05, 1000);
		// +Z is behind the eye for this matrix.
		Vec3[] corners = MirrorScreenBounds.faceCorners(new Vec3(0, 0, 5), new Vec3(0, 0, 1), 1.0);

		assertFalse(MirrorScreenBounds.project(corners, m, W, H, 0).valid(),
				"a corner behind the eye must invalidate the box, not wrap around");
	}

	@Test
	@DisplayName("a face far off to the side clamps away to nothing rather than reporting a bogus box")
	void offScreenIsInvalid() {
		float[] m = perspective(70, (double) W / H, 0.05, 1000);
		Vec3[] corners = MirrorScreenBounds.faceCorners(new Vec3(500, 0, -5), new Vec3(0, 0, 1), 1.0);

		MirrorScreenBounds.Box box = MirrorScreenBounds.project(corners, m, W, H, 0);
		assertFalse(box.valid(), "far off-axis face should clamp to nothing, got " + box);
	}

	@Test
	@DisplayName("padding grows the box but never past the screen")
	void paddingGrowsAndClamps() {
		float[] m = perspective(70, (double) W / H, 0.05, 1000);
		Vec3[] corners = MirrorScreenBounds.faceCorners(new Vec3(0, 0, -5), new Vec3(0, 0, 1), 1.0);

		MirrorScreenBounds.Box tight = MirrorScreenBounds.project(corners, m, W, H, 0);
		MirrorScreenBounds.Box padded = MirrorScreenBounds.project(corners, m, W, H, 4);

		assertTrue(padded.width() >= tight.width() && padded.height() >= tight.height());
		assertTrue(padded.x() >= 0 && padded.y() >= 0);
		assertTrue(padded.x() + padded.width() <= W);
		assertTrue(padded.y() + padded.height() <= H);
	}

	@Test
	@DisplayName("malformed input is rejected instead of throwing")
	void malformedInputIsRejected() {
		float[] m = perspective(70, (double) W / H, 0.05, 1000);
		Vec3[] corners = MirrorScreenBounds.faceCorners(new Vec3(0, 0, -5), new Vec3(0, 0, 1), 1.0);

		assertFalse(MirrorScreenBounds.project(null, m, W, H, 0).valid());
		assertFalse(MirrorScreenBounds.project(corners, null, W, H, 0).valid());
		assertFalse(MirrorScreenBounds.project(corners, new float[4], W, H, 0).valid());
		assertFalse(MirrorScreenBounds.project(corners, m, 0, H, 0).valid());
		assertFalse(MirrorScreenBounds.project(corners, m, W, 0, 0).valid());
	}
}
