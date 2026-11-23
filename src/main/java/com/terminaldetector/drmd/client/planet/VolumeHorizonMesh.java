package com.terminaldetector.drmd.client.planet;

import com.terminaldetector.drmd.world.volume.VolumeField;

import java.util.Arrays;

/** Low-detail silhouette of the seed field, built by the existing horizon worker. */
final class VolumeHorizonMesh {
	static final float OUTER_RADIUS = 1100;
	private static final int CELL = 80;
	private static final int SIDE = 32;
	private static final int AREA = SIDE * SIDE;
	private static final int MAX_QUADS = 32_768;
	private static final int SHIFT = 16;
	private static final int[] DX = {-1, 1, 0, 0, 0, 0};
	private static final int[] DY = {0, 0, -1, 1, 0, 0};
	private static final int[] DZ = {0, 0, 0, 0, -1, 1};
	private static final int[][] CORNERS = {
			{0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0},
			{1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1},
			{0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 0, 1},
			{0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0},
			{1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0},
			{0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1}
	};
	private static final double[] LIGHT = {0.76, 0.66, 0.53, 1.0, 0.84, 0.71};

	private VolumeHorizonMesh() {}

	static Mesh build(VolumeField field, boolean thinDecks, int bottom, int top, double eyeX, double eyeY, double eyeZ) {
		int startX = gridStart(eyeX);
		int startY = gridStart(eyeY);
		int startZ = gridStart(eyeZ);
		int[] cells = new int[SIDE * AREA];
		for (int gy = 0; gy < SIDE; gy++) {
			int y0 = startY + gy * CELL;
			int yMin = Math.max(y0, bottom);
			int yMax = Math.min(y0 + CELL, top);
			if (yMin >= yMax) continue;
			int y = yMin + (yMax - yMin) / 2;
			for (int gz = 0; gz < SIDE; gz++) {
				int z = startZ + gz * CELL + CELL / 2;
				for (int gx = 0; gx < SIDE; gx++) {
					int x = startX + gx * CELL + CELL / 2;
					if (Math.abs(x) >= 29_999_984 || Math.abs(z) >= 29_999_984) continue;
					int occupiedY = y;
					if (field.density(x, y, z) <= 0) {
						if (!thinDecks) continue;
						// Additional vertical samples keep thin tower decks in the distant silhouette.
						if (y - SHIFT >= yMin && field.density(x, y - SHIFT, z) > 0) occupiedY = y - SHIFT;
						else if (y + SHIFT < yMax && field.density(x, y + SHIFT, z) > 0) occupiedY = y + SHIFT;
						else continue;
					}
					cells[index(gx, gy, gz)] = colour(field.material(x, occupiedY, z));
				}
			}
		}

		float[] positions = new float[MAX_QUADS * 12];
		int[] colours = new int[MAX_QUADS];
		int quads = 0;
		for (int gy = 0; gy < SIDE && quads < MAX_QUADS; gy++) {
			int worldY = startY + gy * CELL;
			float lowY = (float) (Math.max(worldY, bottom) - eyeY);
			float highY = (float) (Math.min(worldY + CELL, top) - eyeY);
			for (int gz = 0; gz < SIDE && quads < MAX_QUADS; gz++) {
				float z = (float) (startZ + gz * CELL - eyeZ);
				for (int gx = 0; gx < SIDE && quads < MAX_QUADS; gx++) {
					int colour = cells[index(gx, gy, gz)];
					if (colour == 0) continue;
					float x = (float) (startX + gx * CELL - eyeX);
					for (int face = 0; face < CORNERS.length && quads < MAX_QUADS; face++) {
						int nx = gx + DX[face], ny = gy + DY[face], nz = gz + DZ[face];
						// The sampling boundary is outside the fade. Do not create an artificial outer wall.
						if (nx < 0 || ny < 0 || nz < 0 || nx >= SIDE || ny >= SIDE || nz >= SIDE) continue;
						if (cells[index(nx, ny, nz)] != 0) continue;
						int[] corners = CORNERS[face];
						int vertex = quads * 12;
						for (int i = 0; i < corners.length; i += 3) {
							positions[vertex + i] = x + CELL * corners[i];
							positions[vertex + i + 1] = corners[i + 1] == 0 ? lowY : highY;
							positions[vertex + i + 2] = z + CELL * corners[i + 2];
						}
						colours[quads++] = shade(colour, LIGHT[face]);
					}
				}
			}
		}
		return new Mesh(Arrays.copyOf(positions, quads * 12), Arrays.copyOf(colours, quads));
	}

	private static int index(int x, int y, int z) {
		return y * AREA + z * SIDE + x;
	}

	private static int gridStart(double eye) {
		return ((int) Math.floor(eye / CELL + 0.5) - SIDE / 2) * CELL;
	}

	private static int colour(VolumeField.Material material) {
		return switch (material) {
			case BASALT -> 0x4b494c;
			case DEEPSLATE -> 0x59535d;
			case TUFF -> 0x737268;
			case CALCITE -> 0x9d9693;
			case AMETHYST -> 0x69517a;
			case ENGINEERED -> 0x67616a;
			case LIGHT -> 0xb6986c;
			case STONE -> 0x6e6c6b;
			case DIRT -> 0x705847;
			case GRASS -> 0x535c48;
		};
	}

	private static int shade(int rgb, double light) {
		int r = (int) (((rgb >> 16) & 255) * light);
		int g = (int) (((rgb >> 8) & 255) * light);
		int b = (int) ((rgb & 255) * light);
		return 0xff000000 | r << 16 | g << 8 | b;
	}

	record Mesh(float[] positions, int[] colours) {}
}
