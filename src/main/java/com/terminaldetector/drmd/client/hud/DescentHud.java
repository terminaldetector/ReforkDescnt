package com.terminaldetector.drmd.client.hud;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.DescentCamera;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.flight.ShipAttitude;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.weapon.registry.ArsenalCatalog;
import com.terminaldetector.drmd.world.bombardment.BombardmentItems;
import com.terminaldetector.drmd.world.bombardment.OrdnanceType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pyro GX cockpit HUD — emerald tactical overlay.
 *
 * <p>Layout mirrors the concept cockpit: flight block / coords / target dossier down the left,
 * 3D scope / armament / defense down the right, target bar on top, instrument cluster + ordnance
 * card along the bottom. Vanilla hotbar, hearts and hunger stay untouched underneath.
 *
 * <p>Panels are laid out against the live scaled resolution and dropped in priority order when the
 * screen is too small, so a GUI scale of 4 on 1080p still gets a readable cockpit.
 */
public final class DescentHud {
	private static final int GREEN = 0xFF33FF88;
	private static final int GREEN_DIM = 0xCC22AA55;
	private static final int RED = 0xFFFF4455;
	private static final int RED_DARK = 0xFF661119;
	private static final int AMBER = 0xFFFFCC44;
	private static final int CYAN = 0xFF44AAFF;
	private static final int PANEL = 0xA6000C10;
	private static final int PANEL_EDGE = 0xFF1AFF7A;
	private static final int BAR_EMPTY = 0xFF14231C;

	private static final int ENV_H = 34;
	private static final int PROJ_H = 34;
	private static final int ORD_H = 58;

	private static final ArrayDeque<String> LOG = new ArrayDeque<>();
	private static String lastTargetName = "";
	private static int logCooldown;

	private DescentHud() {}

	public static void pushLog(String line) {
		LOG.addFirst(line);
		while (LOG.size() > 6) LOG.removeLast();
	}

	public static void render(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null || mc.options.hudHidden) return;

		renderReactorSyncStrip(ctx, mc);

		if (!DescentClientState.enabled) return;
		if (!com.terminaldetector.drmd.client.config.DescentConfig.hud) return;

		int sw = mc.getWindow().getScaledWidth();
		int sh = mc.getWindow().getScaledHeight();
		int cx = sw / 2;
		int cy = sh / 2;

		maybeCombatLog();
		List<Entity> contacts = scanContacts(mc);
		TargetInfo target = resolveTarget(mc);

		// ---- left column, stacked from the top -------------------------------------------------
		int lx = 4;
		int ly = 4;
		ly = drawFlightStatus(ctx, mc, lx, ly) + 4;
		ly = drawCoords(ctx, mc, lx, ly) + 4;
		int leftTop = drawTargetPanel(ctx, mc, lx, ly, target) + 4;

		// ---- left column, stacked from the bottom; each panel yields when space runs out --------
		int leftBottom = sh - 4;
		if (leftBottom - ENV_H > leftTop) {
			leftBottom = drawEnvironment(ctx, mc, lx, leftBottom - ENV_H) - 4;
		}
		if (leftBottom - MAP_H > leftTop) {
			leftBottom = drawMinimap(ctx, mc, lx, leftBottom - MAP_H, contacts) - 4;
		}
		int logH = Math.min(66, leftBottom - leftTop);
		if (logH >= 26) {
			// Keep clear of the centre instrument cluster on narrow screens.
			int logW = MathHelper.clamp(cx - 100 - lx, 96, 150);
			drawCombatLog(ctx, mc, lx, leftBottom - logH, logW, logH);
		}

		// ---- right column ----------------------------------------------------------------------
		int radarSize = sw < 520 ? 58 : 72;
		int rx = sw - (radarSize + 8) - 4;
		int rightTop = drawRadar(ctx, mc, rx, 4, radarSize, contacts) + 4;
		int weaponsW = 108;
		int wx = sw - weaponsW - 4;
		rightTop = drawWeapons(ctx, mc, wx, rightTop, weaponsW) + 4;
		rightTop = drawDefense(ctx, mc, wx, rightTop, weaponsW) + 4;

		int rightBottom = sh - 4;
		if (rightBottom - PROJ_H > rightTop) {
			rightBottom = drawProjection(ctx, mc, wx, rightBottom - PROJ_H, weaponsW) - 4;
		}
		if (rightBottom - ORD_H > rightTop) {
			drawOrdnanceCard(ctx, mc, wx, rightBottom - ORD_H, weaponsW);
		}

		// ---- centre ----------------------------------------------------------------------------
		if (sw >= 320) drawTargetBar(ctx, mc, cx, 4, target);
		if (sw >= 380 && sh >= 260) drawInstrumentCluster(ctx, mc, cx, sh - 86);
		drawReticle(ctx, mc, cx, cy, target);
		drawAttitudeLadder(ctx, cx, cy);
		drawDashBar(ctx, cx, cy);

