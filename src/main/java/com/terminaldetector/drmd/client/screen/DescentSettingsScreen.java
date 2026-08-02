package com.terminaldetector.drmd.client.screen;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * DRMD options — pause menu button, {@code ,} keybind, or Controls category.
 *
 * <p>Local render/feel toggles save to {@code drmd.properties}. Creative give-actions
 * go through the server action payload.
 */
public class DescentSettingsScreen extends Screen {
	private static final int ROW = 22;
	private final Screen parent;

	public DescentSettingsScreen(Screen parent) {
		super(Text.translatable("screen.drmd.settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		DescentConfig.load();
		int cx = this.width / 2;
		int left = cx - 155;
		int right = cx + 5;
		int y = Math.max(36, this.height / 2 - 130);

		addDrawableChild(toggle(left, y, "options.drmd.cockpit", DescentConfig.cockpit,
				v -> DescentConfig.cockpit = v));
		addDrawableChild(toggle(right, y, "options.drmd.instruments", DescentConfig.cockpitInstruments,
				v -> DescentConfig.cockpitInstruments = v));
		y += ROW;
		addDrawableChild(toggle(left, y, "options.drmd.hud", DescentConfig.hud,
				v -> DescentConfig.hud = v));
		addDrawableChild(toggle(right, y, "options.drmd.weapon_view", DescentConfig.weaponView,
				v -> DescentConfig.weaponView = v));
		y += ROW;
		addDrawableChild(toggle(left, y, "options.drmd.level_sky", DescentConfig.levelSky,
				v -> DescentConfig.levelSky = v));
		addDrawableChild(toggle(right, y, "options.drmd.orbital_belt_sky", DescentConfig.orbitalBeltSky,
				v -> DescentConfig.orbitalBeltSky = v));
		y += ROW;
		addDrawableChild(toggle(left, y, "options.drmd.hybrid_horizon", DescentConfig.hybridHorizon,
				v -> DescentConfig.hybridHorizon = v));
		addDrawableChild(toggle(right, y, "options.drmd.planet_floor", DescentConfig.planetFloor,
				v -> DescentConfig.planetFloor = v));
		y += ROW;
		addDrawableChild(toggle(left, y, "options.drmd.camera_shake", DescentConfig.cameraShake,
				v -> DescentConfig.cameraShake = v));
		addDrawableChild(toggle(right, y, "options.drmd.fall_aftermath", DescentConfig.fallAftermath,
				v -> DescentConfig.fallAftermath = v));
		y += ROW;

		addDrawableChild(new Slider(left, y, "options.drmd.cockpit_opacity",
				DescentConfig.cockpitOpacity, 0.2f, 1.0f, "%.0f%%", 100f,
				v -> DescentConfig.cockpitOpacity = v));
		addDrawableChild(new Slider(right, y, "options.drmd.look_gain",
				DescentConfig.lookGain, 0.25f, 3.0f, "%.2fx", 1f,
				v -> DescentConfig.lookGain = v));
		y += ROW;
		addDrawableChild(new Slider(left, y, "options.drmd.roll_rate",
				DescentConfig.rollRate, 40f, 400f, "%.0f°/s", 1f,
				v -> DescentConfig.rollRate = v));
		addDrawableChild(ButtonWidget.builder(
						Text.translatable("options.drmd.toggle_6dof",
								Text.translatable(DescentClientState.enabled ? "options.on" : "options.off")),
						b -> {
							ClientPlayNetworking.send(new ModNetworking.ActionPayload("toggle"));
							// Optimistic flip — server sync confirms on next payload.
							boolean next = !DescentClientState.enabled;
							b.setMessage(Text.translatable("options.drmd.toggle_6dof",
									Text.translatable(next ? "options.on" : "options.off")));
						})
				.dimensions(right, y, 150, 20).build());
		y += ROW + 6;

		addDrawableChild(ButtonWidget.builder(Text.translatable("options.drmd.give_ship"),
						b -> ClientPlayNetworking.send(new ModNetworking.ActionPayload("creative_ship")))
				.tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
						Text.translatable("options.drmd.creative_only")))
				.dimensions(left, y, 150, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("options.drmd.give_kit"),
						b -> ClientPlayNetworking.send(new ModNetworking.ActionPayload("creative_kit")))
				.tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
						Text.translatable("options.drmd.creative_only")))
				.dimensions(right, y, 150, 20).build());
		y += ROW + 6;

		addDrawableChild(ButtonWidget.builder(Text.translatable("options.drmd.ship_customize"),
						b -> {
							if (this.client != null) {
								this.client.setScreen(new ShipCustomizeScreen(this));
							}
						})
				.tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
						Text.translatable("options.drmd.ship_customize.tip")))
				.dimensions(cx - 100, y, 200, 20).build());
		y += ROW;
		addDrawableChild(ButtonWidget.builder(Text.translatable("options.drmd.controls"),
						b -> {
							if (this.client != null) {
								this.client.setScreen(new net.minecraft.client.gui.screen.option.KeybindsScreen(
										this, this.client.options));
							}
						})
				.tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
						Text.translatable("options.drmd.controls.tip")))
				.dimensions(cx - 100, y, 200, 20).build());
		y += ROW + 6;

		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
				.dimensions(cx - 100, Math.min(this.height - 28, y), 200, 20).build());
	}

	private ButtonWidget toggle(int x, int y, String key, boolean initial,
								java.util.function.Consumer<Boolean> setter) {
		boolean[] state = {initial};
		return ButtonWidget.builder(label(key, state[0]), b -> {
					state[0] = !state[0];
					setter.accept(state[0]);
					b.setMessage(label(key, state[0]));
				})
				.dimensions(x, y, 150, 20).build();
	}

	private static Text label(String key, boolean on) {
		return Text.translatable(key).append(": ")
				.append(Text.translatable(on ? "options.on" : "options.off"));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0x5FE08A);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("options.drmd.keys_hint"), this.width / 2, 24, 0x7A8A80);
	}

	@Override
	public void close() {
		DescentConfig.save();
		if (this.client != null) this.client.setScreen(parent);
	}

	private static final class Slider extends SliderWidget {
		private final String key;
		private final float min;
		private final float max;
		private final String format;
		private final float displayScale;
		private final java.util.function.Consumer<Float> setter;

		Slider(int x, int y, String key, float value, float min, float max,
			   String format, float displayScale, java.util.function.Consumer<Float> setter) {
			super(x, y, 150, 20, Text.empty(), (value - min) / (max - min));
			this.key = key;
			this.min = min;
			this.max = max;
			this.format = format;
			this.displayScale = displayScale;
			this.setter = setter;
			updateMessage();
		}

		private float value() {
			return min + (float) this.value * (max - min);
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.translatable(key).append(": ")
					.append(String.format(format, value() * displayScale)));
		}

		@Override
		protected void applyValue() {
			setter.accept(value());
		}
	}
}
