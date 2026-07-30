package com.terminaldetector.drmd.client.hud;

import com.terminaldetector.drmd.client.DescentClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Concept cockpit HUD — emerald tactical overlay (Descent / mockup style).
 * Keeps vanilla hotbar/hearts; draws flight readouts, 3D radar, target lock, weapons, logs.
 */
public final class DescentHud {
	private static final int GREEN = 0xFF33FF88;
	private static final int GREEN_DIM = 0xAA22AA55;
	private static final int GREEN_DARK = 0x66001108;
	private static final int RED = 0xFFFF4455;
	private static final int AMBER = 0xFFFFCC44;
	private static final int PANEL = 0x99000810;
	private static final int PANEL_EDGE = 0xFF1AFF7A;

	private static final ArrayDeque<String> LOG = new ArrayDeque<>();
	private static String lastTargetName = "";
	private static int logCooldown;

	private DescentHud() {}

	public static void pushLog(String line) {
		LOG.addFirst(line);
		while (LOG.size() > 5) LOG.removeLast();
	}

	public static void render(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.options.hudHidden || !DescentClientState.enabled) return;

		int sw = mc.getWindow().getScaledWidth();
		int sh = mc.getWindow().getScaledHeight();
		int cx = sw / 2;
		int cy = sh / 2;

		maybeCombatLog(mc);
		drawFlightStatus(ctx, mc, 8, 8);
		drawCoords(ctx, mc, 8, 62);
		drawAtmosphere(ctx, mc, 8, 94);
		drawRadar(ctx, mc, sw - 78, 8, 64);
		TargetInfo target = resolveTarget(mc);
		drawTargetPanel(ctx, mc, 8, 120, target);
		drawWeapons(ctx, mc, sw - 120, 90);
		drawDefense(ctx, mc, sw - 120, sh - 88);
		drawCrosshair(ctx, cx, cy, target != null);
		drawCockpitPanels(ctx, cx, sh - 54);
		drawCombatLog(ctx, mc, 8, sh - 78);
		drawDashBar(ctx, cx, cy);

