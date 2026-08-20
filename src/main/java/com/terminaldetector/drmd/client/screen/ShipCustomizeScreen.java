package com.terminaldetector.drmd.client.screen;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.flight.AfterburnerTiers;
import com.terminaldetector.drmd.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Ship customize — propulsion module socket, weapon hardpoints, energy presets. Same content for
 * every player; raw accel/drag/maxSpeed physics tuning lives in CreativeToolsScreen instead
 * (Phase 5: creative tools split out of survival ship customization).
 */
public class ShipCustomizeScreen extends Screen {
	private static final int ROW = 22;
	private final Screen parent;

	public ShipCustomizeScreen(Screen parent) {
		super(Text.translatable("screen.drmd.ship_customize"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = Math.max(36, this.height / 2 - 125);

		// --- Propulsion module (both modes) — hold a crafted module, click Equip; Unequip returns
		// it. An empty slot means no afterburner at all, not just a weak one (see
		// PyroShipEntity.getAfterburnerTier). Replaces the old free tier-picker: a tier now has to
		// be crafted and socketed like a weapon, not clicked. ---
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.drmd.propulsion.header"), b -> {})
				.dimensions(cx - 100, y, 200, 18).build()).active = false;
		y += ROW;
		addDrawableChild(ButtonWidget.builder(
						Text.translatable("screen.drmd.propulsion.equip"),
						b -> ClientPlayNetworking.send(new ModNetworking.ActionPayload("equip_propulsion")))
				.dimensions(cx - 155, y, 150, 20).build());
		addDrawableChild(ButtonWidget.builder(
						Text.translatable("screen.drmd.propulsion.unequip"),
						b -> ClientPlayNetworking.send(new ModNetworking.ActionPayload("unequip_propulsion")))
				.dimensions(cx + 5, y, 150, 20).build());
		y += ROW + 4;

		boolean hasModule = DescentClientState.afterburnerTier > 0;
		int tier = AfterburnerTiers.clamp(DescentClientState.afterburnerTier);
		addDrawableChild(ButtonWidget.builder(
						hasModule
								? Text.translatable("screen.drmd.afterburner.stats", tier,
										String.format("%.0f", AfterburnerTiers.costPerSec(tier)))
								: Text.translatable("screen.drmd.propulsion.empty"),
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

		// --- Weapon hardpoints (both modes) — hold a weapon, click Equip; Unequip returns it. ---
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.drmd.weapons.header"), b -> {})
				.dimensions(cx - 100, y, 200, 18).build()).active = false;
		y += ROW;
		String[] slotKeys = {"upper", "lower", "side_left", "side_right"};
		com.terminaldetector.drmd.entity.ShipWeaponSlot[] slots = com.terminaldetector.drmd.entity.ShipWeaponSlot.values();
		for (int i = 0; i < slots.length; i++) {
			String key = slotKeys[i];
			com.terminaldetector.drmd.entity.ShipWeaponSlot slot = slots[i];
			addDrawableChild(ButtonWidget.builder(
							Text.translatable("screen.drmd.weapons.equip." + key),
							b -> ClientPlayNetworking.send(new ModNetworking.ActionPayload("equip_slot:" + slot.name())))
					.dimensions(cx - 155, y, 150, 20).build());
			addDrawableChild(ButtonWidget.builder(
							Text.translatable("screen.drmd.weapons.unequip." + key),
							b -> ClientPlayNetworking.send(new ModNetworking.ActionPayload("unequip_slot:" + slot.name())))
					.dimensions(cx + 5, y, 150, 20).build());
			y += ROW;
		}
		y += 6;

		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.drmd.ship.survival_hint"), b -> {})
				.dimensions(cx - 140, y, 280, 18).build()).active = false;
		y += ROW + 8;

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
		boolean hasModule = DescentClientState.afterburnerTier > 0;
		int tier = AfterburnerTiers.clamp(DescentClientState.afterburnerTier);
		int color = hasModule ? AfterburnerTiers.colorArgb(tier) : 0xFF888888;
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, color);

		// Propulsion status strip under title — every bar dim when the slot is empty, since there's
		// no active tier to highlight (0 isn't a real tier, just "nothing socketed").
		int cx = this.width / 2;
		int sx = cx - 40;
		for (int t = 1; t <= 4; t++) {
			int c = AfterburnerTiers.colorArgb(t);
			boolean on = hasModule && t == tier;
			context.fill(sx + (t - 1) * 22, 28, sx + (t - 1) * 22 + 18, 34, on ? c : (c & 0x55FFFFFF));
		}
	}

	@Override
	public void close() {
		if (this.client != null) this.client.setScreen(parent);
	}
}
