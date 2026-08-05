package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.level.NetherRelief;
import com.terminaldetector.drmd.world.level.WorldLevels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Nether band's floor and ceiling shape.
 *
 * <p>The continuity cases are the ones that matter. The band is written a chunk at a time by a
 * background stream, so neighbouring chunks are built on different ticks — anything that made the
 * height depend on which chunk asked would put a cliff on every chunk border, which is the seam the
 * column exists to not have.
 */
class NetherReliefTest {
	private static final long SEED = 0x5EED_1234L;

	@Test
	@DisplayName("height depends on world position only, never on the chunk that asked")
	void heightIsAPureFunctionOfPosition() {
		for (int x = -40; x <= 40; x += 7) {
			for (int z = -40; z <= 40; z += 7) {
				assertEquals(NetherRelief.floorTop(SEED, x, z), NetherRelief.floorTop(SEED, x, z),
						"floor must be stable for repeated queries at " + x + "," + z);
				assertEquals(NetherRelief.ceilingBottom(SEED, x, z),
						NetherRelief.ceilingBottom(SEED, x, z));
			}
		}
	}

	@Test
	@DisplayName("no step at a chunk border")
	void chunkBordersAreSeamless() {
		// 15 → 16 is a chunk edge; the two columns are written by different jobs.
		for (int z = -64; z <= 64; z++) {
			int a = NetherRelief.floorTop(SEED, 15, z);
			int b = NetherRelief.floorTop(SEED, 16, z);
			assertTrue(Math.abs(a - b) <= 2,
					"floor jumps " + (b - a) + " blocks across the chunk edge at z=" + z);
			int ca = NetherRelief.ceilingBottom(SEED, 15, z);
			int cb = NetherRelief.ceilingBottom(SEED, 16, z);
			assertTrue(Math.abs(ca - cb) <= 2,
					"ceiling jumps " + (cb - ca) + " blocks across the chunk edge at z=" + z);
		}
	}

	@Test
	@DisplayName("neighbouring columns never step more than a block or two")
	void reliefIsWalkable() {
		int worst = 0;
		for (int x = -80; x <= 80; x++) {
			for (int z = -80; z <= 80; z += 3) {
				worst = Math.max(worst,
						Math.abs(NetherRelief.floorTop(SEED, x, z) - NetherRelief.floorTop(SEED, x + 1, z)));
			}
		}
		assertTrue(worst <= 3, "worst neighbouring floor step was " + worst + " blocks — that is a wall");
	}

	@Test
	@DisplayName("floor and ceiling stay inside the band and never meet")
	void bandStaysOpen() {
		int floorBase = WorldLevels.NETHER_FLOOR + WorldLevels.NETHER_FLOOR_THICKNESS;
		int ceilBase = WorldLevels.NETHER_CEILING - WorldLevels.NETHER_CEILING_THICKNESS;
		for (int x = -300; x <= 300; x += 11) {
			for (int z = -300; z <= 300; z += 11) {
				int top = NetherRelief.floorTop(SEED, x, z);
				int bottom = NetherRelief.ceilingBottom(SEED, x, z);
				assertTrue(top >= floorBase, "floor dipped below its slab at " + x + "," + z);
				assertTrue(top <= floorBase + NetherRelief.FLOOR_RELIEF);
				assertTrue(bottom <= ceilBase, "ceiling rose above its slab at " + x + "," + z);
				assertTrue(bottom >= ceilBase - NetherRelief.CEILING_DROP);
				assertTrue(bottom - top > 40,
						"band pinched shut to " + (bottom - top) + " blocks at " + x + "," + z);
			}
		}
	}

	@Test
	@DisplayName("the lava sea leaves dry ground and still floods the low places")
	void lavaMakesACoast() {
		int lava = NetherRelief.lavaLevel();
		int dry = 0;
		int flooded = 0;
		for (int x = -200; x <= 200; x += 5) {
			for (int z = -200; z <= 200; z += 5) {
				if (NetherRelief.floorTop(SEED, x, z) > lava) dry++;
				else flooded++;
			}
		}
		assertTrue(dry > 0 && flooded > 0,
				"a sea that covers everything or nothing is not a coast (dry " + dry + ", flooded " + flooded + ")");
	}

	@Test
	@DisplayName("noise stays in range and is not a constant")
	void noiseIsUsable() {
		float min = 1f;
		float max = 0f;
		for (int x = -500; x <= 500; x += 13) {
			for (int z = -500; z <= 500; z += 13) {
				float v = NetherRelief.fractal(SEED, x, z);
				assertTrue(v >= 0f && v <= 1f, "noise out of range: " + v);
				min = Math.min(min, v);
				max = Math.max(max, v);
			}
		}
		assertTrue(max - min > 0.5f, "noise only spans " + (max - min) + " — the band would read as flat");
	}
}
