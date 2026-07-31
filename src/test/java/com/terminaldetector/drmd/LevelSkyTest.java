package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.sky.LevelSky;
import com.terminaldetector.drmd.world.level.WorldLevels;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sky has to be continuous across band boundaries and leave ordinary play alone. */
class LevelSkyTest {
	/** One step of an 8-bit colour channel. A difference below this cannot be seen. */
	private static final double COLOUR_STEP = 1.0 / 255.0;

	private static final Vec3d DAYTIME = new Vec3d(0.47, 0.65, 1.0);

	@Test
	@DisplayName("vanilla sky is untouched through the surface band")
	void surfaceKeepsVanilla() {
		for (int y = WorldLevels.INDUSTRIAL_TOP + 40; y <= WorldLevels.SURFACE_TOP - 40; y += 5) {
			Vec3d out = LevelSky.tint(DAYTIME, y);
			assertEquals(DAYTIME.x, out.x, 1e-6, "y=" + y);
			assertEquals(DAYTIME.y, out.y, 1e-6, "y=" + y);
			assertEquals(DAYTIME.z, out.z, 1e-6, "y=" + y);
		}
	}

	@Test
	@DisplayName("no visible seam anywhere in the column")
	void gradientIsContinuous() {
		Vec3d previous = null;
		double worst = 0;
		int worstY = 0;
		for (int tenths = WorldLevels.WORLD_BOTTOM * 10; tenths <= WorldLevels.WORLD_TOP * 10; tenths++) {
			double y = tenths / 10.0;
			Vec3d c = LevelSky.tint(DAYTIME, y);
			if (previous != null) {
				double step = Math.max(Math.abs(c.x - previous.x),
						Math.max(Math.abs(c.y - previous.y), Math.abs(c.z - previous.z)));
				if (step > worst) {
					worst = step;
					worstY = (int) y;
				}
			}
			previous = c;
		}
		assertTrue(worst < COLOUR_STEP,
				"largest step " + worst + " at y=" + worstY + " exceeds one colour step");
	}

	/**
	 * Boundaries must be ordinary points of the gradient, not edges.
	 *
	 * <p>Compared against the steepest one-block step found anywhere rather than against a fixed
	 * threshold: the claim being made is that nothing happens <em>at a boundary</em>, and a gradient
	 * is allowed to be as steep as it likes in the middle of a band.
	 */
	@Test
	@DisplayName("band boundaries are not special")
	void boundariesHaveNoStep() {
		double steepest = 0;
		Vec3d previous = null;
		for (int y = WorldLevels.WORLD_BOTTOM; y <= WorldLevels.WORLD_TOP; y++) {
			Vec3d c = LevelSky.tint(DAYTIME, y);
			if (previous != null) steepest = Math.max(steepest, maxChannelDelta(previous, c));
			previous = c;
		}

		int[] boundaries = {
				WorldLevels.NETHER_CEILING, WorldLevels.ABYSS_TOP, WorldLevels.INDUSTRIAL_TOP,
				WorldLevels.SURFACE_TOP, WorldLevels.SKY_TOP, WorldLevels.ORBITAL_TOP,
		};
		for (int y : boundaries) {
			double step = maxChannelDelta(LevelSky.tint(DAYTIME, y - 0.5), LevelSky.tint(DAYTIME, y + 0.5));
			assertTrue(step <= steepest + 1e-9,
					"boundary y=" + y + " steps " + step + ", steeper than anywhere else (" + steepest + ")");
		}
	}

	private static double maxChannelDelta(Vec3d a, Vec3d b) {
		return Math.max(Math.abs(a.x - b.x), Math.max(Math.abs(a.y - b.y), Math.abs(a.z - b.z)));
	}

	@Test
	@DisplayName("output stays a valid colour everywhere")
	void staysInGamut() {
		for (int y = WorldLevels.WORLD_BOTTOM - 200; y <= WorldLevels.WORLD_TOP + 200; y += 7) {
			Vec3d c = LevelSky.tint(DAYTIME, y);
			for (double ch : new double[]{c.x, c.y, c.z}) {
				assertTrue(ch >= 0.0 && ch <= 1.0, "channel " + ch + " out of range at y=" + y);
			}
		}
	}
}
