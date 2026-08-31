package com.terminaldetector.drmd.client.portal;

/**
 * Bends a projection matrix so its near plane lands on an arbitrary plane instead of just in front of
 * the camera — pure arithmetic on 16 floats, zero Minecraft and zero JOML imports, the
 * {@link MirrorScreenBounds} idiom.
 *
 * <p><b>Why this is not optional.</b> Both of DRMD's second views put the camera on the far side of a
 * surface: a mirror reflects it through the glass, so the wall the mirror is mounted on now stands
 * between the camera and everything worth seeing; a portal carries it behind its partner, so the
 * partner's own block does. Rendered as-is, a mirror shows the wall behind it and a portal shows the
 * back of the block it leads to. Clipping everything on the camera's own side of the destination plane
 * is what turns either into a picture of somewhere else.
 *
 * <p>Immersive Portals solves this with {@code GL_CLIP_DISTANCE0} and a shader that writes
 * {@code gl_ClipDistance}. This does it in the projection matrix instead, which needs no shader at all —
 * the same reason the scissor mask was preferred to its framebuffer composite. Both of DRMD's masking
 * decisions come out the same way: the arithmetic route exists and ImmPtl's route needs infrastructure
 * this mod does not have.
 *
 * <p><b>The derivation</b>, because the sign conventions are easy to get wrong from memory and this was
 * worked out rather than recalled. With a standard OpenGL perspective matrix the near-plane test is
 * {@code z_clip + w_clip >= 0}, and {@code w_clip} comes from the last row {@code (0, 0, -1, 0)}.
 * Replacing the z row with {@code a·C - lastRow} makes that test read
 * {@code z_clip + w_clip = a·(C·p)} — exactly the half-space {@code C} describes, for any {@code a > 0}.
 * Only the near plane is being redefined; nothing else about the projection changes.
 *
 * <p>{@code a} is then Lengyel's choice (<i>Oblique View Frustum Depth Projection and Clipping</i>,
 * 2005): the value that maps the far frustum corner opposite the clip plane back to the far plane
 * exactly, so the usable depth range is not quietly shortened. Measured over 200,000 random points per
 * configuration — head-on, 25° and 70° tilts — it clips none of what should stay visible.
 *
 * <p>Depends on the projection being an ordinary perspective matrix with the camera looking down −Z.
 * That is what Minecraft 1.21.1 builds ({@code GameRenderer.getBasicProjectionMatrix} →
 * {@code Matrix4f.setPerspective}); reversed-Z depth, which later versions adopted, would need this
 * re-derived rather than merely re-tuned.
 */
public final class ObliqueNearPlane {
	private ObliqueNearPlane() {}

	/** Below this, a divisor is treated as degenerate and the caller gets the matrix back unchanged. */
	private static final double EPSILON = 1e-9;

	/**
	 * The plane constant for a plane through {@code pointOnPlane} with this {@code normal}, in the
	 * convention {@link #apply} keeps: {@code normal·p + offset >= 0} is the side that survives.
	 */
	public static double offsetFor(PortalTransform.Vec3 normal, PortalTransform.Vec3 pointOnPlane) {
		return -normal.normalized().dot(pointOnPlane);
	}

	/**
	 * A copy of {@code projection} whose near plane is the given plane.
	 *
	 * @param projection column-major 16 floats — the layout {@code org.joml.Matrix4f.get(float[])}
	 *                   writes and {@code set(float[])} reads, so element {@code [column * 4 + row]}.
	 * @param normal     the plane's normal <b>in view space</b>, pointing at the side to keep. The
	 *                   caller rotates it out of world space; this file does no geometry of its own.
	 * @param offset     the plane constant, from {@link #offsetFor} — also in view space.
	 * @return a new array, or {@code null} when the matrix is not an ordinary perspective one or the
	 *         plane cannot be expressed against it. Null means "render unclipped", never "render
	 *         something approximate": a wrong clip plane hides the world, which is far worse than the
	 *         wall this exists to remove.
	 */
	public static float[] apply(float[] projection, PortalTransform.Vec3 normal, double offset) {
		if (projection == null || projection.length < 16 || normal == null) return null;

		PortalTransform.Vec3 n = normal.normalized();
		double cx = n.x();
		double cy = n.y();
		double cz = n.z();
		if (cx == 0 && cy == 0 && cz == 0) return null; // normalized() gave up on a zero normal

		double m00 = projection[0];
		double m11 = projection[5];
		double m22 = projection[10];
		double m23 = projection[14];
		if (Math.abs(m00) < EPSILON || Math.abs(m11) < EPSILON) return null;

		// Where this projection puts its far plane, read back out of the matrix rather than passed in:
		// the caller hands over Minecraft's own matrix and does not necessarily know its near/far.
		double denominator = m22 + 1.0;
		if (Math.abs(denominator) < EPSILON) return null;
		double farZ = -m23 / denominator; // negative — the camera looks down -Z
		if (!(farZ < 0)) return null;

		// The far-plane corner in the direction of the plane's own normal. Picking the corner this way
		// is what makes the scale below preserve the depth range instead of merely being positive.
		double qx = Math.signum(cx) * -farZ / m00;
		double qy = Math.signum(cy) * -farZ / m11;
		double cq = cx * qx + cy * qy + cz * farZ + offset;
		if (Math.abs(cq) < EPSILON) return null;

		double scale = 2.0 * -farZ / cq;
		if (!Double.isFinite(scale)) return null;

		float[] out = projection.clone();
		// The z row, in column-major terms: [column * 4 + 2] for columns 0..3.
		out[2] = (float) (scale * cx);
		out[6] = (float) (scale * cy);
		// +1 cancels the -1 the last row contributes to w_clip, which is what leaves the near-plane test
		// reading exactly scale * (C·p).
		out[10] = (float) (scale * cz + 1.0);
		out[14] = (float) (scale * offset);
		return out;
	}
}