		if (DescentClientState.gravityFactor > 0.05f) {
			String warn = DescentClientState.gravityFactor >= 1f
					? "⚠ GRAVITY ACTIVE"
					: String.format(Locale.ROOT, "⚠ GRAVITY RAMP %.0f%%", DescentClientState.gravityFactor * 100);
			ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(warn), cx, 4, AMBER);
		}
	}

	private static void drawFlightStatus(DrawContext ctx, MinecraftClient mc, int x, int y) {
		panel(ctx, x - 2, y - 2, 118, 52);
		float thrust = MathHelper.clamp(DescentClientState.speed / 2.5f, 0f, 1f);
		line(ctx, mc, x, y, "6DOF MODE: ON", GREEN);
		line(ctx, mc, x, y + 10, String.format(Locale.ROOT, "THRUST: %d%%", (int) (thrust * 100)), GREEN);
		line(ctx, mc, x, y + 20, "DAMPENERS: " + (DescentClientState.flightAssist ? "ON" : "OFF"), GREEN);
		line(ctx, mc, x, y + 30, String.format(Locale.ROOT, "GRAVITY: %.2fG", DescentClientState.gravityFactor), GREEN);
		line(ctx, mc, x, y + 40, String.format(Locale.ROOT, "SPEED: %.1f m/s", DescentClientState.speed * 20f), GREEN);
	}

	private static void drawCoords(DrawContext ctx, MinecraftClient mc, int x, int y) {
		var p = mc.player;
		line(ctx, mc, x, y, String.format(Locale.ROOT, "X %.1f", p.getX()), GREEN_DIM);
		line(ctx, mc, x, y + 9, String.format(Locale.ROOT, "Y %.1f", p.getY()), GREEN_DIM);
		line(ctx, mc, x, y + 18, String.format(Locale.ROOT, "Z %.1f", p.getZ()), GREEN_DIM);
	}

	private static void drawAtmosphere(DrawContext ctx, MinecraftClient mc, int x, int y) {
		var band = com.terminaldetector.drmd.world.atmosphere.AtmosphereBand.at(mc.player.getY());
		int color = switch (band) {
			case NEAR_SPACE -> 0xFF88CCFF;
			case THIN -> 0xFFAAEEFF;
			case DEEP_PRESSURE -> AMBER;
			default -> GREEN_DIM;
		};
		line(ctx, mc, x, y, "ATM: " + band.label.toUpperCase(Locale.ROOT), color);
		if (DescentClientState.smokeObscurity > 0.08f) {
			line(ctx, mc, x, y + 9, String.format(Locale.ROOT, "SMOKE %d%%",
					(int) (DescentClientState.smokeObscurity * 100)), AMBER);
		}
	}

	private static void drawRadar(DrawContext ctx, MinecraftClient mc, int x, int y, int size) {
		panel(ctx, x - 2, y - 2, size + 4, size + 14);
		line(ctx, mc, x, y, "RADAR 3D", GREEN);
		int ox = x + size / 2;
		int oy = y + 10 + size / 2;
		int r = size / 2 - 2;
		// Sphere rings
		ring(ctx, ox, oy, r, GREEN_DIM);
		ring(ctx, ox, oy, r * 2 / 3, GREEN_DIM);
		ring(ctx, ox, oy, r / 3, GREEN_DIM);
		ctx.fill(ox - r, oy, ox + r + 1, oy + 1, GREEN_DIM);
		ctx.fill(ox, oy - r, ox + 1, oy + r + 1, GREEN_DIM);
		ctx.fill(ox - 1, oy - 1, ox + 2, oy + 2, GREEN);

		if (!DescentClientState.radar || mc.world == null) return;
		double range = 96 * (1.0 - DescentClientState.smokeObscurity * 0.7);
		for (Entity e : mc.world.getEntities()) {
			if (e == mc.player || !(e instanceof LivingEntity) || !e.isAlive()) continue;
			Vec3d rel = e.getPos().subtract(mc.player.getPos());
			if (rel.lengthSquared() > range * range) continue;
			// Project into local view plane (yaw only for 2D scope + height tint)
			float yaw = mc.player.getYaw() * ((float) Math.PI / 180f);
			double lx = rel.x * Math.cos(yaw) + rel.z * Math.sin(yaw);
			double lz = -rel.x * Math.sin(yaw) + rel.z * Math.cos(yaw);
			double ly = rel.y;
			int px = ox + (int) (lx / range * r);
			int py = oy + (int) (lz / range * r) - (int) (ly / range * (r / 2.0));
			if (px < ox - r || px > ox + r || py < oy - r || py > oy + r) continue;
			boolean hostile = e instanceof net.minecraft.entity.mob.HostileEntity;
			ctx.fill(px - 1, py - 1, px + 2, py + 2, hostile ? RED : GREEN);
		}
	}

	private static void drawTargetPanel(DrawContext ctx, MinecraftClient mc, int x, int y, TargetInfo t) {
		panel(ctx, x - 2, y - 2, 130, 58);
		line(ctx, mc, x, y, "TARGET INFO", GREEN);
		if (t == null) {
			line(ctx, mc, x, y + 12, "NO LOCK", GREEN_DIM);
			wireBox(ctx, x + 4, y + 28, 28, 22, GREEN_DIM);
			return;
		}
		line(ctx, mc, x, y + 11, t.name, GREEN);
		line(ctx, mc, x, y + 21, String.format(Locale.ROOT, "HP %d/%d", t.hp, t.hpMax), t.hp < t.hpMax / 3 ? RED : GREEN);
		line(ctx, mc, x, y + 31, String.format(Locale.ROOT, "DIST %dm", t.dist), GREEN_DIM);
		line(ctx, mc, x, y + 41, String.format(Locale.ROOT, "VEL %.1f", t.vel), GREEN_DIM);
		wireBox(ctx, x + 96, y + 14, 28, 36, GREEN);
		// Simple wireframe silhouette
		ctx.fill(x + 108, y + 22, x + 112, y + 42, GREEN);
		ctx.fill(x + 100, y + 30, x + 120, y + 33, GREEN);
	}

	private static void drawWeapons(DrawContext ctx, MinecraftClient mc, int x, int y) {
		panel(ctx, x - 2, y - 2, 112, 72);
		line(ctx, mc, x, y, "WEAPONS", GREEN);
		String[] slots = {
				"1 CANNON   ∞",
				"2 PLASMA  " + ammoFromEnergy(0.8f),
				"3 MISSILE " + ammoFromEnergy(0.4f),
				"4 GRAVY   " + String.format(Locale.ROOT, "%02.0f", DescentClientState.gravy)
		};
		for (int i = 0; i < slots.length; i++) {
			int color = (i == Math.min(3, DescentClientState.rocketSub)) ? GREEN : GREEN_DIM;
			line(ctx, mc, x, y + 12 + i * 12, slots[i], color);
		}
	}

	private static void drawDefense(DrawContext ctx, MinecraftClient mc, int x, int y) {
		panel(ctx, x - 2, y - 2, 112, 56);
		float shield = DescentClientState.shield / Math.max(1f, DescentClientState.shieldMax);
		float armor = mc.player.getHealth() / mc.player.getMaxHealth();
		line(ctx, mc, x, y, String.format(Locale.ROOT, "SHIELD %d%%", (int) (shield * 100)), 0xFF44AAFF);
		segBar(ctx, x, y + 10, 100, 6, shield, 0xFF3399EE);
		line(ctx, mc, x, y + 20, String.format(Locale.ROOT, "ARMOR  %d%%", (int) (armor * 100)), 0xFF66FFCC);
		segBar(ctx, x, y + 30, 100, 6, armor, 0xFF44DDBB);
		// Ship top-down silhouette
		ctx.fill(x + 40, y + 40, x + 60, y + 42, GREEN);
		ctx.fill(x + 48, y + 38, x + 52, y + 48, GREEN);
		ctx.fill(x + 36, y + 44, x + 64, y + 46, GREEN_DIM);
	}

	private static void drawCombatLog(DrawContext ctx, MinecraftClient mc, int x, int y) {
		panel(ctx, x - 2, y - 2, 160, 48);
		int yy = y;
		for (String s : LOG) {
			line(ctx, mc, x, yy, s, GREEN_DIM);
			yy += 9;
		}
		if (LOG.isEmpty()) {
			line(ctx, mc, x, y, "[SYS] Descent link online", GREEN_DIM);
		}
	}

	private static void drawCockpitPanels(DrawContext ctx, int cx, int y) {
		// Three green instrument plates under crosshair (concept dashboard)
		int w = 48, h = 28, gap = 8;
		int x0 = cx - (w * 3 + gap * 2) / 2;
		for (int i = 0; i < 3; i++) {
			int px = x0 + i * (w + gap);
			panel(ctx, px, y, w, h);
			if (i == 0) {
				float t = MathHelper.clamp(DescentClientState.speed / 2.5f, 0, 1);
				segBar(ctx, px + 6, y + 10, w - 12, 8, t, GREEN);
			} else if (i == 1) {
				// LOCK diamond
				int mx = px + w / 2, my = y + h / 2;
				ctx.fill(mx - 6, my, mx + 7, my + 1, GREEN);
				ctx.fill(mx, my - 6, mx + 1, my + 7, GREEN);
				MinecraftClient mc = MinecraftClient.getInstance();
				ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("LOCK"), mx, my + 8, GREEN);
			} else {
				float e = DescentClientState.energy / Math.max(1f, DescentClientState.energyMax);
				segBar(ctx, px + 6, y + 10, w - 12, 8, e, 0xFF44FFAA);
			}
		}
	}

	private static void drawCrosshair(DrawContext ctx, int cx, int cy, boolean lock) {
		int c = lock ? RED : GREEN;
		// Outer brackets
		ctx.fill(cx - 14, cy - 1, cx - 6, cy + 2, c);
		ctx.fill(cx + 7, cy - 1, cx + 15, cy + 2, c);
		ctx.fill(cx - 1, cy - 14, cx + 2, cy - 6, c);
		ctx.fill(cx - 1, cy + 7, cx + 2, cy + 15, c);
		// Center pip
		ctx.fill(cx, cy, cx + 1, cy + 1, 0xFFFFFFFF);
		if (lock) {
			ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
					Text.literal("LOCK"), cx, cy - 22, RED);
		}
	}

	private static void drawDashBar(DrawContext ctx, int cx, int cy) {
		if (DescentClientState.dashCd <= 0) return;
		float pct = 1f - DescentClientState.dashCd / 1.8f;
		segBar(ctx, cx - 40, cy + 20, 80, 3, pct, AMBER);
	}

	private static TargetInfo resolveTarget(MinecraftClient mc) {
		if (mc.player == null || mc.world == null) return null;
		EntityHitResult hit = rayEntity(mc, 96);
		if (hit == null || !(hit.getEntity() instanceof LivingEntity living) || living == mc.player) return null;
		String name = living.getName().getString();
		if (!name.equals(lastTargetName)) {
			lastTargetName = name;
			pushLog("[SYS] Target lock: " + name);
		}
		return new TargetInfo(
				name,
				(int) living.getHealth(),
				(int) living.getMaxHealth(),
				(int) mc.player.distanceTo(living),
				(float) living.getVelocity().length() * 20f
		);
	}

	private static EntityHitResult rayEntity(MinecraftClient mc, double range) {
		Vec3d eye = mc.player.getEyePos();
		Vec3d end = eye.add(mc.player.getRotationVec(1f).multiply(range));
		Entity focused = null;
		double best = range;
		for (Entity e : mc.world.getOtherEntities(mc.player, mc.player.getBoundingBox().expand(range))) {
			if (!(e instanceof LivingEntity) || !e.isAlive()) continue;
			var box = e.getBoundingBox().expand(0.3);
			var opt = box.raycast(eye, end);
			if (opt.isPresent()) {
				double d = eye.distanceTo(opt.get());
				if (d < best) {
					best = d;
					focused = e;
				}
			}
		}
		// Also respect block occlusion lightly
		var blockHit = mc.world.raycast(new RaycastContext(eye, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
		if (blockHit.getType() == HitResult.Type.BLOCK && eye.distanceTo(blockHit.getPos()) + 0.5 < best) {
			return null;
		}
		return focused == null ? null : new EntityHitResult(focused);
	}

	private static void maybeCombatLog(MinecraftClient mc) {
		if (logCooldown > 0) {
			logCooldown--;
			return;
		}
		if (DescentClientState.shield < 30 && DescentClientState.shield > 0) {
			pushLog("[SYS] Shield critical");
			logCooldown = 80;
		} else if (DescentClientState.alwaysRun) {
			pushLog("[SYS] Afterburner engaged");
			logCooldown = 100;
		}
	}

	private static String ammoFromEnergy(float factor) {
		int n = (int) (DescentClientState.energy * factor);
		return String.format(Locale.ROOT, "%03d", Math.max(0, n));
	}

	private static void panel(DrawContext ctx, int x, int y, int w, int h) {
		ctx.fill(x, y, x + w, y + h, PANEL);
		ctx.fill(x, y, x + w, y + 1, PANEL_EDGE);
		ctx.fill(x, y + h - 1, x + w, y + h, PANEL_EDGE);
		ctx.fill(x, y, x + 1, y + h, PANEL_EDGE);
		ctx.fill(x + w - 1, y, x + w, y + h, PANEL_EDGE);
	}

	private static void line(DrawContext ctx, MinecraftClient mc, int x, int y, String s, int color) {
		ctx.drawText(mc.textRenderer, Text.literal(s), x, y, color, false);
	}

	private static void segBar(DrawContext ctx, int x, int y, int w, int h, float pct, int color) {
		pct = MathHelper.clamp(pct, 0f, 1f);
		ctx.fill(x, y, x + w, y + h, 0xFF102018);
		int segs = 12;
		int filled = (int) (segs * pct);
		int segW = Math.max(1, (w - (segs - 1)) / segs);
		for (int i = 0; i < segs; i++) {
			int sx = x + i * (segW + 1);
			int col = i < filled ? color : 0xFF1A2A22;
			ctx.fill(sx, y, sx + segW, y + h, col);
		}
	}

	private static void ring(DrawContext ctx, int cx, int cy, int r, int color) {
		for (int a = 0; a < 360; a += 8) {
			double rad = Math.toRadians(a);
			int x = cx + (int) (Math.cos(rad) * r);
			int y = cy + (int) (Math.sin(rad) * r);
			ctx.fill(x, y, x + 1, y + 1, color);
		}
	}

	private static void wireBox(DrawContext ctx, int x, int y, int w, int h, int color) {
		ctx.fill(x, y, x + w, y + 1, color);
		ctx.fill(x, y + h - 1, x + w, y + h, color);
		ctx.fill(x, y, x + 1, y + h, color);
		ctx.fill(x + w - 1, y, x + w, y + h, color);
	}

	private record TargetInfo(String name, int hp, int hpMax, int dist, float vel) {}
}
