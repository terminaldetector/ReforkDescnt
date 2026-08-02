package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.level.WorldLevels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The column has to tile without gaps or overlaps, and every band has to be reachable. */
class WorldLevelsTest {
	@Test
	@DisplayName("bands tile the column end to end")
	void bandsTileTheColumn() {
		WorldLevels.Level[] levels = WorldLevels.Level.values();
		assertEquals(WorldLevels.WORLD_BOTTOM, levels[0].yMin);
		assertEquals(WorldLevels.WORLD_TOP, levels[levels.length - 1].yMax);
		for (int i = 0; i < levels.length - 1; i++) {
			assertEquals(levels[i].yMax, levels[i + 1].yMin,
					levels[i].name() + " and " + levels[i + 1].name() + " leave a gap");
		}
	}

	@Test
	@DisplayName("every altitude resolves to the band that contains it")
	void atMatchesBandRanges() {
		for (int y = WorldLevels.WORLD_BOTTOM; y < WorldLevels.WORLD_TOP; y += 3) {
			WorldLevels.Level band = WorldLevels.at(y);
			assertTrue(y >= band.yMin && y < band.yMax,
					"y=" + y + " resolved to " + band.name() + " [" + band.yMin + "," + band.yMax + ")");
		}
	}

	@Test
	@DisplayName("travel altitudes land inside their own band")
	void travelAltitudesAreInsideTheirBand() {
		for (WorldLevels.Level level : WorldLevels.Level.values()) {
			int y = level.travelY();
			assertTrue(y >= level.yMin && y < level.yMax,
					level.name() + " travels to " + y + ", outside [" + level.yMin + "," + level.yMax + ")");
			assertSame(level, WorldLevels.at(y), level.name() + " travel altitude resolves elsewhere");
		}
	}

	@Test
	@DisplayName("the column fits what a dimension_type can describe")
	void columnFitsDimensionLimits() {
		int height = WorldLevels.WORLD_TOP - WorldLevels.WORLD_BOTTOM;
		assertEquals(0, WorldLevels.WORLD_BOTTOM % 16, "min_y must be a multiple of 16");
		assertEquals(0, height % 16, "height must be a multiple of 16");
		assertTrue(height <= 4064, "height " + height + " exceeds the 4064 ceiling");
		assertTrue(WorldLevels.WORLD_TOP <= 2032, "min_y + height must stay under 2032");
	}

	@Test
	@DisplayName("descent shafts are on a grid that covers every region")
	void shaftGridIsReachable() {
		int found = 0;
		for (int cx = -16; cx <= 16; cx++) {
			for (int cz = -16; cz <= 16; cz++) {
				if (WorldLevels.isShaftChunk(cx, cz)) found++;
			}
		}
		// 33x33 chunks with one shaft every 8 on each axis: 5 hits per axis.
		assertEquals(25, found, "shaft spacing changed; levels may be unreachable by flight");
	}
}
