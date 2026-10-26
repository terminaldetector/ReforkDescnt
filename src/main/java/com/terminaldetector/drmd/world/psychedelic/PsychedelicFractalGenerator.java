package com.terminaldetector.drmd.world.psychedelic;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldAccess;

/**
 * Builds one psychedelic fractal installation in open space (colourful glass / concrete / crystals).
 */
public final class PsychedelicFractalGenerator {
	private static final BlockState[] PALETTE = {
			Blocks.MAGENTA_STAINED_GLASS.getDefaultState(),
			Blocks.PURPLE_STAINED_GLASS.getDefaultState(),
			Blocks.CYAN_STAINED_GLASS.getDefaultState(),
			Blocks.LIME_STAINED_GLASS.getDefaultState(),
			Blocks.PINK_CONCRETE.getDefaultState(),
			Blocks.YELLOW_CONCRETE.getDefaultState(),
			Blocks.ORANGE_CONCRETE.getDefaultState(),
			Blocks.AMETHYST_BLOCK.getDefaultState(),
			Blocks.PURPUR_BLOCK.getDefaultState(),
			Blocks.SEA_LANTERN.getDefaultState(),
			Blocks.SHROOMLIGHT.getDefaultState(),
			Blocks.PEARLESCENT_FROGLIGHT.getDefaultState(),
			Blocks.VERDANT_FROGLIGHT.getDefaultState(),
			Blocks.OCHRE_FROGLIGHT.getDefaultState(),
			Blocks.TINTED_GLASS.getDefaultState(),
			Blocks.CRYING_OBSIDIAN.getDefaultState(),
	};

	private PsychedelicFractalGenerator() {}

	public static void generate(WorldAccess world, BlockPos origin, PsychedelicFractal kind, Random random) {
		switch (kind) {
			case MENGER_SPONGE -> menger(world, origin, 3, random);
			case SIERPINSKI_TETRA -> sierpinski(world, origin, 4, random);
			case MANDELBULB_SHELL -> mandelbulb(world, origin, 18, random);
			case JULIA_SLICE -> julia(world, origin, 22, random);
			case APOLLONIAN_SPHERES -> apollonian(world, origin, random);
			case GYROID -> gyroid(world, origin, 20, random);
			case SPIRAL_GALAXY -> galaxy(world, origin, random);
			case TORUS_KNOT -> torusKnot(world, origin, random);
			case KOCH_STAR -> koch(world, origin, 3, random);
			case HILBERT_TUBE -> hilbert(world, origin, 3, random);
			case DRAGON_RIBBON -> dragon(world, origin, 10, random);
			case LORENZ_ATTRACTOR -> lorenz(world, origin, random);
			case FIBONACCI_SUNFLOWER -> sunflower(world, origin, random);
			case CANTOR_DUST -> cantor(world, origin, 4, random);
			case PLASMA_NEBULA -> plasma(world, origin, 24, random);
			case FLOWER_OF_LIFE -> flower(world, origin, random);
			case HYPERBOLIC_LATTICE -> hyperbolic(world, origin, 16, random);
			case QUATERNION_SLICE -> quaternion(world, origin, 18, random);
		}
		// Nav beacon at centre
		set(world, origin, Blocks.BEACON.getDefaultState());
		set(world, origin.up(), Blocks.SEA_LANTERN.getDefaultState());
	}

	private static void menger(WorldAccess world, BlockPos o, int iter, Random random) {
		int size = (int) Math.pow(3, iter);
		int half = size / 2;
		for (int x = 0; x < size; x++) {
			for (int y = 0; y < size; y++) {
				for (int z = 0; z < size; z++) {
					if (!mengerOccupied(x, y, z, iter)) continue;
					set(world, o.add(x - half, y - half, z - half), pal(random, x + y + z));
				}
			}
		}
	}