		if (!mc.options.getPerspective().isFirstPerson()) {
			ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("EXTERNAL · SHIP CAM"), cx, sh - 96, GREEN_DIM);
		}
		TerrainMap3d.render(ctx, mc);

		if (DescentClientState.gravityFactor > 0.05f) {
			String warn = DescentClientState.gravityFactor >= 1f
					? "⚠ GRAVITY ACTIVE"
					: String.format(Locale.ROOT, "⚠ GRAVITY RAMP %.0f%%", DescentClientState.gravityFactor * 100);
			ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(warn), cx, 18, AMBER);
		}
	}

	// =============================================================== left column

	private static int drawFlightStatus(DrawContext ctx, MinecraftClient mc, int x, int y) {
		int w = 116, h = 74;
		panel(ctx, x, y, w, h);
		int tx = x + 4, ty = y + 4;
		float thrust = MathHelper.clamp(DescentClientState.speed / 2.5f, 0f, 1f);
		var layer = com.terminaldetector.drmd.world.layer.WorldLayer.at(mc.world, mc.player.getY());
		line(ctx, mc, tx, ty, "6DOF MODE: ON", GREEN);
		line(ctx, mc, tx, ty + 11, String.format(Locale.ROOT, "THRUST: %d%%", Math.round(thrust * 100)), GREEN);
		int abTier = com.terminaldetector.drmd.flight.AfterburnerTiers.clamp(DescentClientState.afterburnerTier);
		int abColor = DescentClientState.alwaysRun
				? com.terminaldetector.drmd.flight.AfterburnerTiers.colorArgb(abTier)
				: GREEN_DIM;
		line(ctx, mc, tx, ty + 22, "AFTERBURNER: "
						+ (DescentClientState.alwaysRun ? "ON" : "OFF")
						+ " " + com.terminaldetector.drmd.flight.AfterburnerTiers.shortLabel(abTier),
				abColor);
		line(ctx, mc, tx, ty + 33, "DAMPENERS: " + (DescentClientState.flightAssist ? "ON" : "OFF"),
				DescentClientState.flightAssist ? GREEN : AMBER);
		line(ctx, mc, tx, ty + 44, String.format(Locale.ROOT, "SPEED: %.1f m/s", DescentClientState.speed * 20f), GREEN);
		line(ctx, mc, tx, ty + 55, "LAYER: " + layer.label.toUpperCase(Locale.ROOT), layer.hudColor | 0xFF000000);
		return y + h;
	}

	private static int drawCoords(DrawContext ctx, MinecraftClient mc, int x, int y) {
		int w = 116, h = 46;
		panel(ctx, x, y, w, h);
		var p = mc.player;
		int tx = x + 4, ty = y + 3;
		line(ctx, mc, tx, ty, "COORDS:", GREEN);
		line(ctx, mc, tx, ty + 10, String.format(Locale.ROOT, "X: %.1f", p.getX()), GREEN);
		line(ctx, mc, tx, ty + 20, String.format(Locale.ROOT, "Y: %.1f", p.getY()), GREEN);
		line(ctx, mc, tx, ty + 30, String.format(Locale.ROOT, "Z: %.1f", p.getZ()), GREEN);
		return y + h;
	}

	private static int drawTargetPanel(DrawContext ctx, MinecraftClient mc, int x, int y, TargetInfo t) {
		int w = 124, h = 70;
		panel(ctx, x, y, w, h);
		int tx = x + 4;
		line(ctx, mc, tx, y + 3, "TARGET INFO", RED);
		int thumbX = x + 4, thumbY = y + 14;
		wireBox(ctx, thumbX, thumbY, 34, 26, t == null ? GREEN_DIM : RED);
		if (t == null) {
			line(ctx, mc, tx + 40, y + 18, "NO LOCK", GREEN_DIM);
			line(ctx, mc, tx + 40, y + 29, "SCANNING…", GREEN_DIM);
			return y + h;
		}
		drawTargetThumb(ctx, thumbX + 17, thumbY + 13, t);
		int nx = tx + 40;
		line(ctx, mc, nx, y + 16, clip(mc, t.name.toUpperCase(Locale.ROOT), w - 46), RED);
		line(ctx, mc, nx, y + 27, "TYPE: " + t.type, GREEN);
		if (t.hpMax > 0) {
			int hpColor = t.hp * 3 < t.hpMax ? RED : GREEN;
			line(ctx, mc, tx, y + 42, String.format(Locale.ROOT, "HP: %d/%d", t.hp, t.hpMax), hpColor);
			segBar(ctx, tx, y + 52, w - 8, 4, t.hp / (float) t.hpMax, hpColor);
		} else {
			line(ctx, mc, tx, y + 42, "HP: —", GREEN_DIM);
		}
		line(ctx, mc, tx, y + 59, String.format(Locale.ROOT, "DIST: %dm", t.dist), GREEN);
		String vel = String.format(Locale.ROOT, "VEL: %.1f", t.vel);
		line(ctx, mc, x + w - 4 - mc.textRenderer.getWidth(vel), y + 59, vel, GREEN);
		return y + h;
	}

	/** Tiny procedural silhouette so the dossier reads at a glance without per-target art. */
	private static void drawTargetThumb(DrawContext ctx, int cx, int cy, TargetInfo t) {
		int c = RED;
		switch (t.type) {
			case "STRUCTURE" -> {
				for (int i = 0; i < 9; i++) {
					ctx.fill(cx - i, cy + 8 - i * 2, cx + i + 1, cy + 9 - i * 2, i % 2 == 0 ? c : RED_DARK);
				}
			}
			case "DRONE" -> {
				ctx.fill(cx - 3, cy - 3, cx + 4, cy + 4, c);
				ctx.fill(cx - 9, cy - 1, cx - 4, cy + 2, RED_DARK);
				ctx.fill(cx + 5, cy - 1, cx + 10, cy + 2, RED_DARK);
			}
			case "PLAYER" -> {
				ctx.fill(cx - 2, cy - 8, cx + 3, cy - 3, c);
				ctx.fill(cx - 4, cy - 2, cx + 5, cy + 5, c);
				ctx.fill(cx - 2, cy + 6, cx + 3, cy + 10, RED_DARK);
			}
			default -> {
				ctx.fill(cx - 2, cy - 7, cx + 3, cy + 8, c);
				ctx.fill(cx - 7, cy - 2, cx + 8, cy + 2, RED_DARK);
			}
		}
	}

	private static void drawCombatLog(DrawContext ctx, MinecraftClient mc, int x, int y, int w, int h) {
		panel(ctx, x, y, w, h);
		int yy = y + 4;
		if (LOG.isEmpty()) {
			line(ctx, mc, x + 4, yy, "[SYS] Descent link online", GREEN);
			return;
		}
		int rows = Math.max(1, (h - 6) / 10);
		for (String s : LOG) {
			if (rows-- <= 0) break;
			boolean critical = s.contains("critical") || s.contains("Direct hit");
			line(ctx, mc, x + 4, yy, clip(mc, s, w - 8), critical ? RED : GREEN);
			yy += 10;
		}
	}

	private static int drawEnvironment(DrawContext ctx, MinecraftClient mc, int x, int y) {
		int w = 116, h = 34;
		panel(ctx, x, y, w, h);
		int tx = x + 4;
		long dayTime = mc.world.getTimeOfDay() % 24000L;
		int hours = (int) ((dayTime / 1000L + 6L) % 24L);
		int minutes = (int) (dayTime % 1000L * 60L / 1000L);
		boolean day = hours >= 6 && hours < 18;
		boolean dusk = hours == 18 || hours == 5;
		line(ctx, mc, tx, y + 3, "BIOME: " + biomeName(mc), GREEN);
		line(ctx, mc, tx, y + 13, String.format(Locale.ROOT, "TIME: %02d:%02d", hours, minutes), GREEN);
		line(ctx, mc, tx, y + 23, "LIGHT: " + (day ? "DAY" : dusk ? "DUSK" : "NIGHT"), day ? GREEN : AMBER);
		return y;
	}

	private static String biomeName(MinecraftClient mc) {
		return mc.world.getBiome(mc.player.getBlockPos()).getKey()
				.map(k -> k.getValue().getPath().replace('_', ' ').toUpperCase(Locale.ROOT))
				.orElse("UNKNOWN");
	}

	// =============================================================== minimap

	private static final int MAP_CELLS = 36;
	private static final int MAP_STEP = 3;
	private static final int MAP_H = MAP_CELLS * 2 + 8 + 18; // terrain + armament strip
	/** North-up height/color cache — rotated on blit by live ship yaw (no full rescan per turn). */
	private static final int[] mapColors = new int[MAP_CELLS * MAP_CELLS];
	private static final short[] mapHeights = new short[MAP_CELLS * MAP_CELLS];
	private static final boolean[] mapReady = new boolean[MAP_CELLS * MAP_CELLS];
	private static int mapCursor;
	private static double mapOx, mapOz;
	private static boolean mapAnchored;
	private static final int MAP_BUDGET = 48; // heightmap samples per HUD frame

	private static int drawMinimap(DrawContext ctx, MinecraftClient mc, int x, int y, List<Entity> contacts) {
		int size = MAP_CELLS * 2;
		panel(ctx, x, y, size + 8, size + 8);
		int ox = x + 4, oy = y + 4;
		pumpMapScan(mc);
		float yaw = DescentClientState.attitudeValid
				? ShipAttitudeClient.get().yawDegrees()
				: mc.player.getYaw();
		float yawRad = yaw * ((float) Math.PI / 180f);
		double sin = Math.sin(yawRad), cos = Math.cos(yawRad);
		int half = MAP_CELLS / 2;
		double span = MAP_CELLS * MAP_STEP / 2.0;
		double py = mc.player.getY();

		// Ship-up blit from the north-up cache — turning only rotates the view, not the sampler.
		for (int gz = 0; gz < MAP_CELLS; gz++) {
			for (int gx = 0; gx < MAP_CELLS; gx++) {
				double lx = (gx - half) * MAP_STEP;
				double lz = -(gz - half) * MAP_STEP;
				double wx = mapOx + lx * cos + lz * sin;
				double wz = mapOz - lx * sin + lz * cos;
				int col = sampleMapColor(wx, wz, py);
				if (col == 0) continue;
				ctx.fill(ox + gx * 2, oy + gz * 2, ox + gx * 2 + 2, oy + gz * 2 + 2, col);
			}
		}
		// Contacts in live ship frame.
		for (Entity e : contacts) {
			double dx = e.getX() - mc.player.getX();
			double dz = e.getZ() - mc.player.getZ();
			double lx = dx * cos - dz * sin;
			double lz = dx * sin + dz * cos;
			if (Math.abs(lx) > span || Math.abs(lz) > span) continue;
			int px = ox + (int) ((lx + span) / (span * 2) * size);
			int pz = oy + (int) ((-lz + span) / (span * 2) * size);
			ctx.fill(px - 1, pz - 1, px + 2, pz + 2, hostile(e) ? RED : CYAN);
		}
		// Own ship always centred — terrain slides under the marker as the cache reanchors.
		int mx = ox + size / 2;
		int my = oy + size / 2;
		ctx.fill(mx - 1, my - 2, mx + 2, my + 3, 0xFFFFFFFF);
		ctx.fill(mx, my - 4, mx + 1, my - 1, GREEN);
		// Heading notch.
		ctx.fill(mx, my - 6, mx + 1, my - 4, AMBER);
		// Armament strip under the wireframe terrain (not only contact blips).
		int stripY = y + size + 10;
		int rockets = countAll(mc, ArsenalCatalog.hudMissileOrder());
		int mines = countAll(mc, ArsenalCatalog.hudMineOrder());
		String strip = String.format(Locale.ROOT, "RKT %02d  MINE %02d  NRG %03d",
				Math.min(99, rockets), Math.min(99, mines),
				(int) MathHelper.clamp(DescentClientState.energy, 0f, 999f));
		panel(ctx, x, stripY, Math.max(size + 8, mc.textRenderer.getWidth(strip) + 8), 14);
		line(ctx, mc, x + 4, stripY + 3, strip, GREEN);
		return y;
	}

	/**
	 * Budgeted north-up terrain fill. Reanchors when the pilot leaves the cache centre; slows
	 * sampling during hard barrel rolls so the cockpit stays smooth.
	 */
	private static void pumpMapScan(MinecraftClient mc) {
		double px = mc.player.getX();
		double pz = mc.player.getZ();
		if (!mapAnchored) {
			mapOx = px;
			mapOz = pz;
			mapAnchored = true;
			mapCursor = 0;
			java.util.Arrays.fill(mapReady, false);
		} else {
			double dx = px - mapOx;
			double dz = pz - mapOz;
			if (dx * dx + dz * dz > (MAP_STEP * 2.5) * (MAP_STEP * 2.5)) {
				mapOx = px;
				mapOz = pz;
				mapCursor = 0;
				java.util.Arrays.fill(mapReady, false);
			}
		}
		int budget = MAP_BUDGET;
		if (Math.abs(ShipAttitudeClient.rollSpeed()) > 90f) budget = 12;
		BlockPos.Mutable probe = new BlockPos.Mutable();
		int half = MAP_CELLS / 2;
		int total = MAP_CELLS * MAP_CELLS;
		for (int n = 0; n < budget; n++) {
			int idx = mapCursor % total;
			mapCursor++;
			int gx = idx % MAP_CELLS;
			int gz = idx / MAP_CELLS;
			int bx = MathHelper.floor(mapOx + (gx - half) * MAP_STEP);
			int bz = MathHelper.floor(mapOz + (gz - half) * MAP_STEP);
			if (!mc.world.isChunkLoaded(bx >> 4, bz >> 4)) {
				mapReady[idx] = false;
				mapColors[idx] = 0;
				continue;
			}
			int top = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, bx, bz);
			probe.set(bx, Math.max(mc.world.getBottomY(), top - 1), bz);
			var state = mc.world.getBlockState(probe);
			mapHeights[idx] = (short) top;
			if (state.isAir()) {
				mapColors[idx] = 0xFF061018;
			} else {
				int base = state.getMapColor(mc.world, probe).color;
				mapColors[idx] = 0xFF000000 | base;
			}
			mapReady[idx] = true;
		}
	}

	/** Bilinear-ish nearest cell from the north-up cache, height-shaded vs pilot altitude. */
	private static int sampleMapColor(double wx, double wz, double playerY) {
		int half = MAP_CELLS / 2;
		double fx = (wx - mapOx) / MAP_STEP + half;
		double fz = (wz - mapOz) / MAP_STEP + half;
		int gx = MathHelper.floor(fx);
		int gz = MathHelper.floor(fz);
		if (gx < 0 || gz < 0 || gx >= MAP_CELLS || gz >= MAP_CELLS) return 0;
		int idx = gz * MAP_CELLS + gx;
		if (!mapReady[idx]) return 0x66061018;
		int base = mapColors[idx];
		if (base == 0) return 0;
		double delta = MathHelper.clamp((mapHeights[idx] - playerY) / 48.0, -1.0, 1.0);
		int rgb = base & 0xFFFFFF;
		return 0xFF000000 | shade(rgb, (float) (0.70 + delta * 0.30));
	}

	private static int shade(int rgb, float k) {
		int r = MathHelper.clamp((int) (((rgb >> 16) & 0xFF) * k), 0, 255);
		int g = MathHelper.clamp((int) (((rgb >> 8) & 0xFF) * k), 0, 255);
		int b = MathHelper.clamp((int) ((rgb & 0xFF) * k), 0, 255);
		return (r << 16) | (g << 8) | b;
	}

	// =============================================================== right column

	private static int drawRadar(DrawContext ctx, MinecraftClient mc, int x, int y, int size, List<Entity> contacts) {
		int h = size + 20;
		panel(ctx, x, y, size + 8, h);
		int ox = x + 4 + size / 2;
		int oy = y + 14 + size / 2;
		int r = size / 2 - 4;

		ring(ctx, ox, oy, r, GREEN_DIM);
		ring(ctx, ox, oy, r * 2 / 3, GREEN_DIM);
		ring(ctx, ox, oy, r / 3, GREEN_DIM);
		ctx.fill(ox - r, oy, ox + r + 1, oy + 1, GREEN_DIM);
		ctx.fill(ox, oy - r, ox + 1, oy + r + 1, GREEN_DIM);

		var tr = mc.textRenderer;
		ctx.drawText(tr, Text.literal("+Y"), ox - 5, y + 4, GREEN, false);
		ctx.drawText(tr, Text.literal("-Y"), ox - 5, oy + r + 1, GREEN, false);
		ctx.drawText(tr, Text.literal("-X"), x + 1, oy - 4, GREEN, false);
		ctx.drawText(tr, Text.literal("+X"), x + size + 7 - tr.getWidth("+X"), oy - 4, GREEN, false);

		if (!DescentClientState.radar) {
			ctx.drawText(tr, Text.literal("OFF"), ox - tr.getWidth("OFF") / 2, oy - 4, AMBER, false);
			return y + h;
		}

		// Project into the ship frame so the scope stays true through loops and barrel rolls.
		ShipAttitude att = DescentClientState.attitudeValid ? ShipAttitudeClient.get() : null;
		Vec3d fwd = att != null ? att.forward() : mc.player.getRotationVec(1f);
		Vec3d up = att != null ? att.up() : new Vec3d(0, 1, 0);
		Vec3d right = fwd.crossProduct(up);
		if (right.lengthSquared() < 1e-8) right = new Vec3d(1, 0, 0);
		right = right.normalize();

		double range = 96 * (1.0 - DescentClientState.smokeObscurity * 0.7);
		Vec3d eye = mc.player.getEyePos();
		for (Entity e : contacts) {
			Vec3d rel = e.getPos().subtract(eye);
			double dist = rel.length();
			if (dist > range || dist < 1e-3) continue;
			// Depth (forward) drives the blip size, lateral/vertical drive the position.
			double lx = rel.dotProduct(right) / range;
			double ly = rel.dotProduct(up) / range;
			double lf = rel.dotProduct(fwd) / range;
			int px = ox - (int) (lx * r);
			int py = oy - (int) (ly * r);
			if (px < ox - r || px > ox + r || py < oy - r || py > oy + r) continue;
			int col = hostile(e) ? RED : CYAN;
			int s = lf > 0 ? 2 : 1;
			ctx.fill(px - s / 2, py - s / 2, px - s / 2 + s + 1, py - s / 2 + s + 1, col);
		}
		ctx.fill(ox - 1, oy - 1, ox + 2, oy + 2, 0xFFFFFFFF);
		return y + h;
	}

	private static int drawWeapons(DrawContext ctx, MinecraftClient mc, int x, int y, int w) {
		// Concept-style armament list: held primary + rocket / mine inventory counts.
		int h = 78;
		panel(ctx, x, y, w, h);
		line(ctx, mc, x + 4, y + 4, "WEAPONS", GREEN);
		ItemStack held = mc.player.getMainHandStack();
		String heldName = "—";
		boolean heldOpen = false;
		if (held.getItem() instanceof DescentWeaponItem dwi) {
			var entry = ArsenalCatalog.get(dwi.getDef().id);
			heldName = entry != null ? entry.hudSlot() : dwi.getDef().displayName;
			heldOpen = entry != null;
		}
		line(ctx, mc, x + 4, y + 15, "1. " + heldName, heldOpen ? GREEN : GREEN_DIM);
		String nrg = String.format(Locale.ROOT, "%03d", (int) MathHelper.clamp(DescentClientState.energy, 0f, 999f));
		line(ctx, mc, x + w - 4 - mc.textRenderer.getWidth(nrg), y + 15, nrg, CYAN);

		int rockets = countAll(mc, ArsenalCatalog.hudMissileOrder());
		int mines = countAll(mc, ArsenalCatalog.hudMineOrder());
		int bombs = countAll(mc, bombItems());
		String[] names = {"2. ROCKETS", "3. MINES", "4. BOMB", "5. SHIELD"};
		String[] counts = {
				String.format(Locale.ROOT, "%02d", Math.min(99, rockets)),
				String.format(Locale.ROOT, "%02d", Math.min(99, mines)),
				String.format(Locale.ROOT, "%02d", Math.min(99, bombs)),
				String.format(Locale.ROOT, "%02d", Math.min(99, countAll(mc, new Item[]{ModItems.SHIELD_ORB})))
		};
		ItemStack main = held;
		int selected = 0;
		if (main.getItem() instanceof DescentWeaponItem dwi) {
			String id = dwi.getDef().id;
			if (id.startsWith("rocket_")) selected = 1;
			else if (id.startsWith("mine_")) selected = 2;
		} else if (main.getItem() instanceof BombardmentItems.BombBayItem) selected = 3;
		for (int i = 0; i < names.length; i++) {
			int color = (i + 1) == selected ? GREEN : GREEN_DIM;
			line(ctx, mc, x + 4, y + 26 + i * 11, names[i], color);
			int cw = mc.textRenderer.getWidth(counts[i]);
			line(ctx, mc, x + w - 4 - cw, y + 26 + i * 11, counts[i], color);
		}
		return y + h;
	}

	private static int drawDefense(DrawContext ctx, MinecraftClient mc, int x, int y, int w) {
		int h = 74;
		panel(ctx, x, y, w, h);
		float shield = DescentClientState.shield / Math.max(1f, DescentClientState.shieldMax);
		float armor = mc.player.getHealth() / Math.max(1f, mc.player.getMaxHealth());
		line(ctx, mc, x + 4, y + 4, String.format(Locale.ROOT, "SHIELD: %d%%", Math.round(shield * 100)), CYAN);
		segBar(ctx, x + 4, y + 14, w - 8, 5, shield, CYAN);
		line(ctx, mc, x + 4, y + 23, String.format(Locale.ROOT, "ARMOR: %d%%", Math.round(armor * 100)),
				armor < 0.34f ? RED : GREEN);
		segBar(ctx, x + 4, y + 33, w - 8, 5, armor, armor < 0.34f ? RED : GREEN);
		drawShipWireframe(ctx, x + w / 2, y + 56);
		return y + h;
	}

	/** Wireframe hull that banks with the ship — doubles as an attitude read-out. */
	private static void drawShipWireframe(DrawContext ctx, int cx, int cy) {
		float roll = DescentCamera.hudRoll();
		float pitch = DescentClientState.pitch;
		double rr = Math.toRadians(roll);
		double sinR = Math.sin(rr), cosR = Math.cos(rr);
		double squash = Math.cos(Math.toRadians(MathHelper.clamp(pitch, -80f, 80f))) * 0.55 + 0.45;
		// Nose, wingtips, tail — local (x = right, y = down-on-screen).
		double[][] pts = {{0, -11}, {-13, 5}, {-4, 2}, {0, 9}, {4, 2}, {13, 5}};
		int[] px = new int[pts.length];
		int[] py = new int[pts.length];
		for (int i = 0; i < pts.length; i++) {
			double lx = pts[i][0];
			double ly = pts[i][1] * squash;
			px[i] = cx + (int) Math.round(lx * cosR - ly * sinR);
			py[i] = cy + (int) Math.round(lx * sinR + ly * cosR);
		}
		for (int i = 0; i < pts.length; i++) {
			int j = (i + 1) % pts.length;
			lineTo(ctx, px[i], py[i], px[j], py[j], GREEN);
		}
		lineTo(ctx, px[1], py[1], px[5], py[5], GREEN_DIM);
	}

	private static int drawProjection(DrawContext ctx, MinecraftClient mc, int x, int y, int w) {
		int h = 34;
		panel(ctx, x, y, w, h);
		int top = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, mc.player.getBlockX(), mc.player.getBlockZ());
		int alt = (int) Math.round(mc.player.getY() - top);
		var band = com.terminaldetector.drmd.world.atmosphere.AtmosphereBand.at(mc.player.getY());
		line(ctx, mc, x + 4, y + 3, "PROJECTION: 6DOF", GREEN);
		line(ctx, mc, x + 4, y + 13, "ATM: " + band.label.toUpperCase(Locale.ROOT), GREEN);
		line(ctx, mc, x + 4, y + 23, String.format(Locale.ROOT, "ALT: %dm", alt), alt < 8 ? AMBER : GREEN);
		return y;
	}

	/** Card for whatever is in hand: real ordnance or weapon stats, never invented numbers. */
	private static void drawOrdnanceCard(DrawContext ctx, MinecraftClient mc, int x, int y, int w) {
		int h = 58;
		ItemStack held = mc.player.getMainHandStack();
		panel(ctx, x, y, w, h);
		if (held.getItem() instanceof BombardmentItems.BombBayItem bomb) {
			OrdnanceType o = bomb.getOrdnance();
			line(ctx, mc, x + 4, y + 4, "BOMB", AMBER);
			line(ctx, mc, x + 4, y + 15, o.heavyCluster ? "HEAVY CLUSTER"
					: o.cluster ? "CLUSTER SPREAD"
					: o.rocket ? "ROCKET STRIKE"
					: o.incendiary ? "INCENDIARY" : "AREA DAMAGE", GREEN);
			line(ctx, mc, x + 4, y + 26, "BLAST: " + (o.blastPower >= 1.2f ? "HIGH"
					: o.blastPower >= 0.8f ? "MEDIUM" : "LOW"), GREEN);
			line(ctx, mc, x + 4, y + 36, String.format(Locale.ROOT, "RADIUS: %.0fm", o.blastPower * 8f), GREEN);
			line(ctx, mc, x + 4, y + 46, String.format(Locale.ROOT, "COUNT: %02d", held.getCount()), GREEN);
			ctx.drawItem(held, x + w - 20, y + h - 22);
		} else if (held.getItem() instanceof DescentWeaponItem weapon) {
			var def = weapon.getDef();
			line(ctx, mc, x + 4, y + 4, clip(mc, def.displayName.toUpperCase(Locale.ROOT), w - 8), AMBER);
			line(ctx, mc, x + 4, y + 15, def.category.toUpperCase(Locale.ROOT) + " · " + def.dmgClass.name(), GREEN);
			line(ctx, mc, x + 4, y + 26, String.format(Locale.ROOT, "DAMAGE: %.0f", def.damage), GREEN);
			line(ctx, mc, x + 4, y + 36, String.format(Locale.ROOT, "RADIUS: %.0fm", def.splashRadius / 16f), GREEN);
			line(ctx, mc, x + 4, y + 46, String.format(Locale.ROOT, "ENERGY: %.0f", def.energyCost),
					def.energyCost > DescentClientState.energy ? RED : GREEN);
			ctx.drawItem(held, x + w - 20, y + h - 22);
		} else {
			line(ctx, mc, x + 4, y + 4, "ARMAMENT", GREEN_DIM);
			line(ctx, mc, x + 4, y + 18, "NO ORDNANCE", GREEN_DIM);
			line(ctx, mc, x + 4, y + 30, "SELECT A DRMD", GREEN_DIM);
			line(ctx, mc, x + 4, y + 40, "WEAPON OR BOMB", GREEN_DIM);
		}
	}

	// =============================================================== centre

	private static void drawTargetBar(DrawContext ctx, MinecraftClient mc, int cx, int y, TargetInfo t) {
		if (t == null || t.hpMax <= 0) return;
		int w = 182;
		int x = cx - w / 2;
		panel(ctx, x, y, w, 24);
		ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(clip(mc, t.name.toUpperCase(Locale.ROOT), w - 8)),
				cx, y + 3, GREEN);
		float pct = MathHelper.clamp(t.hp / (float) t.hpMax, 0f, 1f);
		int bx = x + 5, by = y + 14, bw = w - 10, bh = 6;
		ctx.fill(bx, by, bx + bw, by + bh, BAR_EMPTY);
		ctx.fill(bx, by, bx + (int) (bw * pct), by + bh, pct < 0.34f ? RED : 0xFFCC2233);
		String hp = t.hp + "/" + t.hpMax;
		ctx.drawText(mc.textRenderer, Text.literal(hp), cx - mc.textRenderer.getWidth(hp) / 2, by - 1, 0xFFFFFFFF, true);
	}

	private static void drawInstrumentCluster(DrawContext ctx, MinecraftClient mc, int cx, int y) {
		int w = 60, h = 44, gap = 6;
		int x0 = cx - (w * 3 + gap * 2) / 2;

		// Left plate — thrust + afterburner state.
		panel(ctx, x0, y, w, h);
		ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("THRUST"), x0 + w / 2, y + 3, GREEN);
		float thrust = MathHelper.clamp(DescentClientState.speed / 2.5f, 0f, 1f);
		ctx.drawCenteredTextWithShadow(mc.textRenderer,
				Text.literal(Math.round(thrust * 100) + "%"), x0 + w / 2, y + 14, AMBER);
		vertBar(ctx, x0 + 8, y + 24, 8, 16, thrust, GREEN);
		int boostTier = com.terminaldetector.drmd.flight.AfterburnerTiers.clamp(DescentClientState.afterburnerTier);
		ctx.drawText(mc.textRenderer, Text.literal("BOOST "
						+ com.terminaldetector.drmd.flight.AfterburnerTiers.shortLabel(boostTier)),
				x0 + 20, y + 30,
				DescentClientState.alwaysRun
						? com.terminaldetector.drmd.flight.AfterburnerTiers.colorArgb(boostTier)
						: GREEN_DIM, false);

		// Middle plate — attitude MFD.
		int mx = x0 + w + gap;
		panel(ctx, mx, y, w, h);
		int mcx = mx + w / 2, mcy = y + 20;
		ring(ctx, mcx, mcy, 13, GREEN_DIM);
		ctx.fill(mcx - 13, mcy, mcx + 14, mcy + 1, GREEN_DIM);
		ctx.fill(mcx, mcy - 13, mcx + 1, mcy + 14, GREEN_DIM);
		float roll = DescentCamera.hudRoll();
		double rr = Math.toRadians(roll);
		int hx = (int) (Math.cos(rr) * 11);
		int hy = (int) (Math.sin(rr) * 11);
		lineTo(ctx, mcx - hx, mcy - hy, mcx + hx, mcy + hy, GREEN);
		boolean lock = !lastTargetName.isEmpty();
		ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("LOCK"), mcx, y + h - 11,
				lock ? RED : GREEN_DIM);

		// Right plate — energy + dampeners.
		int ex = mx + w + gap;
		panel(ctx, ex, y, w, h);
		ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("ENERGY"), ex + w / 2, y + 3, GREEN);
		float energy = DescentClientState.energy / Math.max(1f, DescentClientState.energyMax);
		ctx.drawCenteredTextWithShadow(mc.textRenderer,
				Text.literal(Math.round(energy * 100) + "%"), ex + w / 2, y + 14, AMBER);
		vertBar(ctx, ex + 8, y + 24, 8, 16, energy, 0xFF44FFAA);
		ctx.drawText(mc.textRenderer, Text.literal("DAMP."), ex + 20, y + 30,
				DescentClientState.flightAssist ? GREEN : GREEN_DIM, false);
	}

	private static void drawReticle(DrawContext ctx, MinecraftClient mc, int cx, int cy, TargetInfo t) {
		boolean lock = t != null;
		int c = lock ? RED : GREEN;
		ring(ctx, cx, cy, 11, c);
		ctx.fill(cx - 16, cy, cx - 12, cy + 1, c);
		ctx.fill(cx + 13, cy, cx + 17, cy + 1, c);
		ctx.fill(cx, cy - 16, cx + 1, cy - 12, c);
		ctx.fill(cx, cy + 13, cx + 1, cy + 17, c);
		ctx.fill(cx, cy, cx + 1, cy + 1, 0xFFFFFFFF);
		if (lock) {
			ring(ctx, cx, cy, 17, RED_DARK);
			ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("LOCK"), cx, cy - 30, RED);
		}
	}

	private static void drawAttitudeLadder(DrawContext ctx, int cx, int cy) {
		if (!DescentClientState.attitudeValid) return;
		float roll = DescentCamera.hudRoll();
		float pitch = DescentClientState.pitch;
		double rad = Math.toRadians(roll);
		int hw = 40;
		int hx = (int) (Math.cos(rad) * hw);
		int hy = (int) (Math.sin(rad) * hw);
		int py = (int) MathHelper.clamp(pitch * 0.35f, -22, 22);
		lineTo(ctx, cx - hx - 24, cy + py - hy, cx - hx, cy + py - hy, GREEN_DIM);
		lineTo(ctx, cx + hx, cy + py + hy, cx + hx + 24, cy + py + hy, GREEN_DIM);
	}

	private static void drawDashBar(DrawContext ctx, int cx, int cy) {
		if (DescentClientState.dashCd <= 0) return;
		float pct = 1f - DescentClientState.dashCd / 1.8f;
		segBar(ctx, cx - 40, cy + 26, 80, 3, pct, AMBER);
	}

	// =============================================================== targeting

	private static List<Entity> contactCache = List.of();
	private static long contactStamp = Long.MIN_VALUE;

	/** Radar + minimap share one sweep, refreshed on a tick budget rather than per frame. */
	private static List<Entity> scanContacts(MinecraftClient mc) {
		long now = mc.world.getTime();
		if (contactStamp != Long.MIN_VALUE && now - contactStamp < 4) return contactCache;
		contactStamp = now;
		List<Entity> out = new ArrayList<>();
		var box = mc.player.getBoundingBox().expand(96);
		for (Entity e : mc.world.getOtherEntities(mc.player, box)) {
			if (!(e instanceof LivingEntity) || !e.isAlive()) continue;
			out.add(e);
			if (out.size() >= 128) break;
		}
		contactCache = out;
		return out;
	}

	private static boolean hostile(Entity e) {
		// DroneEntity extends HostileEntity, so the whole DRMD roster is covered here.
		return e instanceof net.minecraft.entity.mob.HostileEntity;
	}

	private static TargetInfo targetCache;
	private static long targetStamp = Long.MIN_VALUE;

	private static TargetInfo resolveTarget(MinecraftClient mc) {
		long now = mc.world.getTime();
		// One raycast + entity sweep per game tick is enough; HUD may paint at 120+ fps.
		if (targetStamp == now) return targetCache;
		targetStamp = now;

		Vec3d eye = mc.player.getEyePos();
		Vec3d dir = DescentClientState.attitudeValid
				? ShipAttitudeClient.get().forward()
				: mc.player.getRotationVec(1f);
		Vec3d end = eye.add(dir.multiply(160));

		BlockHitResult blockHit = mc.world.raycast(new RaycastContext(eye, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
		double blockDist = blockHit.getType() == HitResult.Type.BLOCK ? eye.distanceTo(blockHit.getPos()) : Double.MAX_VALUE;

		EntityHitResult hit = rayEntity(mc, eye, end, Math.min(blockDist + 0.5, 160));
		if (hit != null && hit.getEntity() instanceof LivingEntity living) {
			String name = living.getName().getString();
			if (!name.equals(lastTargetName)) {
				lastTargetName = name;
				pushLog("[SYS] Target lock: " + name);
			}
			targetCache = new TargetInfo(
					name,
					entityType(living),
					(int) living.getHealth(),
					(int) living.getMaxHealth(),
					(int) mc.player.distanceTo(living),
					(float) living.getVelocity().length() * 20f);
			return targetCache;
		}
		lastTargetName = "";
		if (blockHit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = blockHit.getBlockPos();
			var state = mc.world.getBlockState(pos);
			targetCache = new TargetInfo(
					state.getBlock().getName().getString(),
					"STRUCTURE",
					0, 0,
					(int) blockDist,
					0f);
			return targetCache;
		}
		targetCache = null;
		return null;
	}

	private static String entityType(LivingEntity e) {
		if (e instanceof com.terminaldetector.drmd.entity.DroneEntity) return "DRONE";
		if (e instanceof net.minecraft.entity.player.PlayerEntity) return "PLAYER";
		if (e instanceof net.minecraft.entity.mob.HostileEntity) return "HOSTILE";
		return "CONTACT";
	}

	private static EntityHitResult rayEntity(MinecraftClient mc, Vec3d eye, Vec3d end, double maxDist) {
		Entity focused = null;
		double best = maxDist;
		for (Entity e : mc.world.getOtherEntities(mc.player, mc.player.getBoundingBox().stretch(end.subtract(eye)).expand(2))) {
			if (!(e instanceof LivingEntity) || !e.isAlive()) continue;
			var opt = e.getBoundingBox().expand(0.3).raycast(eye, end);
			if (opt.isPresent()) {
				double d = eye.distanceTo(opt.get());
				if (d < best) {
					best = d;
					focused = e;
				}
			}
		}
		return focused == null ? null : new EntityHitResult(focused);
	}

	private static void maybeCombatLog() {
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

	// =============================================================== ammo counting

	private static Item[] missileItems;
	private static Item[] bombItems;

	private static Item[] missileItems() {
		if (missileItems == null) {
			missileItems = new Item[]{ModItems.ROCKETS, ModItems.HOMING, ModItems.CONCUSSION,
					ModItems.SMART_MISSILE, ModItems.MEGA_MISSILE};
		}
		return missileItems;
	}

	private static Item[] bombItems() {
		if (bombItems == null) {
			bombItems = new Item[]{ModItems.BOMB_TNT, ModItems.BOMB_CLUSTER,
					ModItems.BOMB_HEAVY_CLUSTER, ModItems.BOMB_ROCKET,
					ModItems.BOMB_INCENDIARY, ModItems.BOMB_GUIDED};
		}
		return bombItems;
	}

	private static int countAll(MinecraftClient mc, Item[] items) {
		var inv = mc.player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			for (Item item : items) {
				if (item != null && stack.isOf(item)) {
					n += stack.getCount();
					break;
				}
			}
		}
		return n;
	}

	// =============================================================== primitives

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

	private static String clip(MinecraftClient mc, String s, int maxWidth) {
		if (mc.textRenderer.getWidth(s) <= maxWidth) return s;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			if (mc.textRenderer.getWidth(sb.toString() + s.charAt(i) + "…") > maxWidth) break;
			sb.append(s.charAt(i));
		}
		return sb + "…";
	}

	private static void segBar(DrawContext ctx, int x, int y, int w, int h, float pct, int color) {
		pct = MathHelper.clamp(pct, 0f, 1f);
		ctx.fill(x, y, x + w, y + h, BAR_EMPTY);
		int segs = 12;
		int filled = Math.round(segs * pct);
		int segW = Math.max(1, (w - (segs - 1)) / segs);
		for (int i = 0; i < segs; i++) {
			int sx = x + i * (segW + 1);
			ctx.fill(sx, y, sx + segW, y + h, i < filled ? color : 0xFF1A2A22);
		}
	}

	private static void vertBar(DrawContext ctx, int x, int y, int w, int h, float pct, int color) {
		pct = MathHelper.clamp(pct, 0f, 1f);
		ctx.fill(x, y, x + w, y + h, BAR_EMPTY);
		int fill = Math.round(h * pct);
		ctx.fill(x, y + h - fill, x + w, y + h, color);
		wireBox(ctx, x - 1, y - 1, w + 2, h + 2, GREEN_DIM);
	}

	private static void ring(DrawContext ctx, int cx, int cy, int r, int color) {
		if (r <= 0) return;
		int steps = Math.max(16, r * 6);
		for (int i = 0; i < steps; i++) {
			double rad = Math.PI * 2 * i / steps;
			int x = cx + (int) Math.round(Math.cos(rad) * r);
			int y = cy + (int) Math.round(Math.sin(rad) * r);
			ctx.fill(x, y, x + 1, y + 1, color);
		}
	}

	private static void wireBox(DrawContext ctx, int x, int y, int w, int h, int color) {
		ctx.fill(x, y, x + w, y + 1, color);
		ctx.fill(x, y + h - 1, x + w, y + h, color);
		ctx.fill(x, y, x + 1, y + h, color);
		ctx.fill(x + w - 1, y, x + w, y + h, color);
	}

	/** Bresenham segment — the GUI layer only gives axis-aligned rectangles. */
	private static void lineTo(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
		int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
		int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
		int err = dx + dy;
		int guard = 0;
		while (guard++ < 512) {
			ctx.fill(x0, y0, x0 + 1, y0 + 1, color);
			if (x0 == x1 && y0 == y1) break;
			int e2 = 2 * err;
			if (e2 >= dy) { err += dy; x0 += sx; }
			if (e2 <= dx) { err += dx; y0 += sy; }
		}
	}

	/** Column / End / orbit aftermath cue — visible even outside 6DoF. */
	private static void renderReactorSyncStrip(DrawContext ctx, MinecraftClient mc) {
		var sync = com.terminaldetector.drmd.client.sync.ClientReactorSync.INSTANCE;
		int breachSec = sync.nearestBreachSeconds(
				mc.player.getX(), mc.player.getY(), mc.player.getZ());
		int falls = sync.falls().size();
		if (breachSec < 0 && falls <= 0 && sync.lastGameTime() < 0) return;

		int sw = mc.getWindow().getScaledWidth();
		String line;
		int color;
		if (breachSec >= 0) {
			line = "REACTOR BREACH  " + breachSec + "s";
			color = breachSec <= 15 ? RED : AMBER;
		} else if (falls > 0) {
			line = "METEOR FALLS  " + falls;
			color = AMBER;
		} else {
			line = sync.lastOrbital() ? "ORBITAL DETONATION LOGGED" : "FACILITY DETONATION LOGGED";
			color = GREEN_DIM;
		}
		int tw = mc.textRenderer.getWidth(line);
		int x = (sw - tw) / 2;
		int y = 6;
		ctx.fill(x - 4, y - 2, x + tw + 4, y + 11, 0x88000C10);
		ctx.drawText(mc.textRenderer, Text.literal(line), x, y, color, false);
	}

	private record TargetInfo(String name, String type, int hp, int hpMax, int dist, float vel) {}
}
