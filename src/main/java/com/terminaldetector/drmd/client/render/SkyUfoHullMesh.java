package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.mega.SkyUfoHull;
import com.terminaldetector.drmd.world.structure.StructureDelta.Cell;
import com.terminaldetector.drmd.world.structure.StructureFaceCuller;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The Sky UFO hull, baked once into a flat colored-quad buffer and cached forever — same 12-floats-
 * per-quad layout {@link com.terminaldetector.drmd.client.planet.PlanetSurfaceMesh} already uses, but
 * baked a single time rather than rebuilt on camera drift: {@link SkyUfoHull#TEMPLATE} is one immutable
 * static shape (hull damage is all-or-nothing at destruction, not incremental while flying — see
 * {@code SkyUfoEntity.finishDestruction}), so unlike terrain under a moving viewpoint, this geometry
 * never actually changes. Only the transform applied at render time does.
 *
 * <p>Flat per-material colour, not textured — a real block-texture UV lookup needs at least one
 * rendering API call this sandbox cannot pin down exactly without decompiled Minecraft source (see
 * this feature's plan file), so it is left as an explicitly separate, optional follow-up. Exact shades
 * below are a reasonable guess at each material's real color, not sampled from the real texture — a
 * live-client-only tuning question, same honesty this project's other unverified visual constants
 * already carry.
 *
 * <p>Classifies by exact {@code Block} identity against {@link SkyUfoHull}'s own known, small,
 * fully-enumerable material palette ({@link SkyUfoHull#stateFor} plus its pedestal/core/lantern
 * additions) rather than calling {@code BlockState.isOpaque()} — this project's test suite has never
 * exercised anything touching {@code Block}/{@code BlockState} (confirmed from {@code build.gradle}'s
 * own comment on why its tests are pure-geometry only), so this file deliberately needs nothing more
 * from that API than {@code BlockState.isOf(Block)}, one of this codebase's own most common calls.
 */
public final class SkyUfoHullMesh {
	private static SkyUfoHullMesh instance;

	public final float[] positions;
	public final int[] colours;
	public final int quads;

	private SkyUfoHullMesh(float[] positions, int[] colours, int quads) {
		this.positions = positions;
		this.colours = colours;
		this.quads = quads;
	}

	/** Baked once, lazily, on first use — never rebuilt afterward. */
	public static SkyUfoHullMesh get() {
		if (instance == null) instance = bake();
		return instance;
	}

	private enum Material {
		/** Oxidized-copper outer hull. */
		COPPER(0xFF6E8A6A, 1f),
		/** Prismarine-bricks inner shell. */
		SHELL(0xFF5B9E93, 1f),
		/** Dark-prismarine deck plate. */
		DECK(0xFF2B4B49, 1f),
		/** Obsidian reactor pedestal. */
		OBSIDIAN(0xFF16141C, 1f),
		/** The unstable reactor core block itself. */
		REACTOR(0xFFCC5522, 1f),
		/** Sea-lantern ring lights. */
		LANTERN(0xFFE8FBF2, 1f),
		/** Cyan stained glass — translucent, always drawn full (see {@link #bake}). */
		GLASS(0xFF4CD2D8, 0.55f);

		final int rgb;
		final float alpha;

		Material(int rgb, float alpha) {
			this.rgb = rgb;
			this.alpha = alpha;
		}
	}

	private static Material classify(BlockState state) {
		if (state.isOf(Blocks.OXIDIZED_COPPER)) return Material.COPPER;
		if (state.isOf(Blocks.PRISMARINE_BRICKS)) return Material.SHELL;
		if (state.isOf(Blocks.DARK_PRISMARINE)) return Material.DECK;
		if (state.isOf(Blocks.OBSIDIAN)) return Material.OBSIDIAN;
		if (state.isOf(ModWorldBlocks.UNSTABLE_REACTOR)) return Material.REACTOR;
		if (state.isOf(Blocks.SEA_LANTERN)) return Material.LANTERN;
		if (state.isOf(Blocks.CYAN_STAINED_GLASS)) return Material.GLASS;
		return null; // air (carved interior/openings) or anything outside the template's known palette
	}

	private static SkyUfoHullMesh bake() {
		Map<Cell, BlockState> cells = SkyUfoHull.TEMPLATE.cells();

		// Opaque-for-culling excludes glass on purpose: a neighbour sitting against a glass cell would
		// still be visible through it in the real world, so it must keep the face that borders glass —
		// exactly ordinary block-transparency behaviour, not a simplification.
		Set<Cell> opaque = new HashSet<>(cells.size() * 2);
		for (Map.Entry<Cell, BlockState> e : cells.entrySet()) {
			Material m = classify(e.getValue());
			if (m != null && m != Material.GLASS) opaque.add(e.getKey());
		}

		// Worst case every one of these cells shows all 6 faces (nothing culled) — generous headroom,
		// trimmed to the real size below, same as PlanetSurfaceMesh.build()'s own over-allocate-then-copy.
		int maxQuads = cells.size() * 6;
		Builder b = new Builder(maxQuads);

		for (Map.Entry<Cell, BlockState> e : cells.entrySet()) {
			Cell cell = e.getKey();
			Material m = classify(e.getValue());
			if (m == null) continue;

			if (m == Material.GLASS) {
				for (Direction d : Direction.values()) b.face(cell, d, m.rgb, m.alpha);
			} else {
				for (int[] offset : StructureFaceCuller.visibleFaceOffsets(opaque, cell)) {
					b.face(cell, Direction.of(offset), m.rgb, m.alpha);
				}
			}
		}

		return new SkyUfoHullMesh(
				Arrays.copyOf(b.positions, b.quads * 12), Arrays.copyOf(b.colours, b.quads), b.quads);
	}

	/** The six axis faces, matching {@link StructureFaceCuller}'s own fixed offset order. */
	private enum Direction {
		EAST(1, 0, 0), WEST(-1, 0, 0),
		UP(0, 1, 0), DOWN(0, -1, 0),
		SOUTH(0, 0, 1), NORTH(0, 0, -1);

		final int dx, dy, dz;

		Direction(int dx, int dy, int dz) {
			this.dx = dx;
			this.dy = dy;
			this.dz = dz;
		}

		static Direction of(int[] offset) {
			for (Direction d : values()) {
				if (d.dx == offset[0] && d.dy == offset[1] && d.dz == offset[2]) return d;
			}
			throw new IllegalArgumentException("not an axis offset: " + Arrays.toString(offset));
		}
	}

	private static final class Builder {
		final float[] positions;
		final int[] colours;
		int quads;

		Builder(int maxQuads) {
			positions = new float[maxQuads * 12];
			colours = new int[maxQuads];
		}

		/** Emits the one unit-square quad for {@code cell}'s face in direction {@code d}. */
		void face(Cell cell, Direction d, int rgb, float alpha) {
			float x0 = cell.x(), y0 = cell.y(), z0 = cell.z();
			float x1 = x0 + 1, y1 = y0 + 1, z1 = z0 + 1;
			// Top brighter, bottom darker, the four sides in between — cheap fake ambient occlusion,
			// same shading idiom PlanetSurfaceMesh.box() already uses for its landmark silhouettes.
			float shade = switch (d) {
				case UP -> 1.0f;
				case DOWN -> 0.55f;
				default -> 0.8f;
			};
			int argb = argb(rgb, shade, alpha);
			switch (d) {
				case EAST -> quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, argb);
				case WEST -> quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, argb);
				case UP -> quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, argb);
				case DOWN -> quad(x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, argb);
				case SOUTH -> quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, argb);
				case NORTH -> quad(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, argb);
			}
		}

		private void quad(float x0, float y0, float z0, float x1, float y1, float z1,
						  float x2, float y2, float z2, float x3, float y3, float z3, int argb) {
			int i = quads * 12;
			positions[i] = x0; positions[i + 1] = y0; positions[i + 2] = z0;
			positions[i + 3] = x1; positions[i + 4] = y1; positions[i + 5] = z1;
			positions[i + 6] = x2; positions[i + 7] = y2; positions[i + 8] = z2;
			positions[i + 9] = x3; positions[i + 10] = y3; positions[i + 11] = z3;
			colours[quads] = argb;
			quads++;
		}

		private static int argb(int rgb, float shade, float alpha) {
			int r = (int) (((rgb >> 16) & 0xFF) * shade);
			int g = (int) (((rgb >> 8) & 0xFF) * shade);
			int b = (int) ((rgb & 0xFF) * shade);
			int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255f);
			return (a << 24) | (r << 16) | (g << 8) | b;
		}
	}
}
