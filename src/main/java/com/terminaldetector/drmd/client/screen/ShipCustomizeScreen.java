package com.terminaldetector.drmd.client.screen;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.flight.AfterburnerTiers;
import com.terminaldetector.drmd.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * Afterburner + ship customize.
 *
 * <ul>
 *   <li>Survival — lightweight: afterburner tier (traffic light) + energy presets</li>
 *   <li>Creative — fuller: same + accel / drag / max-speed tuners</li>
 * </ul>
 */
public class ShipCustomizeScreen extends Screen {
	private static final int ROW = 22;
	private final Screen parent;
	private final boolean creative;

	public ShipCustomizeScreen(Screen parent) {
		super(Text.translatable("screen.drmd.ship_customize"));
		this.parent = parent;
		boolean c = false;
		if (net.minecraft.client.MinecraftClient.getInstance().player != null) {
			c = net.minecraft.client.MinecraftClient.getInstance().player.isCreative();
		}
		this.creative = c;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = Math.max(36, this.height / 2 - (creative ? 110 : 70));

		// --- Afterburner traffic light ---
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.drmd.afterburner.header"), b -> {})
				.dimensions(cx - 100, y, 200, 18).build()).active = false;
		y += ROW;

		int tier = AfterburnerTiers.clamp(DescentClientState.afterburnerTier);
		int bx = cx - 152;
		for (int t = 1; t <= 4; t++) {
			final int ft = t;
			boolean sel = ft == tier;
			ButtonWidget btn = ButtonWidget.builder(
							Text.translatable("screen.drmd.afterburner.tier." + AfterburnerTiers.colorName(ft)),
							b -> {
								ClientPlayNetworking.send(new ModNetworking.ActionPayload("ab_" + ft));
								DescentClientState.afterburnerTier = ft;
								rebuild();
							})
					.dimensions(bx + (t - 1) * 78, y, 74, 20)
					.build();
			btn.active = !sel;
			addDrawableChild(btn);
		}
		y += ROW + 4;

		addDrawableChild(ButtonWidget.builder(
						Text.translatable("screen.drmd.afterburner.stats",
								AfterburnerTiers.clamp(DescentClientState.afterburnerTier),
								String.format("%.0f", AfterburnerTiers.costPerSec(DescentClientState.afterburnerTier))),
						b -> {})
				.dimensions(cx - 120, y, 240, 18).build()).active = false;
		y += ROW + 6;

		// --- Energy presets (both modes) ---
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.drmd.preset.header"), b -> {})
				.dimensions(cx - 100, y, 200, 18).build()).active = false;
		y += ROW;
		String[] presets = {"balanced", "assault", "interceptor", "siege"};
		int px = cx - 152;
		for (int i = 0; i < presets.length; i++) {
			String id = presets[i];
			boolean sel = id.equalsIgnoreCase(DescentClientState.preset);
			ButtonWidget btn = ButtonWidget.builder(
							Text.translatable("screen.drmd.preset." + id),
							b -> {
								ClientPlayNetworking.send(new ModNetworking.ActionPayload("preset_" + id));
								DescentClientState.preset = id;
								rebuild();
							})
					.dimensions(px + i * 78, y, 74, 20)
					.build();
			btn.active = !sel;
			addDrawableChild(btn);
		}
		y += ROW + 10;

		if (creative) {
			addDrawableChild(ButtonWidget.builder(Text.translatable("screen.drmd.ship.creative_header"), b -> {})
					.dimensions(cx - 100, y, 200, 18).build()).active = false;
			y += ROW;
			addDrawableChild(new TuneSlider(cx - 155, y, "screen.drmd.ship.accel",
					DescentClientState.accel, 2000f, 8000f, "accel"));
			addDrawableChild(new TuneSlider(cx + 5, y, "screen.drmd.ship.drag",
					DescentClientState.drag, 0.5f, 5.0f, "drag"));
			y += ROW;
			addDrawableChild(new TuneSlider(cx - 75, y, "screen.drmd.ship.maxspeed",
					DescentClientState.maxSpeed, 800f, 4000f, "maxspeed"));
			y += ROW + 8;
		} else {
			addDrawableChild(ButtonWidget.builder(Text.translatable("screen.drmd.ship.survival_hint"), b -> {})
					.dimensions(cx - 140, y, 280, 18).build()).active = false;
			y += ROW + 8;
		}

		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
				.dimensions(cx - 100, Math.min(this.height - 28, y), 200, 20).build());
	}

	private void rebuild() {
		this.clearChildren();
		this.init();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		int tier = AfterburnerTiers.clamp(DescentClientState.afterburnerTier);
		int color = AfterburnerTiers.colorArgb(tier);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, color);
		Text mode = Text.translatable(creative
				? "screen.drmd.ship.mode_creative"
				: "screen.drmd.ship.mode_survival");
		context.drawCenteredTextWithShadow(this.textRenderer, mode, this.width / 2, 24, 0x88AAAAAA);

		// Traffic-light strip under title
		int cx = this.width / 2;
		int sx = cx - 40;
		for (int t = 1; t <= 4; t++) {
			int c = AfterburnerTiers.colorArgb(t);
			boolean on = t == tier;
			context.fill(sx + (t - 1) * 22, 28, sx + (t - 1) * 22 + 18, 34, on ? c : (c & 0x55FFFFFF));
		}
	}

	@Override
	public void close() {
		if (this.client != null) this.client.setScreen(parent);
	}

	/** Creative physics tuner — sends {@code accel:N} / {@code drag:N} / {@code maxspeed:N}. */
	private static final class TuneSlider extends SliderWidget {
		private final String key;
		private final float min;
		private final float max;
		private final String actionPrefix;

		TuneSlider(int x, int y, String key, float value, float min, float max, String actionPrefix) {
			super(x, y, 150, 20, Text.empty(), (value - min) / (max - min));
			this.key = key;
			this.min = min;
			this.max = max;
			this.actionPrefix = actionPrefix;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			float v = (float) (min + (max - min) * this.value);
			setMessage(Text.translatable(key, String.format(java.util.Locale.ROOT, "%.1f", v)));
		}

		@Override
		protected void applyValue() {
			float v = (float) (min + (max - min) * this.value);
			switch (actionPrefix) {
				case "accel" -> DescentClientState.accel = v;
				case "drag" -> DescentClientState.drag = v;
				case "maxspeed" -> DescentClientState.maxSpeed = v;
			}
			ClientPlayNetworking.send(new ModNetworking.ActionPayload(
					actionPrefix + ":" + String.format(java.util.Locale.ROOT, "%.2f", v)));
		}
	}
}
