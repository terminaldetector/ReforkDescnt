package com.terminaldetector.drmd.client.sky;

import com.terminaldetector.drmd.world.level.WorldLevels;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Sky and fog colour as one continuous function of altitude.
 *
 * <p>The Nether and the End are bands of a single column here rather than separate dimensions, so
 * there is no loading screen to hide a change of sky behind — you fly across the boundary and the
 * sky has to come with you. Anchoring a colour to each band and switching at the boundary would put
 * a visible seam exactly where the player is looking; instead the anchors sit at band <em>centres</em>
 * and the colour is interpolated between whichever two you are between. That makes the function
 * continuous everywhere by construction, with no special case at a boundary.
 *
 * <p>Surface is anchored at weight 0 rather than to a colour, so the whole of ordinary play keeps
 * vanilla's sky untouched — day, night, weather and biome tint all still work. The tint only takes
 * hold as you climb or dive out of it.
 */
public final class LevelSky {
	/** An altitude with a colour and how strongly it displaces vanilla there. */
	private record Anchor(double y, float r, float g, float b, float weight) {}

	private static final Anchor[] ANCHORS = {
			// Ember light off lava seas under a glowstone ceiling.
			new Anchor(mid(WorldLevels.WORLD_BOTTOM, WorldLevels.NETHER_CEILING), 0.34f, 0.09f, 0.05f, 1.00f),
			// The open drop between levels: almost no light reaches it.
			new Anchor(mid(WorldLevels.NETHER_CEILING, WorldLevels.ABYSS_TOP), 0.02f, 0.025f, 0.045f, 1.00f),
			// Industrial depth — gunmetal haze off the complexes.
			new Anchor(mid(WorldLevels.ABYSS_TOP, WorldLevels.INDUSTRIAL_TOP), 0.09f, 0.10f, 0.12f, 0.85f),
			// Surface is a plateau at weight zero, not a single point.
			//
			// Anchored only at the band's midpoint, the tint was exactly vanilla at y=180 and blended
			// away from it in both directions — which put sea level, where the game is actually
			// played, 0.51 of the way to industrial grey. Pinning both edges keeps the entire band
			// untouched and moves the whole ramp outside it.
			new Anchor(WorldLevels.INDUSTRIAL_TOP, 0f, 0f, 0f, 0.00f),
			new Anchor(WorldLevels.SURFACE_TOP, 0f, 0f, 0f, 0.00f),
			// Thin air over the archipelago — paler and colder than sea level.
			new Anchor(mid(WorldLevels.SURFACE_TOP, WorldLevels.SKY_TOP), 0.33f, 0.52f, 0.76f, 0.70f),
			// Orbital: near vacuum, the sky drains to black.
			new Anchor(mid(WorldLevels.SKY_TOP, WorldLevels.ORBITAL_TOP), 0.015f, 0.02f, 0.07f, 1.00f),
			// Upper column — stock-like empty high sky (no End violet when END_BAND is parked).
			new Anchor(mid(WorldLevels.ORBITAL_TOP, WorldLevels.WORLD_TOP), 0.015f, 0.02f, 0.07f, 1.00f),
	};

	private LevelSky() {}

	private static double mid(int a, int b) {
		return (a + b) / 2.0;
	}

	/**
	 * Blend a vanilla sky colour toward the band tint for this altitude.
	 *
	 * <p>Below the lowest anchor and above the highest the value is held rather than extrapolated,
	 * so the ends of the column are flat instead of running off into out-of-range colour.
	 */
	public static Vec3d tint(Vec3d vanilla, double y) {
		// Ending 2 — absolute void: no sun, no moon, no band colour. Just black.
		if (com.terminaldetector.drmd.client.DescentClientState.isVoidEnding()) {
			return new Vec3d(0.0, 0.0, 0.0);
		}
		Anchor lo = ANCHORS[0];
		Anchor hi = ANCHORS[ANCHORS.length - 1];
		if (y <= lo.y()) return blend(vanilla, lo, 1.0);
		if (y >= hi.y()) return blend(vanilla, hi, 1.0);

		for (int i = 0; i < ANCHORS.length - 1; i++) {
			Anchor a = ANCHORS[i];
			Anchor b = ANCHORS[i + 1];
			if (y < a.y() || y > b.y()) continue;
			double t = (y - a.y()) / (b.y() - a.y());
			// Smoothstep rather than linear: the rate of change goes to zero at each anchor, so
			// crossing one has no visible kink even when the two colours pull in opposite directions.
			t = t * t * (3 - 2 * t);
			float r = MathHelper.lerp((float) t, a.r(), b.r());
			float g = MathHelper.lerp((float) t, a.g(), b.g());
			float bl = MathHelper.lerp((float) t, a.b(), b.b());
			float w = MathHelper.lerp((float) t, a.weight(), b.weight());
			return mix(vanilla, r, g, bl, w);
		}
		return vanilla;
	}

	private static Vec3d blend(Vec3d vanilla, Anchor a, double t) {
		return mix(vanilla, a.r(), a.g(), a.b(), (float) (a.weight() * t));
	}

	private static Vec3d mix(Vec3d vanilla, float r, float g, float b, float w) {
		if (w <= 1e-4f) return vanilla;
		return new Vec3d(
				MathHelper.lerp(w, vanilla.x, r),
				MathHelper.lerp(w, vanilla.y, g),
				MathHelper.lerp(w, vanilla.z, b));
	}
}
