package com.terminaldetector.drmd.world.build;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.world.LocalOrientation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Voxel templates for construction lasers — oriented to local UP / ship aim (6DoF).
 */
public final class ConstructShapes {
	private ConstructShapes() {}

	public static List<BlockPos> resolve(World world, PlayerEntity player, ConstructLaserTier tier,
			ConstructShape shape, boolean sneak) {
		int scale = sneak ? 2 : 1;
		int len = Math.min(tier.defaultLength * scale, tier.maxBlocks);
		Vec3d eye = player.getEyePos();
		Vec3d aim = aim(player);
		Vec3d up = LocalOrientation.getUp(player.getUuid()).normalize();
		Vec3d right = aim.crossProduct(up);
		if (right.lengthSquared() < 1e-6) right = aim.crossProduct(new Vec3d(0, 1, 0));
		if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
		right = right.normalize();
		up = right.crossProduct(aim).normalize();

		BlockPos anchor = anchor(world, player, eye, aim, Math.max(8, len));
		return switch (shape) {
			case LINE -> line(anchor, aim, len, tier.maxBlocks);
			case WALL -> wall(anchor, right, up, 6 * scale, 4 * scale, tier.maxBlocks);
			case BOX_FRAME -> boxFrame(anchor, right, up, aim, 5 * scale, 4 * scale, 5 * scale, tier.maxBlocks);
			case SOLID -> solidBox(anchor, right, up, aim, 4 * scale, 3 * scale, 4 * scale, tier.maxBlocks);
			case CYLINDER -> cylinder(anchor, aim, 3 * scale, 8 * scale, tier.maxBlocks);
			case STREAM -> List.of(); // continuous path — no scaffold
			case RING -> ring(anchor, right, up, 8 * scale, tier.maxBlocks);
			case PLATFORM -> platform(anchor, right, up, 7 * scale, tier.maxBlocks);
			case HANGAR -> hangar(anchor, right, up, aim, 6 * scale, 5 * scale, 8 * scale, tier.maxBlocks);
			case TORUS -> torus(anchor, right, up, 7 * scale, 2, tier.maxBlocks);
		};
	}

	public static Vec3d aim(PlayerEntity player) {
		return DescentPlayerData.get(player).isEnabled()
				? WeaponCore.aimDir(player)
				: player.getRotationVec(1f);
	}

