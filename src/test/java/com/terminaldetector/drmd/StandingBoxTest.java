package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.gravity.GravityMount;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The clearance volume a standing body needs under local gravity.
 *
 * <p>This is the test that decides whether vanilla may fold a player into the crawl pose. It used to
 * measure 1.8 blocks up the world Y whatever way the body was pointing, so a pilot stood on a wall
 * was asked for headroom above someone lying sideways; in a corridor there is none, the crawl was
 * allowed, and once crawling the body is shorter still and the answer never changed back.
 */
class StandingBoxTest {
	private static final double HALF_WIDTH = 0.3;   // player
	private static final double HEIGHT = 1.8;

	private static Box box(Vec3d up) {
		return GravityMount.standingBox(new Vec3d(10, 64, -7), up, HALF_WIDTH, HEIGHT);
	}

	@Test
	@DisplayName("on a level floor the box is exactly vanilla's")
	void levelFloorIsUnchanged() {
		Box b = box(new Vec3d(0, 1, 0));
		assertEquals(10 - HALF_WIDTH, b.minX, 1e-9);
		assertEquals(10 + HALF_WIDTH, b.maxX, 1e-9);
		assertEquals(64.0, b.minY, 1e-9, "feet sit on the floor, not below it");
		assertEquals(64 + HEIGHT, b.maxY, 1e-9);
		assertEquals(-7 - HALF_WIDTH, b.minZ, 1e-9);
		assertEquals(-7 + HALF_WIDTH, b.maxZ, 1e-9);
	}

	@Test
	@DisplayName("on a wall the body lies along the wall's up, not the world's")
	void wallMountLiesSideways() {
		Box b = box(new Vec3d(1, 0, 0));
		assertEquals(HEIGHT, b.maxX - b.minX, 1e-9, "the long axis follows local up");
		assertEquals(2 * HALF_WIDTH, b.maxY - b.minY, 1e-9, "and world Y only needs the body's width");
		assertEquals(2 * HALF_WIDTH, b.maxZ - b.minZ, 1e-9);
		assertTrue(b.maxY - b.minY < HEIGHT,
				"a wall-mounted pilot must not be asked for a standing height of headroom");
	}

	@Test
	@DisplayName("on a ceiling the body hangs down from the feet")
	void ceilingMountHangsDown() {
		Box b = box(new Vec3d(0, -1, 0));
		assertEquals(64 - HEIGHT, b.minY, 1e-9);
		assertEquals(64.0, b.maxY, 1e-9);
		assertEquals(2 * HALF_WIDTH, b.maxX - b.minX, 1e-9);
	}

	@Test
	@DisplayName("the volume is the same size whichever way the body points")
	void volumeIsOrientationIndependentOnAxes() {
		double want = 2 * HALF_WIDTH * 2 * HALF_WIDTH * HEIGHT;
		for (Vec3d up : new Vec3d[]{
				new Vec3d(0, 1, 0), new Vec3d(0, -1, 0),
				new Vec3d(1, 0, 0), new Vec3d(-1, 0, 0),
				new Vec3d(0, 0, 1), new Vec3d(0, 0, -1)}) {
			Box b = box(up);
			double v = (b.maxX - b.minX) * (b.maxY - b.minY) * (b.maxZ - b.minZ);
			assertEquals(want, v, 1e-9, "axis-aligned mount " + up + " changed the body's size");
		}
	}

	@Test
	@DisplayName("a diagonal mount stays bounded between the two axis cases")
	void diagonalMountIsBounded() {
		Box b = box(new Vec3d(1, 1, 0).normalize());
		double dy = b.maxY - b.minY;
		assertTrue(dy > 2 * HALF_WIDTH && dy < HEIGHT + 2 * HALF_WIDTH,
				"diagonal Y extent " + dy + " is outside the flat and upright cases");
		assertTrue(b.maxX - b.minX > 2 * HALF_WIDTH, "and it must lean along X too");
	}

	@Test
	@DisplayName("an unusable up falls back to standing upright rather than to nothing")
	void degenerateUpIsUpright() {
		Box b = GravityMount.standingBox(new Vec3d(0, 64, 0), Vec3d.ZERO, HALF_WIDTH, HEIGHT);
		assertEquals(HEIGHT, b.maxY - b.minY, 1e-9);
	}
}