	private static boolean mengerOccupied(int x, int y, int z, int iter) {
		for (int i = 0; i < iter; i++) {
			int xm = Math.floorMod(x, 3), ym = Math.floorMod(y, 3), zm = Math.floorMod(z, 3);
			int holes = 0;
			if (xm == 1) holes++;
			if (ym == 1) holes++;
			if (zm == 1) holes++;
			if (holes >= 2) return false;
			x /= 3; y /= 3; z /= 3;
		}
		return true;
	}

	private static void sierpinski(WorldAccess world, BlockPos o, int depth, Random random) {
		sierRecurse(world, o, 16, depth, random);
	}

	private static void sierRecurse(WorldAccess world, BlockPos c, int size, int depth, Random random) {
		if (depth <= 0 || size < 2) {
			fillBox(world, c, size / 2, pal(random, depth));
			return;
		}
		int h = size / 2;
		sierRecurse(world, c.add(-h, -h, -h), h, depth - 1, random);
		sierRecurse(world, c.add(h, -h, -h), h, depth - 1, random);
		sierRecurse(world, c.add(0, -h, h), h, depth - 1, random);
		sierRecurse(world, c.add(0, h, 0), h, depth - 1, random);
	}

	private static void mandelbulb(WorldAccess world, BlockPos o, int r, Random random) {
		int power = 8;
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					if (x * x + y * y + z * z > r * r) continue;
					double nx = x / (double) r * 1.2;
					double ny = y / (double) r * 1.2;
					double nz = z / (double) r * 1.2;
					if (!mandelHit(nx, ny, nz, power)) continue;
					// Surface shell only
					if (mandelHit(nx * 0.92, ny * 0.92, nz * 0.92, power)) continue;
					set(world, o.add(x, y, z), pal(random, x * 3 + y * 5 + z));
				}
			}
		}
	}

	private static boolean mandelHit(double x, double y, double z, int power) {
		double zx = x, zy = y, zz = z;
		for (int i = 0; i < 8; i++) {
			double r = Math.sqrt(zx * zx + zy * zy + zz * zz);
			if (r > 2) return false;
			double theta = Math.atan2(Math.sqrt(zx * zx + zy * zy), zz);
			double phi = Math.atan2(zy, zx);
			double rp = Math.pow(r, power);
			zx = rp * Math.sin(theta * power) * Math.cos(phi * power) + x;
			zy = rp * Math.sin(theta * power) * Math.sin(phi * power) + y;
			zz = rp * Math.cos(theta * power) + z;
		}
		return true;
	}

	private static void julia(WorldAccess world, BlockPos o, int r, Random random) {
		double cx = -0.7, cy = 0.27015;
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				double zx = x / (double) r * 1.6;
				double zy = y / (double) r * 1.6;
				int iter = 0;
				while (zx * zx + zy * zy < 4 && iter < 24) {
					double tmp = zx * zx - zy * zy + cx;
					zy = 2 * zx * zy + cy;
					zx = tmp;
					iter++;
				}
				if (iter < 8 || iter >= 24) continue;
				int depth = Math.min(6, iter / 3);
				for (int z = -depth; z <= depth; z++) {
					set(world, o.add(x, y, z), pal(random, iter + z));
				}
			}
		}
	}

	private static void apollonian(WorldAccess world, BlockPos o, Random random) {
		placeSphere(world, o, 14, pal(random, 1));
		placeSphere(world, o.add(18, 0, 0), 9, pal(random, 2));
		placeSphere(world, o.add(-12, 10, 8), 7, pal(random, 3));
		placeSphere(world, o.add(4, -8, -14), 8, pal(random, 4));
		placeSphere(world, o.add(8, 12, 12), 5, pal(random, 5));
		placeSphere(world, o.add(-10, -6, 14), 6, pal(random, 6));
	}

	private static void gyroid(WorldAccess world, BlockPos o, int r, Random random) {
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					if (x * x + y * y + z * z > r * r) continue;
					double s = Math.sin(x * 0.45) * Math.cos(y * 0.45)
							+ Math.sin(y * 0.45) * Math.cos(z * 0.45)
							+ Math.sin(z * 0.45) * Math.cos(x * 0.45);
					if (Math.abs(s) > 0.18) continue;
					set(world, o.add(x, y, z), pal(random, x + y * 2 + z * 3));
				}
			}
		}
	}

	private static void galaxy(WorldAccess world, BlockPos o, Random random) {
		for (int i = 0; i < 900; i++) {
			double t = i / 900.0 * Math.PI * 8;
			double rad = 2 + t * 2.2;
			double x = Math.cos(t) * rad + (random.nextDouble() - 0.5) * 2;
			double z = Math.sin(t) * rad + (random.nextDouble() - 0.5) * 2;
			double y = (random.nextDouble() - 0.5) * 3 + Math.sin(t * 0.5);
			set(world, o.add((int) x, (int) y, (int) z), pal(random, i));
		}
		// Core
		placeSphere(world, o, 4, Blocks.SHROOMLIGHT.getDefaultState());
	}

	private static void torusKnot(WorldAccess world, BlockPos o, Random random) {
		int p = 3, q = 2;
		for (int i = 0; i < 360; i++) {
			double t = Math.toRadians(i);
			double r = Math.cos(q * t) + 2;
			double x = r * Math.cos(p * t) * 6;
			double y = r * Math.sin(p * t) * 6;
			double z = -Math.sin(q * t) * 6;
			for (int d = -1; d <= 1; d++) {
				set(world, o.add((int) x + d, (int) y, (int) z), pal(random, i + d));
				set(world, o.add((int) x, (int) y + d, (int) z), pal(random, i));
			}
		}
	}

	private static void koch(WorldAccess world, BlockPos o, int depth, Random random) {
		kochEdge(world, o.add(-16, 0, 0), o.add(16, 0, 0), depth, random);
		kochEdge(world, o.add(16, 0, 0), o.add(0, 0, 18), depth, random);
		kochEdge(world, o.add(0, 0, 18), o.add(-16, 0, 0), depth, random);
	}

	private static void kochEdge(WorldAccess world, BlockPos a, BlockPos b, int depth, Random random) {
		if (depth <= 0) {
			line(world, a, b, pal(random, depth));
			return;
		}
		BlockPos ab = lerp(a, b, 1f / 3f);
		BlockPos bc = lerp(a, b, 2f / 3f);
		double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
		BlockPos peak = new BlockPos(
				(ab.getX() + bc.getX()) / 2 - (int) (dz / 3),
				a.getY() + 4,
				(ab.getZ() + bc.getZ()) / 2 + (int) (dx / 3));
		kochEdge(world, a, ab, depth - 1, random);
		kochEdge(world, ab, peak, depth - 1, random);
		kochEdge(world, peak, bc, depth - 1, random);
		kochEdge(world, bc, b, depth - 1, random);
	}

	private static void hilbert(WorldAccess world, BlockPos o, int order, Random random) {
		int n = 1 << order;
		BlockPos prev = null;
		for (int i = 0; i < n * n; i++) {
			int[] xz = d2xy(n, i);
			BlockPos p = o.add(xz[0] * 3 - n * 3 / 2, (i % 7) - 3, xz[1] * 3 - n * 3 / 2);
			if (prev != null) line(world, prev, p, pal(random, i));
			prev = p;
		}
	}

	private static int[] d2xy(int n, int d) {
		int x = 0, y = 0, t = d;
		for (int s = 1; s < n; s *= 2) {
			int rx = 1 & (t / 2);
			int ry = 1 & (t ^ rx);
			if (ry == 0) {
				if (rx == 1) { x = s - 1 - x; y = s - 1 - y; }
				int tmp = x; x = y; y = tmp;
			}
			x += s * rx;
			y += s * ry;
			t /= 4;
		}
		return new int[]{x, y};
	}

	private static void dragon(WorldAccess world, BlockPos o, int turns, Random random) {
		String seq = "FX";
		for (int i = 0; i < turns; i++) {
			StringBuilder next = new StringBuilder();
			for (char c : seq.toCharArray()) {
				if (c == 'X') next.append("X+YF+");
				else if (c == 'Y') next.append("-FX-Y");
				else next.append(c);
			}
			seq = next.toString();
		}
		int x = 0, y = 0, z = 0, dir = 0;
		int[][] D = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
		for (char c : seq.toCharArray()) {
			if (c == 'F') {
				x += D[dir][0];
				z += D[dir][1];
				set(world, o.add(x, y, z), pal(random, x + z));
				set(world, o.add(x, y + 1, z), Blocks.CYAN_STAINED_GLASS.getDefaultState());
			} else if (c == '+') dir = (dir + 1) & 3;
			else if (c == '-') dir = (dir + 3) & 3;
		}
	}

	private static void lorenz(WorldAccess world, BlockPos o, Random random) {
		double x = 0.1, y = 0, z = 0;
		double dt = 0.01, sigma = 10, rho = 28, beta = 8.0 / 3.0;
		for (int i = 0; i < 2500; i++) {
			double dx = sigma * (y - x);
			double dy = x * (rho - z) - y;
			double dz = x * y - beta * z;
			x += dx * dt; y += dy * dt; z += dz * dt;
			set(world, o.add((int) (x * 1.2), (int) ((z - 25) * 1.1), (int) (y * 1.2)), pal(random, i));
		}
	}

	private static void sunflower(WorldAccess world, BlockPos o, Random random) {
		double golden = Math.PI * (3 - Math.sqrt(5));
		for (int i = 0; i < 400; i++) {
			double r = 0.5 * Math.sqrt(i);
			double th = i * golden;
			int x = (int) (Math.cos(th) * r * 1.8);
			int z = (int) (Math.sin(th) * r * 1.8);
			int y = (int) (Math.sin(i * 0.15) * 2);
			set(world, o.add(x, y, z), pal(random, i));
			if (i % 7 == 0) set(world, o.add(x, y + 1, z), Blocks.SHROOMLIGHT.getDefaultState());
		}
	}

	private static void cantor(WorldAccess world, BlockPos o, int depth, Random random) {
		cantorRec(world, o, 27, depth, random);
	}

	private static void cantorRec(WorldAccess world, BlockPos c, int size, int depth, Random random) {
		if (depth <= 0 || size < 3) {
			fillBox(world, c, Math.max(1, size / 2), pal(random, depth));
			return;
		}
		int h = size / 3;
		for (int ix = -1; ix <= 1; ix += 2) {
			for (int iy = -1; iy <= 1; iy += 2) {
				for (int iz = -1; iz <= 1; iz += 2) {
					cantorRec(world, c.add(ix * h, iy * h, iz * h), h, depth - 1, random);
				}
			}
		}
	}

	private static void plasma(WorldAccess world, BlockPos o, int r, Random random) {
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					double d = Math.sqrt(x * x + y * y + z * z) / r;
					if (d > 1) continue;
					double n = Math.sin(x * 0.35) + Math.cos(y * 0.41) + Math.sin(z * 0.29 + y * 0.1);
					if (n < 1.2 || d < 0.25) continue;
					if (random.nextFloat() > 0.35f) continue;
					set(world, o.add(x, y, z), pal(random, (int) (n * 10) + x));
				}
			}
		}
	}

	private static void flower(WorldAccess world, BlockPos o, Random random) {
		int[] radii = {8, 8, 8, 8, 8, 8};
		for (int i = 0; i < 6; i++) {
			double ang = i * Math.PI / 3;
			BlockPos c = o.add((int) (Math.cos(ang) * 8), 0, (int) (Math.sin(ang) * 8));
			ring(world, c, radii[i], pal(random, i));
		}
		ring(world, o, 8, pal(random, 9));
		ring(world, o, 16, Blocks.CYAN_STAINED_GLASS.getDefaultState());
	}

	private static void hyperbolic(WorldAccess world, BlockPos o, int r, Random random) {
		for (int x = -r; x <= r; x += 2) {
			for (int y = -r; y <= r; y += 2) {
				for (int z = -r; z <= r; z += 2) {
					double h = x * x + y * y - z * z;
					if (Math.abs(h - 40) > 12) continue;
					set(world, o.add(x, y, z), pal(random, x + z));
				}
			}
		}
	}

	private static void quaternion(WorldAccess world, BlockPos o, int r, Random random) {
		// 3D slice of a quaternion Julia-like escape (cheap).
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					if (x * x + y * y + z * z > r * r) continue;
					double qx = x / (double) r, qy = y / (double) r, qz = z / (double) r, qw = 0.1;
					boolean escape = false;
					for (int i = 0; i < 7; i++) {
						// q^2 + c
						double nx = qx * qx - qy * qy - qz * qz - qw * qw;
						double ny = 2 * qx * qy;
						double nz = 2 * qx * qz;
						double nw = 2 * qx * qw;
						qx = nx - 0.2; qy = ny + 0.5; qz = nz; qw = nw + 0.1;
						if (qx * qx + qy * qy + qz * qz + qw * qw > 4) { escape = true; break; }
					}
					if (escape) continue;
					set(world, o.add(x, y, z), pal(random, x * 7 + y));
				}
			}
		}
	}

	// --- helpers ---

	private static BlockState pal(Random random, int salt) {
		return PALETTE[Math.floorMod(salt * 31 + random.nextInt(3), PALETTE.length)];
	}

	private static void placeSphere(WorldAccess world, BlockPos c, int r, BlockState state) {
		int r2 = r * r;
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					int d = x * x + y * y + z * z;
					if (d > r2 || d < (r - 1) * (r - 1)) continue;
					set(world, c.add(x, y, z), state);
				}
			}
		}
	}

	private static void ring(WorldAccess world, BlockPos c, int r, BlockState state) {
		for (int a = 0; a < 360; a += 3) {
			double rad = Math.toRadians(a);
			set(world, c.add((int) (Math.cos(rad) * r), 0, (int) (Math.sin(rad) * r)), state);
		}
	}

	private static void fillBox(WorldAccess world, BlockPos c, int half, BlockState state) {
		for (int x = -half; x <= half; x++) {
			for (int y = -half; y <= half; y++) {
				for (int z = -half; z <= half; z++) {
					set(world, c.add(x, y, z), state);
				}
			}
		}
	}

	private static void line(WorldAccess world, BlockPos a, BlockPos b, BlockState state) {
		int steps = Math.max(Math.abs(b.getX() - a.getX()),
				Math.max(Math.abs(b.getY() - a.getY()), Math.abs(b.getZ() - a.getZ())));
		steps = Math.max(1, steps);
		for (int i = 0; i <= steps; i++) {
			float t = i / (float) steps;
			set(world, new BlockPos(
					MathHelper.floor(MathHelper.lerp(t, a.getX(), b.getX())),
					MathHelper.floor(MathHelper.lerp(t, a.getY(), b.getY())),
					MathHelper.floor(MathHelper.lerp(t, a.getZ(), b.getZ()))), state);
		}
	}

	private static BlockPos lerp(BlockPos a, BlockPos b, float t) {
		return new BlockPos(
				MathHelper.floor(MathHelper.lerp(t, a.getX(), b.getX())),
				MathHelper.floor(MathHelper.lerp(t, a.getY(), b.getY())),
				MathHelper.floor(MathHelper.lerp(t, a.getZ(), b.getZ())));
	}

	private static void set(WorldAccess world, BlockPos pos, BlockState state) {
		int y = pos.getY();
		if (y < world.getBottomY() || y >= world.getBottomY() + world.getHeight()) return;
		if (world.getBlockState(pos) == state) return;
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
	}
}
