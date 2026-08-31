package com.terminaldetector.drmd.client.portal;

/**
 * Where a mirror's own face lands on screen, in pixels — pure arithmetic, zero Minecraft and zero JOML
 * imports, same idiom as {@link PortalTransform}: the caller hands over the matrix and the camera
 * offset already sampled, this file only projects.
 *
 * <p>Exists so the reflection can be clipped to the mirror instead of covering the whole screen. The
 * exact-shape composite needs a custom shader (see {@code docs/PORTAL_RENDERING.md}); a scissor
 * rectangle needs none, because the reflection is still drawn as a full-screen blit and only the
 * <em>clip</em> changes — so every pixel keeps the screen position it was rendered at and no texture
 * coordinates are involved at all. That sidesteps the whole reason ImmPtl needs a shader here: per-vertex
 * screen-space UVs get interpolated perspective-correctly across a quad and come out distorted.
 *
 * <p>Known gap, named rather than hidden: a rectangle is not the mirror's shape. Seen head-on the two
 * nearly coincide; seen at a steep angle the box is larger than the face and the reflection spills past
 * its edges. That is the price of not having a shader yet, and it is still a large step in from covering
 * the entire view.
 */
public final class MirrorScreenBounds {
	private MirrorScreenBounds() {}

	/**
	 * A pixel rectangle in OpenGL's bottom-left origin, ready for a scissor call.
	 *
	 * @param valid false when there is no box to report — the face is entirely at or behind the eye
	 *              plane, or it lands wholly off-screen. The caller must fall back rather than clip to
	 *              garbage. A face only <em>partly</em> in front of the eye is valid, and reports the
	 *              part that is.
	 */
	public record Box(int x, int y, int width, int height, boolean valid) {
		public static final Box INVALID = new Box(0, 0, 0, 0, false);
	}

	/**
	 * Two orthonormal vectors spanning the plane with this unit {@code normal} — the mirror face's own
	 * axes, so its corners can be walked without the caller knowing which way the block faces.
	 *
	 * <p>Picks the world axis least parallel to {@code normal} to cross against, so the first tangent
	 * is never near-zero: crossing with a fixed axis would collapse for a floor or ceiling mirror,
	 * which in a 6DoF game is not an edge case but an ordinary Tuesday.
	 */
	public static PortalTransform.Vec3[] tangents(PortalTransform.Vec3 normal) {
		PortalTransform.Vec3 n = normal.normalized();
		PortalTransform.Vec3 reference = Math.abs(n.y()) < 0.9
				? new PortalTransform.Vec3(0, 1, 0)
				: new PortalTransform.Vec3(1, 0, 0);
		PortalTransform.Vec3 u = reference.cross(n).normalized();
		PortalTransform.Vec3 v = n.cross(u).normalized();
		return new PortalTransform.Vec3[] {u, v};
	}

	/**
	 * The four corners of a square face of side {@code size}, centred on {@code centre}.
	 *
	 * @param centre the face centre in whatever space the caller will project from — for DRMD's mirror
	 *               that is {@code MirrorScanner.MirrorFace.planePoint} made camera-relative.
	 */
	public static PortalTransform.Vec3[] faceCorners(PortalTransform.Vec3 centre,
			PortalTransform.Vec3 normal, double size) {
		PortalTransform.Vec3[] t = tangents(normal);
		double h = size / 2.0;
		PortalTransform.Vec3 a = t[0].scaled(h);
		PortalTransform.Vec3 b = t[1].scaled(h);
		// Around the square, not across it: {@link #project} clips these as a polygon, and a Z-shaped
		// order would have it clipping the diagonals.
		return new PortalTransform.Vec3[] {
				centre.plus(a).plus(b),
				centre.plus(a).minus(b),
				centre.minus(a).minus(b),
				centre.minus(a).plus(b),
		};
	}

	/**
	 * How close to the eye plane a point may be and still be projected. A vertex exactly on it divides
	 * by zero; a hair in front of it projects enormously far off-screen, which is the honest answer and
	 * what {@link #PIXEL_LIMIT} then bounds.
	 */
	private static final double W_EPSILON = 1e-6;

	/**
	 * Pixel coordinates are clamped to this before being rounded to {@code int}.
	 *
	 * <p>Not tidiness. An edge cut at the eye plane projects to something like 10^9 pixels, and Java's
	 * double-to-int conversion saturates at {@code Integer.MAX_VALUE} — after which subtracting the pad
	 * overflows and wraps to a large negative, turning "far off to the right" into "far off to the
	 * left". Clamping first keeps every later step in ordinary arithmetic.
	 */
	private static final double PIXEL_LIMIT = 1e7;

