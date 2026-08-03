package com.terminaldetector.drmd.client.planet;

/**
 * How far away distant things get drawn.
 *
 * <p>The projection can only reach as far as its far plane, which at render distance 12 is some
 * seven hundred blocks — while the things worth seeing are kilometres out. So distance is
 * <em>compressed</em>: a point at true distance {@code d} is drawn at {@code g(d)}, along the same
 * line from the eye.
 *
 * <p>Scaling the whole eye-to-point vector, rather than its horizontal part, is what makes this a
 * projection instead of a distortion. A vector scaled by any positive number keeps its direction,
 * so every point stays on its own sight line: same bearing, same angle above or below the horizon.
 * The image is exactly the image the real thing would make — a tower two kilometres out leaves the
 * silhouette it would really leave, at the size it would really have. Only the sense of depth is
 * lost, which is the trade every horizon renderer makes.
 *
 * <pre>
 *   g(d) = d                                        for d ≤ inner
 *   g(d) = inner + span · (1 − e^−(d−inner)/span)   for d > inner,  span = reach − inner
 * </pre>
 *
 * <p>Two properties matter and both are deliberate. At {@code inner} the curve is the identity and
 * its slope is one, so where the map meets the real chunks there is no step and no crease — the
 * ground simply continues. And it approaches {@code reach} without ever touching it, so terrain at
 * any distance at all has somewhere to go inside the far plane, and the ordering never inverts.
 */
public final class HorizonProjection {
	private HorizonProjection() {}

	/** Share of the far plane the compressed field is allowed to fill. */
	public static final double CLIP_USE = 0.72;

	/**
	 * Nothing beyond this is sampled.
	 *
	 * <p>Not a view limit so much as an honesty limit: the curve saturates, so past a few times its
	 * span everything lands within a metre of the same drawn distance. Sampling further would spend
	 * thousands of cells to add detail that is already stacked on the horizon line.
	 */
	public static final double MAX_TRUE_RADIUS = 8_192.0;

	/** Drawn distance for a true distance. */
	public static double compress(double distance, double inner, double reach) {
		if (distance <= inner) return distance;
		double span = Math.max(1.0, reach - inner);
		return inner + span * (1.0 - Math.exp(-(distance - inner) / span));
	}

	/**
	 * Factor to multiply an eye-to-point vector by.
	 *
	 * <p>Guards the origin: a point on the eye has no direction to preserve, and dividing by its
	 * length would be a division by zero.
	 */
	public static double factor(double dx, double dy, double dz, double inner, double reach) {
		double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (d < 1.0e-4) return 1.0;
		return compress(d, inner, reach) / d;
	}
}
