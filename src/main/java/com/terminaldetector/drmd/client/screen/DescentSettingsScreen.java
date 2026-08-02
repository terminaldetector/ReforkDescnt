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
 * DRMD options, opened from the pause menu.
 *
 * <p>Two halves: rendering choices that are purely local, and a creative row that asks the server to
 * do something. The creative actions go through the ordinary action payload, so the server decides
 * whether the sender is allowed — a client-side gamemode check here would be decoration.
 */
public class DescentSettingsScreen extends Screen {
	private static final int ROW = 24;
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
		int y = Math.max(40, this.height / 2 - 100);

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
		addDrawableChild(toggle(right, y, "options.drmd.camera_shake", DescentConfig.cameraShake,
				v -> DescentConfig.cameraShake = v));
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
							b.setMessage(Text.translatable("options.drmd.toggle_6dof",
									Text.translatable(DescentClientState.enabled ? "options.off" : "options.on")));
						})
				.dimensions(right, y, 150, 20).build());
		y += ROW + 8;

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
		y += ROW + 8;

		// Controls get their own row rather than a scattering of hints. Thirteen of the mod's
		// actions are bound keys — roll, dash, assist, hook, workshop — and none of them is
		// discoverable from this screen otherwise.
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
		y += ROW + 8;

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
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0x5FE08A);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("options.drmd.keys_hint"), this.width / 2, 30, 0x7A8A80);
	}

	@Override
	public void close() {
		DescentConfig.save();
		if (this.client != null) this.client.setScreen(parent);
	}

	/** Vanilla SliderWidget wants 0..1 internally; this keeps the real range at the edges. */
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
