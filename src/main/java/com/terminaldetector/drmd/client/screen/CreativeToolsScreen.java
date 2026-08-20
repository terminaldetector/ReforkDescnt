package com.terminaldetector.drmd.client.screen;

import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * Developer-facing controls, split out of ShipCustomizeScreen so survival ship customization
 * (slots, propulsion, presets) stays free of them: raw accel/drag/maxSpeed physics tuning, and
 * the Workshop weapon-builder entry point.
 *
 * <p>The Workshop button opens unconditionally for anyone, matching the existing {@code M}
 * keybind — only the write-back ({@code ConstructionPayload}) is server-gated to creative. The
 * physics tuners are creative-only both here (declutter) and, as before, on the server (the
 * sender decides nothing, the server does).
 */
public class CreativeToolsScreen extends Screen {
	private static final int ROW = 22;
	private final Screen parent;
	private final boolean creative;

	public CreativeToolsScreen(Screen parent) {
		super(Text.translatable("screen.drmd.creative_tools"));
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
		int y = Math.max(36, this.height / 2 - (creative ? 65 : 40));

		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.drmd.creative_tools.workshop"),
						b -> DescentClient.openWorkshop())
				.dimensions(cx - 100, y, 200, 20).build());
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
			addDrawableChild(ButtonWidget.builder(Text.translatable("options.drmd.creative_only"), b -> {})
					.dimensions(cx - 140, y, 280, 18).build()).active = false;
			y += ROW + 8;
		}

		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
				.dimensions(cx - 100, Math.min(this.height - 28, y), 200, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0x5FE08A);
	}

	@Override
	public void close() {
		if (this.client != null) this.client.setScreen(parent);
	}

	/** Creative physics tuner — sends {@code accel:N} / {@code drag:N} / {@code maxspeed:N}.
	 *  Moved verbatim from ShipCustomizeScreen (Phase 5: creative tools split out of survival
	 *  ship customize). */
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