	private static BlockPos anchor(World world, PlayerEntity player, Vec3d eye, Vec3d aim, double range) {
		var hit = world.raycast(new RaycastContext(eye, eye.add(aim.multiply(range)),
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
			return hit.getBlockPos().offset(hit.getSide());
		}
		return BlockPos.ofFloored(eye.add(aim.multiply(Math.min(6, range))));
	}

	private static List<BlockPos> line(BlockPos start, Vec3d aim, int len, int max) {
		Set<BlockPos> out = new LinkedHashSet<>();
		Direction face = Direction.getFacing(aim.x, aim.y, aim.z);
		BlockPos cur = start;
		for (int i = 0; i < len && out.size() < max; i++) {
			out.add(cur.toImmutable());
			cur = cur.offset(face);
		}
		return new ArrayList<>(out);
	}

	private static List<BlockPos> wall(BlockPos origin, Vec3d right, Vec3d up, int w, int h, int max) {
		Set<BlockPos> out = new LinkedHashSet<>();
		for (int x = -w / 2; x <= w / 2 && out.size() < max; x++) {
			for (int y = 0; y < h && out.size() < max; y++) {
				out.add(offset(origin, right, up, Vec3d.ZERO, x, y, 0));
			}
		}
		return new ArrayList<>(out);
	}

	private static List<BlockPos> boxFrame(BlockPos origin, Vec3d right, Vec3d up, Vec3d fwd,
			int w, int h, int d, int max) {
		Set<BlockPos> out = new LinkedHashSet<>();
		for (int x = 0; x <= w && out.size() < max; x++) {
			for (int y = 0; y <= h && out.size() < max; y++) {
				for (int z = 0; z <= d && out.size() < max; z++) {
					boolean edge = x == 0 || x == w || y == 0 || y == h || z == 0 || z == d;
					boolean cornerish = (x == 0 || x == w ? 1 : 0)
							+ (y == 0 || y == h ? 1 : 0)
							+ (z == 0 || z == d ? 1 : 0) >= 2;
					if (edge && cornerish) out.add(offset(origin, right, up, fwd, x, y, z));
				}
			}
		}
		return new ArrayList<>(out);
	}

	private static List<BlockPos> solidBox(BlockPos origin, Vec3d right, Vec3d up, Vec3d fwd,
			int w, int h, int d, int max) {
		Set<BlockPos> out = new LinkedHashSet<>();
		for (int x = 0; x <= w && out.size() < max; x++) {
			for (int y = 0; y <= h && out.size() < max; y++) {
				for (int z = 0; z <= d && out.size() < max; z++) {
					out.add(offset(origin, right, up, fwd, x, y, z));
				}
			}
		}
		return new ArrayList<>(out);
	}

	private static List<BlockPos> cylinder(BlockPos origin, Vec3d axis, int radius, int length, int max) {
		Set<BlockPos> out = new LinkedHashSet<>();
		Vec3d a = axis.normalize();
		Vec3d ref = Math.abs(a.y) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
		Vec3d u = a.crossProduct(ref).normalize();
		Vec3d v = a.crossProduct(u).normalize();
		for (int t = 0; t < length && out.size() < max; t++) {
			for (int x = -radius; x <= radius && out.size() < max; x++) {
				for (int y = -radius; y <= radius && out.size() < max; y++) {
					if (x * x + y * y > radius * radius) continue;
					boolean shell = x * x + y * y >= (radius - 1) * (radius - 1);
					if (!shell && radius > 1) continue;
					out.add(offset(origin, u, v, a, x, y, t));
				}
			}
		}
		return new ArrayList<>(out);
	}

	private static List<BlockPos> ring(BlockPos origin, Vec3d right, Vec3d up, int radius, int max) {
		Set<BlockPos> out = new LinkedHashSet<>();
		for (int a = 0; a < 360 && out.size() < max; a += 6) {
			double rad = Math.toRadians(a);
			int x = (int) Math.round(Math.cos(rad) * radius);
			int z = (int) Math.round(Math.sin(rad) * radius);
			out.add(offset(origin, right, up, right.crossProduct(up).normalize(), x, 0, z));
			out.add(offset(origin, right, up, right.crossProduct(up).normalize(), x, 1, z));
		}
		return new ArrayList<>(out);
	}

	private static List<BlockPos> platform(BlockPos origin, Vec3d right, Vec3d up, int radius, int max) {
		Set<BlockPos> out = new LinkedHashSet<>();
		Vec3d fwd = right.crossProduct(up).normalize();
		for (int x = -radius; x <= radius && out.size() < max; x++) {
			for (int z = -radius; z <= radius && out.size() < max; z++) {
				if (x * x + z * z > radius * radius) continue;
				out.add(offset(origin, right, up, fwd, x, 0, z));
				if (x * x + z * z >= (radius - 1) * (radius - 1)) {
					out.add(offset(origin, right, up, fwd, x, 1, z));
				}
			}
		}
		return new ArrayList<>(out);
	}

	private static List<BlockPos> hangar(BlockPos origin, Vec3d right, Vec3d up, Vec3d fwd,
			int w, int h, int d, int max) {
		Set<BlockPos> out = new LinkedHashSet<>();
		for (int x = 0; x <= w && out.size() < max; x++) {
			for (int y = 0; y <= h && out.size() < max; y++) {
				for (int z = 0; z <= d && out.size() < max; z++) {
					boolean floor = y == 0;
					boolean roof = y == h;
					boolean wall = x == 0 || x == w;
					boolean back = z == d;
					if (floor || roof || wall || back) {
						out.add(offset(origin, right, up, fwd, x, y, z));
					}
				}
			}
		}
		return new ArrayList<>(out);
	}

	private static List<BlockPos> torus(BlockPos origin, Vec3d right, Vec3d up, int major, int minor, int max) {
		Set<BlockPos> out = new LinkedHashSet<>();
		Vec3d fwd = right.crossProduct(up).normalize();
		for (int a = 0; a < 360 && out.size() < max; a += 8) {
			double rad = Math.toRadians(a);
			double cx = Math.cos(rad) * major;
			double cz = Math.sin(rad) * major;
			for (int b = 0; b < 360 && out.size() < max; b += 30) {
				double br = Math.toRadians(b);
				double ox = Math.cos(br) * minor;
				double oy = Math.sin(br) * minor;
				int x = (int) Math.round(cx + ox * Math.cos(rad));
				int y = (int) Math.round(oy);
				int z = (int) Math.round(cz + ox * Math.sin(rad));
				out.add(offset(origin, right, up, fwd, x, y, z));
			}
		}
		return new ArrayList<>(out);
	}

	private static BlockPos offset(BlockPos origin, Vec3d right, Vec3d up, Vec3d fwd, int x, int y, int z) {
		Vec3d p = Vec3d.ofCenter(origin)
				.add(right.multiply(x))
				.add(up.multiply(y))
				.add(fwd.multiply(z));
		return BlockPos.ofFloored(p);
	}

	/** Pack local shape index for a tier (stable cycle). */
	public static ConstructShape shapeAt(ConstructLaserTier tier, int index) {
		ConstructShape[] shapes = tier.shapes();
		return shapes[Math.floorMod(index, shapes.length)];
	}

	public static int nextIndex(ConstructLaserTier tier, int index) {
		return Math.floorMod(index + 1, tier.shapes().length);
	}

	public static Direction localFace(UUID id) {
		Vec3d up = LocalOrientation.getUp(id);
		return Direction.getFacing(
				(float) up.x, (float) up.y, (float) up.z);
	}

	public static int clampLen(int v, int lo, int hi) {
		return MathHelper.clamp(v, lo, hi);
	}
}
