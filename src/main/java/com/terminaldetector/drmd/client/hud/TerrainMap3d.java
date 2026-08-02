package com.terminaldetector.drmd.client.hud;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.client.llod.LlodClientState;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.flight.ShipAttitude;
import com.terminaldetector.drmd.world.WorldRules;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Pyro GX tactical 3D terrain map (Overlord Vertor–style volumetric scanner).
 * Toggle with Tab+H while piloting Pyro GX (or with 6DoF on as fallback).
 *
 * Isometric heightfield of nearby columns in ship-local axes + macro markers.
 */
public final class TerrainMap3d {
	private static final int GRID = 28;          // samples per side
	private static final int STEP = 3;           // blocks between samples
	private static final int PANEL_W = 220;
	private static final int PANEL_H = 160;

	private static boolean open;
	private static int[][] heights;              // relative Y to player
	private static byte[][] kinds;               // 0 air/void, 1 solid, 2 liquid, 3 industrial
	private static int scanAge;
	private static float yawView;

	private TerrainMap3d() {}

	public static boolean isOpen() { return open; }

	public static void toggle() {
		open = !open;
		if (open) {
			scanAge = 999;
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.player != null) {
				mc.player.sendMessage(Text.literal("§bPYRO GX §f· 3D MAP ON §7(Tab+H)"), true);
			}
		} else {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.player != null) {
				mc.player.sendMessage(Text.literal("§7PYRO GX · 3D MAP OFF"), true);
			}
		}
	}

	public static void close() { open = false; }

	/** Allowed when piloting Pyro GX, or 6DoF enabled (ship systems online). */
	public static boolean canUse(ClientPlayerEntity player) {
		if (player == null) return false;
		if (player.getVehicle() instanceof PyroShipEntity) return true;
		return DescentClientState.enabled;
	}

	public static void tick(MinecraftClient mc) {
		if (!open || mc.player == null || mc.world == null) return;
		if (!canUse(mc.player)) {
			open = false;
			return;
		}
		scanAge++;
		if (scanAge < 8 && heights != null) return;
		scanAge = 0;
		rescan(mc.world, mc.player);
		if (DescentClientState.attitudeValid) {
			yawView = ShipAttitudeClient.get().yawDegrees();
		} else {
			yawView = mc.player.getYaw();
		}
	}

	private static void rescan(World world, ClientPlayerEntity player) {
		heights = new int[GRID][GRID];
		kinds = new byte[GRID][GRID];
		BlockPos origin = player.getBlockPos();
		int half = (GRID * STEP) / 2;
		for (int ix = 0; ix < GRID; ix++) {
			for (int iz = 0; iz < GRID; iz++) {
				int wx = origin.getX() - half + ix * STEP;
				int wz = origin.getZ() - half + iz * STEP;
				int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, wx, wz);
				// Also probe near player Y for caves / industrial voids
				int probeY = origin.getY();
				BlockPos.Mutable m = new BlockPos.Mutable(wx, probeY, wz);
				int solidNear = findNearestSolid(world, m, probeY, 24);
				int hy = solidNear != Integer.MIN_VALUE ? solidNear : top;
				heights[ix][iz] = hy - origin.getY();
				BlockState st = world.getBlockState(new BlockPos(wx, hy, wz));
				if (st.isOf(Blocks.WATER) || st.isOf(Blocks.LAVA)) kinds[ix][iz] = 2;
				else if (st.isOf(Blocks.BEACON) || st.isOf(Blocks.LODESTONE) || st.isOf(Blocks.IRON_BLOCK)
						|| st.isOf(Blocks.COPPER_BLOCK) || st.isOf(Blocks.SEA_LANTERN)) kinds[ix][iz] = 3;
				else if (st.isAir()) kinds[ix][iz] = 0;
				else kinds[ix][iz] = 1;
			}
		}
	}

	private static int findNearestSolid(World world, BlockPos.Mutable m, int centerY, int range) {
		for (int d = 0; d <= range; d++) {
			for (int sign : new int[]{0, 1, -1}) {
				if (d == 0 && sign != 0) continue;
				int y = centerY + d * (sign == 0 ? 0 : sign);
				if (sign == 0 && d > 0) continue;
				m.setY(y);
				BlockState st = world.getBlockState(m);
				if (!st.isAir() && st.getHardness(world, m) >= 0) return y;
			}
		}
		return Integer.MIN_VALUE;
	}

	public static void render(DrawContext ctx, MinecraftClient mc) {
		if (!open || mc.player == null || heights == null) return;
		int sw = mc.getWindow().getScaledWidth();
		int sh = mc.getWindow().getScaledHeight();
		int px = sw - PANEL_W - 10;
		int py = sh / 2 - PANEL_H / 2;

		// Panel
		ctx.fill(px - 2, py - 14, px + PANEL_W + 2, py + PANEL_H + 2, 0xCC000810);
		ctx.fill(px - 2, py - 14, px + PANEL_W + 2, py - 13, 0xFF1AFF7A);
		ctx.fill(px - 2, py + PANEL_H + 1, px + PANEL_W + 2, py + PANEL_H + 2, 0xFF1AFF7A);
		ctx.drawText(mc.textRenderer, Text.literal("PYRO GX · 3D MAP"), px, py - 11, 0xFF33FF88, false);
		ctx.drawText(mc.textRenderer, Text.literal("Tab+H"), px + PANEL_W - 36, py - 11, 0xAA22AA55, false);

		float yaw = yawView * ((float) Math.PI / 180f);
		float cos = MathHelper.cos(yaw);
		float sin = MathHelper.sin(yaw);
		// Isometric projection factors
		float sx = 3.2f;
		float sy = 1.55f;
		int ox = px + PANEL_W / 2;
		int oy = py + PANEL_H / 2 + 8;

		ShipAttitude att = DescentClientState.attitudeValid ? ShipAttitudeClient.get() : null;
		Vec3d up = att != null ? att.up() : new Vec3d(0, 1, 0);

		// Heightfield wire mesh
		for (int ix = 0; ix < GRID - 1; ix++) {
			for (int iz = 0; iz < GRID - 1; iz++) {
				int[] a = project(ix, iz, heights[ix][iz], cos, sin, sx, sy, ox, oy);
				int[] b = project(ix + 1, iz, heights[ix + 1][iz], cos, sin, sx, sy, ox, oy);
				int[] c = project(ix, iz + 1, heights[ix][iz + 1], cos, sin, sx, sy, ox, oy);
				int col = colorFor(kinds[ix][iz], heights[ix][iz], up.y);
				if (inPanel(a, px, py) || inPanel(b, px, py)) line(ctx, a[0], a[1], b[0], b[1], col);
				if (inPanel(a, px, py) || inPanel(c, px, py)) line(ctx, a[0], a[1], c[0], c[1], col);
			}
		}

		// Player pip at center
		ctx.fill(ox - 2, oy - 2, ox + 3, oy + 3, 0xFFFFFFFF);
		ctx.fill(ox - 1, oy - 5, ox + 2, oy - 2, 0xFF33FF88);

		// Macro / LLOD blips
		BlockPos origin = mc.player.getBlockPos();
		int half = (GRID * STEP) / 2;
		for (var e : LlodClientState.INSTANCE.entries()) {
			double lx = e.center().x - origin.getX();
			double lz = e.center().z - origin.getZ();
			double ly = e.center().y - origin.getY();
			if (lx * lx + lz * lz > (half + 24.0) * (half + 24.0)) continue;
			float rx = (float) (lx * cos - lz * sin);
			float rz = (float) (lx * sin + lz * cos);
			int x = ox + (int) ((rx - rz) * sx * 0.22f);
			int y = oy + (int) ((rx + rz) * sy * 0.22f - ly * 0.35f);
			if (x < px || x > px + PANEL_W || y < py || y > py + PANEL_H) continue;
			ctx.fill(x - 1, y - 1, x + 2, y + 2, 0xFF000000 | (e.colorRgb() & 0xFFFFFF));
		}

		var layer = WorldRules.practicalLayer(mc.player.getY());
		ctx.drawText(mc.textRenderer, Text.literal(WorldRules.biomeLabel(layer)), px + 4, py + PANEL_H - 10, 0xAA22AA55, false);
		String mode = mc.player.getVehicle() instanceof PyroShipEntity ? "PILOT" : "LINK";
		ctx.drawText(mc.textRenderer, Text.literal(mode), px + PANEL_W - 28, py + PANEL_H - 10, 0xFF33FF88, false);
	}

	private static int[] project(int ix, int iz, int h, float cos, float sin, float sx, float sy, int ox, int oy) {
		float cx = (ix - GRID / 2f) * STEP;
		float cz = (iz - GRID / 2f) * STEP;
		float rx = cx * cos - cz * sin;
		float rz = cx * sin + cz * cos;
		int x = ox + (int) ((rx - rz) * sx * 0.22f);
		int y = oy + (int) ((rx + rz) * sy * 0.22f - h * 0.45f);
		return new int[]{x, y};
	}

	private static boolean inPanel(int[] p, int px, int py) {
		return p[0] >= px && p[0] <= px + PANEL_W && p[1] >= py && p[1] <= py + PANEL_H;
	}

	private static int colorFor(byte kind, int h, double upY) {
		// Dimmer when ship is inverted (up.y < 0) — still readable
		int alpha = upY < -0.3 ? 0xAA000000 : 0xFF000000;
		return switch (kind) {
			case 2 -> alpha | 0x4488FF;
			case 3 -> alpha | 0xFFAA44;
			case 0 -> alpha | 0x224433;
			default -> {
				int g = MathHelper.clamp(80 + h * 3, 40, 200);
				yield alpha | (g << 8) | 0x330000 | MathHelper.clamp(60 + h, 20, 120);
			}
		};
	}

	private static void line(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
		int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1;
		int sy = y0 < y1 ? 1 : -1;
		int err = dx - dy;
		int x = x0, y = y0;
		int steps = 0;
		while (true) {
			ctx.fill(x, y, x + 1, y + 1, color);
			if (x == x1 && y == y1) break;
			if (++steps > 200) break;
			int e2 = 2 * err;
			if (e2 > -dy) { err -= dy; x += sx; }
			if (e2 < dx) { err += dx; y += sy; }
		}
	}
}
