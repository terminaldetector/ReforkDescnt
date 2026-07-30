package com.terminaldetector.drmd.client.hud;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.input.DescentKeybinds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Cockpit HUD — port of d6_cockpit.lua + d6_client.lua:
 * Shield/Hull/Energy bars, speed, roll, gravity warning, dash CD, weapon mode.
 */
public final class DescentHud {
	private DescentHud() {}

	public static void render(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.options.hudHidden || !DescentClientState.enabled) return;

		int sw = mc.getWindow().getScaledWidth();
		int sh = mc.getWindow().getScaledHeight();

		// Crosshair
		int cx = sw / 2, cy = sh / 2;
		ctx.fill(cx - 8, cy, cx + 9, cy + 1, 0xAA00DCFF);
		ctx.fill(cx, cy - 8, cx + 1, cy + 9, 0xAA00DCFF);

		int left = 12;
		int bottom = sh - 14;

		drawBar(ctx, left, bottom - 36, 120, 8, DescentClientState.shield / Math.max(1f, DescentClientState.shieldMax), 0xFF33AADD, "SHIELD");
		float hp = mc.player.getHealth() / mc.player.getMaxHealth();
		drawBar(ctx, left, bottom - 24, 120, 8, hp, 0xFFDD4433, "HULL");
		drawBar(ctx, left, bottom - 12, 120, 8, DescentClientState.energy / Math.max(1f, DescentClientState.energyMax), 0xFF44DD66, "ENERGY");

		// Right side info
		String[] lines = {
				String.format("SPD %.0f", DescentClientState.speed * 20),
				String.format("ROLL %.0f°", DescentClientState.roll),
				"PRESET " + DescentClientState.preset.toUpperCase(),
				"RKT " + rocketLabel(DescentClientState.rocketSub),
				DescentClientState.alwaysRun ? "ALWAYS-RUN" : "",
				DescentClientState.flightAssist ? "FA ON" : "FA OFF",
				String.format("GRAVY %.0f", DescentClientState.gravy)
		};
		int y = sh - 14 - lines.length * 10;
		for (String line : lines) {
			if (line == null || line.isEmpty()) { y += 10; continue; }
			ctx.drawTextWithShadow(mc.textRenderer, Text.literal(line), sw - 12 - mc.textRenderer.getWidth(line), y, 0xFFCCEEFF);
			y += 10;
		}

		// Dash CD
		if (DescentClientState.dashCd > 0) {
			float pct = 1f - DescentClientState.dashCd / 1.8f;
			drawBar(ctx, cx - 40, cy + 18, 80, 3, pct, 0xFFFFAA00, null);
		}

		// Gravity warning
		if (DescentClientState.gravityFactor <= 0 && false) {
			// reserved
		}
		// Approximate idle warning via gravityFactor approaching
		if (DescentClientState.gravityFactor > 0 && DescentClientState.gravityFactor < 1f) {
			String warn = String.format("⚠ GRAVITY RAMP %.0f%%", DescentClientState.gravityFactor * 100);
			ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(warn), cx, 20, 0xFFFFCC33);
		} else if (DescentClientState.gravityFactor >= 1f) {
			ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("⚠ GRAVITY ACTIVE"), cx, 20, 0xFFFF4422);
		}

		// Hint strip
		String hint = "[H] 6DOF  [Shift] Dash  [R] Afterburn  [Z] Hook  [M] Workshop";
		ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(hint), cx, sh - 10, 0x88AABBCC);
	}

	private static String rocketLabel(int sub) {
		return switch (sub) {
			case 1 -> "×3 VOLLEY";
			case 2 -> "×6 HOMING";
			case 3 -> "☢ ATOMIC";
			default -> "×1 DIRECT";
		};
	}

	private static void drawBar(DrawContext ctx, int x, int y, int w, int h, float pct, int color, String label) {
		pct = Math.max(0f, Math.min(1f, pct));
		ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x88000000);
		ctx.fill(x, y, x + w, y + h, 0xFF222222);
		ctx.fill(x, y, x + (int) (w * pct), y + h, color);
		if (label != null) {
			MinecraftClient mc = MinecraftClient.getInstance();
			ctx.drawTextWithShadow(mc.textRenderer, Text.literal(label), x + w + 6, y - 1, 0xFFCCDDEE);
		}
	}
}
