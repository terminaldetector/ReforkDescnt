package com.terminaldetector.drmd.client.screen;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * DRMD options — pause menu button, {@code ,} keybind, or Controls category.
 *
 * <p>Local render/feel toggles save to {@code drmd.properties} on every change (and on close).
 * Content scrolls so GUI-scale / short windows still reach every row.
 */
public class DescentSettingsScreen extends Screen {
	private static final int ROW = 22;
	private static final int TOP = 36;
	/** Rows from first toggle through Controls (including spacers). */
	private static final int CONTENT_ROWS = 12;
	private final Screen parent;
	private int scroll;

	public DescentSettingsScreen(Screen parent) {
		super(Text.translatable("screen.drmd.settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		DescentConfig.reload();
		this.clearChildren();

		int cx = this.width / 2;
		int left = cx - 155;
		int right = cx + 5;
		int viewBottom = this.height - 32;
		int contentH = CONTENT_ROWS * ROW + 12;
		int viewH = Math.max(ROW, viewBottom - TOP);
		int minScroll = Math.min(0, viewH - contentH);
		scroll = MathHelper.clamp(scroll, minScroll, 0);

		int y = TOP + scroll;
		y = addRow(y, viewBottom,
				toggle(left, y, "options.drmd.cockpit", DescentConfig.cockpit, v -> {
					DescentConfig.cockpit = v;
					DescentConfig.save();
				}),
				toggle(right, y, "options.drmd.instruments", DescentConfig.cockpitInstruments, v -> {
					DescentConfig.cockpitInstruments = v;
					DescentConfig.save();
				}));
		y = addRow(y, viewBottom,
				toggle(left, y, "options.drmd.hud", DescentConfig.hud, v -> {
					DescentConfig.hud = v;
					DescentConfig.save();
				}),
				toggle(right, y, "options.drmd.weapon_view", DescentConfig.weaponView, v -> {
					DescentConfig.weaponView = v;
					DescentConfig.save();
				}));
		y = addRow(y, viewBottom,
				toggle(left, y, "options.drmd.level_sky", DescentConfig.levelSky, v -> {
					DescentConfig.levelSky = v;
					DescentConfig.save();
				}),
				toggle(right, y, "options.drmd.orbital_belt_sky", DescentConfig.orbitalBeltSky, v -> {
					DescentConfig.orbitalBeltSky = v;
					DescentConfig.save();
				}));
		y = addRow(y, viewBottom,
				toggle(left, y, "options.drmd.hybrid_horizon", DescentConfig.hybridHorizon, v -> {
					DescentConfig.hybridHorizon = v;
					DescentConfig.save();
				}),
				toggle(right, y, "options.drmd.planet_floor", DescentConfig.planetFloor, v -> {
					DescentConfig.planetFloor = v;
					DescentConfig.save();
				}));
		y = addRow(y, viewBottom,
				toggle(left, y, "options.drmd.camera_shake", DescentConfig.cameraShake, v -> {
					DescentConfig.cameraShake = v;
					DescentConfig.save();
				}),
				toggle(right, y, "options.drmd.fall_aftermath", DescentConfig.fallAftermath, v -> {
					DescentConfig.fallAftermath = v;
					DescentConfig.save();
				}));
		y = addRow(y, viewBottom,
				new Slider(left, y, "options.drmd.cockpit_opacity",
						DescentConfig.cockpitOpacity, 0.2f, 1.0f, "%.0f%%", 100f, v -> {
					DescentConfig.cockpitOpacity = v;
					DescentConfig.save();
				}),
				new Slider(right, y, "options.drmd.look_gain",
						DescentConfig.lookGain, 0.25f, 3.0f, "%.2fx", 1f, v -> {
					DescentConfig.lookGain = v;
					DescentConfig.save();
				}));
		y = addRow(y, viewBottom,
				new Slider(left, y, "options.drmd.roll_rate",
						DescentConfig.rollRate, 40f, 400f, "%.0f°/s", 1f, v -> {
					DescentConfig.rollRate = v;
					DescentConfig.save();
				}),
				ButtonWidget.builder(
								Text.translatable("options.drmd.toggle_6dof",
										Text.translatable(DescentClientState.enabled ? "options.on" : "options.off")),
								b -> {
									ClientPlayNetworking.send(new ModNetworking.ActionPayload("toggle"));
									boolean next = !DescentClientState.enabled;
									b.setMessage(Text.translatable("options.drmd.toggle_6dof",
											Text.translatable(next ? "options.on" : "options.off")));
								})
						.dimensions(right, y, 150, 20).build());
		y += 6;
		y = addRow(y, viewBottom,
				ButtonWidget.builder(Text.translatable("options.drmd.give_ship"),
								b -> ClientPlayNetworking.send(new ModNetworking.ActionPayload("creative_ship")))
						.tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
								Text.translatable("options.drmd.creative_only")))
						.dimensions(left, y, 150, 20).build(),
				ButtonWidget.builder(Text.translatable("options.drmd.give_kit"),
								b -> ClientPlayNetworking.send(new ModNetworking.ActionPayload("creative_kit")))
						.tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
								Text.translatable("options.drmd.creative_only")))
						.dimensions(right, y, 150, 20).build());
		y += 6;
		y = addRow(y, viewBottom,
				ButtonWidget.builder(Text.translatable("options.drmd.ship_customize"),
								b -> {
									if (this.client != null) {
										this.client.setScreen(new ShipCustomizeScreen(this));
									}
								})
						.tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
								Text.translatable("options.drmd.ship_customize.tip")))
						.dimensions(cx - 100, y, 200, 20).build(),
				null);
		addRow(y, viewBottom,
				ButtonWidget.builder(Text.translatable("options.drmd.controls"),
								b -> {
									if (this.client != null) {
										this.client.setScreen(new net.minecraft.client.gui.screen.option.KeybindsScreen(
												this, this.client.options));
									}
								})
						.tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
								Text.translatable("options.drmd.controls.tip")))
						.dimensions(cx - 100, y, 200, 20).build(),
				null);

		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
				.dimensions(cx - 100, this.height - 28, 200, 20).build());
	}

	private int addRow(int y, int viewBottom, ClickableWidget a, ClickableWidget b) {
		if (y >= TOP - 2 && y + 20 <= viewBottom) {
			if (a != null) addDrawableChild(a);
			if (b != null) addDrawableChild(b);
		}
		return y + ROW;
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
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int before = scroll;
		scroll += (int) Math.round(verticalAmount * 14);
		int viewBottom = this.height - 32;
		int contentH = CONTENT_ROWS * ROW + 12;
		int viewH = Math.max(ROW, viewBottom - TOP);
		int minScroll = Math.min(0, viewH - contentH);
		scroll = MathHelper.clamp(scroll, minScroll, 0);
		if (scroll != before) {
			this.clearAndInit();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