	private static double pixelX(double clipX, double clipW, int screenWidth) {
		// GL's window origin is bottom-left, which is also what a scissor call expects, so neither axis
		// is flipped the way a GUI-space conversion would.
		return clamp((clipX / clipW * 0.5 + 0.5) * screenWidth);
	}

	private static double pixelY(double clipY, double clipW, int screenHeight) {
		return clamp((clipY / clipW * 0.5 + 0.5) * screenHeight);
	}

	private static double clamp(double pixel) {
		if (pixel < -PIXEL_LIMIT) return -PIXEL_LIMIT;
		if (pixel > PIXEL_LIMIT) return PIXEL_LIMIT;
		return pixel;
	}

	/**
	 * Project points through a combined view-projection matrix and take their pixel bounding box.
	 *
	 * @param matrix column-major 16 floats, the layout {@code org.joml.Matrix4f.get(float[])} writes —
	 *               so element {@code [column * 4 + row]}. Taken as a raw array rather than a
	 *               {@code Matrix4f} to keep this class testable without a graphics library.
	 * @param screenWidth  framebuffer width in pixels
	 * @param screenHeight framebuffer height in pixels
	 * @param pad          pixels to grow the box by on every side, to cover rounding at the edges
	 *
	 * <p>{@code points} are treated as a polygon in order, and one crossing the eye plane is cut there
	 * rather than thrown away — see the loop for why that matters more than it sounds.
	 */
	public static Box project(PortalTransform.Vec3[] points, float[] matrix,
			int screenWidth, int screenHeight, int pad) {
		if (points == null || points.length == 0 || matrix == null || matrix.length < 16) {
			return Box.INVALID;
		}
		if (screenWidth <= 0 || screenHeight <= 0) return Box.INVALID;

		int n = points.length;
		double[] cx = new double[n];
		double[] cy = new double[n];
		double[] cw = new double[n];
		for (int i = 0; i < n; i++) {
			double x = points[i].x();
			double y = points[i].y();
			double z = points[i].z();
			cx[i] = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
			cy[i] = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
			cw[i] = matrix[3] * x + matrix[7] * y + matrix[11] * z + matrix[15];
		}

		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		boolean any = false;

		// Walk the polygon's edges, keeping what is in front of the eye plane and cutting each edge
		// where it crosses. Rejecting the whole face instead — which is what this did — meant a portal
		// disappeared exactly as you got close enough to walk through it, since a wide face straddles
		// the eye plane long before you reach it. Clip-space coordinates are linear along an edge, so
		// the cut point is exact rather than an approximation.
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			boolean insideI = cw[i] > W_EPSILON;
			boolean insideJ = cw[j] > W_EPSILON;
			if (insideI) {
				double px = pixelX(cx[i], cw[i], screenWidth);
				double py = pixelY(cy[i], cw[i], screenHeight);
				if (px < minX) minX = px;
				if (px > maxX) maxX = px;
				if (py < minY) minY = py;
				if (py > maxY) maxY = py;
				any = true;
			}
			if (insideI != insideJ) {
				double t = (W_EPSILON - cw[i]) / (cw[j] - cw[i]);
				double ix = cx[i] + t * (cx[j] - cx[i]);
				double iy = cy[i] + t * (cy[j] - cy[i]);
				double px = pixelX(ix, W_EPSILON, screenWidth);
				double py = pixelY(iy, W_EPSILON, screenHeight);
				if (px < minX) minX = px;
				if (px > maxX) maxX = px;
				if (py < minY) minY = py;
				if (py > maxY) maxY = py;
				any = true;
			}
		}
		// Nothing survived: the face is entirely at or behind the eye plane.
		if (!any) return Box.INVALID;

		int x0 = (int) Math.floor(minX) - pad;
		int y0 = (int) Math.floor(minY) - pad;
		int x1 = (int) Math.ceil(maxX) + pad;
		int y1 = (int) Math.ceil(maxY) + pad;

		// Clamp to the screen: a scissor box reaching outside it is not an error, but clamping keeps
		// the reported width/height honest for anything that reasons about coverage.
		if (x0 < 0) x0 = 0;
		if (y0 < 0) y0 = 0;
		if (x1 > screenWidth) x1 = screenWidth;
		if (y1 > screenHeight) y1 = screenHeight;

		if (x1 <= x0 || y1 <= y0) return Box.INVALID; // entirely off-screen
		return new Box(x0, y0, x1 - x0, y1 - y0, true);
	}
}
