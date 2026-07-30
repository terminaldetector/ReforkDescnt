package com.terminaldetector.drmd.client;

import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Dedicated DRMD mod menu — tabs with live click triggers (no console).
 * Labels update in place; full rebuild only on tab change (never mid-click).
 */
public class ModSettingsScreen extends Screen {
	private enum Tab { FLIGHT, VIEW, SESSION }

	private Tab tab = Tab.FLIGHT;
	private Tab pendingTab;
	private String status = "Клик по кнопкам — триггеры мода";

	private ButtonWidget thrusterBtn;
	private ButtonWidget faBtn;
	private ButtonWidget arBtn;
	private ButtonWidget radarBtn;
	private ButtonWidget presetBtn;
	private ButtonWidget weaponViewBtn;
	private ButtonWidget attitudeBtn;

	public ModSettingsScreen() {
		super(Text.translatable("screen.drmd.settings"));
	}

	@Override
	protected void init() {
		clearChildren();
		int cx = width / 2;
		int tabY = 34;
		int tabW = 90;
		addDrawableChild(tabButton(Tab.FLIGHT, cx - tabW * 3 / 2 - 4, tabY, tabW));
		addDrawableChild(tabButton(Tab.VIEW, cx - tabW / 2, tabY, tabW));
		addDrawableChild(tabButton(Tab.SESSION, cx + tabW / 2 + 4, tabY, tabW));

		int top = 62;
		int w = 210;
		int h = 20;
		int gap = 22;
		int x = cx - w / 2;

		switch (tab) {
			case FLIGHT -> buildFlight(x, top, w, h, gap);
			case VIEW -> buildView(x, top, w, h, gap);
			case SESSION -> buildSession(x, top, w, h, gap);
		}

		addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> close())
				.dimensions(cx - 50, height - 26, 100, 20).build());
	}

	private ButtonWidget tabButton(Tab t, int x, int y, int w) {
		String mark = tab == t ? "§b▶ " : "§7";
		String label = switch (t) {
			case FLIGHT -> "Полёт";
			case VIEW -> "Обзор";
			case SESSION -> "Сессия";
		};
		return ButtonWidget.builder(Text.literal(mark + label), b -> {
			if (tab == t) return;
			pendingTab = t;
			status = "Вкладка: " + label;
		}).dimensions(x, y, w, 20).build();
	}

	private void buildFlight(int x, int y, int w, int h, int gap) {
		thrusterBtn = addDrawableChild(ButtonWidget.builder(thrusterLabel(), b -> {
			// Explicit enable/disable — never "toggle" (avoids desync with optimistic UI).
			boolean next = !DescentClientState.enabled;
			applyClientFlightMode(next);
			send(next ? "enable" : "disable");
			status = next ? "Режим: §aПОЛЁТ" : "Режим: §eПЕШИЙ";
			refreshLabels();
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("§aРежим ПОЛЁТ (force ON)"), b -> {
			applyClientFlightMode(true);
			send("enable");
			status = "Режим: §aПОЛЁТ";
			refreshLabels();
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("§cРежим ПЕШИЙ (полёт OFF)"), b -> {
			applyClientFlightMode(false);
			send("disable");
			status = "Режим: §eПЕШИЙ";
			refreshLabels();
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("§eПочинить полёт + обзор"), b -> {
			applyClientFlightMode(true);
			send("repair_flight");
			reprimeLook();
			status = "Repair — полёт ON, пеший/foot сброшены";
			refreshLabels();
		}).dimensions(x, y, w, h).build());
		y += gap;

		faBtn = addDrawableChild(ButtonWidget.builder(faLabel(), b -> {
			send("flightassist");
			DescentClientState.flightAssist = !DescentClientState.flightAssist;
			status = "Flight Assist " + (DescentClientState.flightAssist ? "ON" : "OFF");
			refreshLabels();
		}).dimensions(x, y, w, h).build());
		y += gap;

		arBtn = addDrawableChild(ButtonWidget.builder(arLabel(), b -> {
			send("alwaysrun");
			DescentClientState.alwaysRun = !DescentClientState.alwaysRun;
			status = "Always-Run " + (DescentClientState.alwaysRun ? "ON" : "OFF");
			refreshLabels();
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Рывок (Dash)"), b -> {
			send("dash");
			status = "Dash";
		}).dimensions(x, y, w, h).build());
	}

	private void buildView(int x, int y, int w, int h, int gap) {
		weaponViewBtn = addDrawableChild(ButtonWidget.builder(weaponViewLabel(), b -> {
			DescentClientState.weaponViewMode = DescentClientState.weaponViewMode.next();
			status = "Вид оружия: " + DescentClientState.weaponViewMode.label();
			refreshLabels();
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Сброс крена / level"), b -> {
			if (client != null && client.player != null && DescentClientState.enabled) {
				ShipAttitudeClient.level();
			}
			send("reset_roll");
			status = "Крен сброшен";
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("§eПерезапуск attitude (360°)"), b -> {
			reprimeLook();
			status = DescentClientState.attitudeValid
					? "Attitude OK — мышь + Q/E"
					: "Сначала включи полёт";
			refreshLabels();
		}).dimensions(x, y, w, h).build());
		y += gap;

		radarBtn = addDrawableChild(ButtonWidget.builder(radarLabel(), b -> {
			send("radar");
			DescentClientState.radar = !DescentClientState.radar;
			status = "Радар " + (DescentClientState.radar ? "ON" : "OFF");
			refreshLabels();
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Оружейная мастерская (M)"), b -> {
			close();
			DescentClient.openWorkshop();
		}).dimensions(x, y, w, h).build());
		y += gap;

		attitudeBtn = addDrawableChild(ButtonWidget.builder(attitudeStatusLabel(), b -> {
			reprimeLook();
			status = "Статус обновлён";
			refreshLabels();
		}).dimensions(x, y, w, h).build());
	}

	private void buildSession(int x, int y, int w, int h, int gap) {
		presetBtn = addDrawableChild(ButtonWidget.builder(presetLabel(), b -> {
			send("energy_cycle");
			status = "Пресет энергии — ждём sync";
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Строительство ON/OFF"), b -> {
			send("construct");
			DescentClientState.constructionMode = !DescentClientState.constructionMode;
			status = "Construction " + (DescentClientState.constructionMode ? "ON" : "OFF");
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Лифт высоты (в колонке мира)"), b -> {
			send("level_lift");
			status = "Смена высоты внутри −64…320";
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Запуск реактора + набор"), b -> {
			send("reactor_start");
			DescentClientState.enabled = true;
			status = "Reactor Room активирован";
			refreshLabels();
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Выдать стартовый набор"), b -> {
			send("starter_kit");
			DescentClientState.enabled = true;
			status = "Стартовый набор выдан";
			refreshLabels();
		}).dimensions(x, y, w, h).build());
	}

	private void refreshLabels() {
		if (thrusterBtn != null) thrusterBtn.setMessage(thrusterLabel());
		if (faBtn != null) faBtn.setMessage(faLabel());
		if (arBtn != null) arBtn.setMessage(arLabel());
		if (radarBtn != null) radarBtn.setMessage(radarLabel());
		if (presetBtn != null) presetBtn.setMessage(presetLabel());
		if (weaponViewBtn != null) weaponViewBtn.setMessage(weaponViewLabel());
		if (attitudeBtn != null) attitudeBtn.setMessage(attitudeStatusLabel());
	}

	private void reprimeLook() {
		if (client == null || client.player == null) return;
		if (!DescentClientState.enabled) {
			status = "Включи полёт — потом attitude";
			return;
		}
		ShipAttitudeClient.resetFromPlayer(client.player);
		DescentClientState.attitudeValid = true;
	}

	/** Keep client DescentPlayerData + camera flags in the same mode the buttons claim. */
	private void applyClientFlightMode(boolean flight) {
		DescentClientState.enabled = flight;
		DescentClientState.footGravity = false;
		if (client == null || client.player == null) return;
		var data = com.terminaldetector.drmd.DescentPlayerData.get(client.player);
		data.setEnabled(flight);
		if (flight) {
			com.terminaldetector.drmd.world.gravity.FootGravitySystem.clear(client.player);
			com.terminaldetector.drmd.world.LocalOrientation.setUp(client.player, new net.minecraft.util.math.Vec3d(0, 1, 0));
			client.player.setNoGravity(true);
			ShipAttitudeClient.resetFromPlayer(client.player);
			DescentClientState.attitudeValid = true;
			com.terminaldetector.drmd.client.gravity.FootGravityCamera.reset();
		} else {
			data.setFlightVelocity(net.minecraft.util.math.Vec3d.ZERO);
			client.player.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
			client.player.setNoGravity(false);
			DescentClientState.attitudeValid = false;
			ShipAttitudeClient.clear();
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (pendingTab != null) {
			tab = pendingTab;
			pendingTab = null;
			clearAndInit();
			return;
		}
		// Soft label sync from network — never tear down widgets mid-click.
		refreshLabels();
	}

	private Text thrusterLabel() {
		return Text.literal("6DoF thrusters: " + (DescentClientState.enabled ? "§aON" : "§cOFF") + " §7(toggle)");
	}

	private Text faLabel() {
		return Text.literal("Flight Assist: " + (DescentClientState.flightAssist ? "§aON" : "§cOFF"));
	}

	private Text arLabel() {
		return Text.literal("Always-Run: " + (DescentClientState.alwaysRun ? "§aON" : "§cOFF"));
	}

	private Text radarLabel() {
		return Text.literal("Радар: " + (DescentClientState.radar ? "§aON" : "§cOFF"));
	}

	private Text presetLabel() {
		String p = DescentClientState.preset == null ? "balanced" : DescentClientState.preset;
		return Text.literal("Энергия: §e" + p + " §7(цикл)");
	}

	private Text weaponViewLabel() {
		return Text.literal("Вид оружия: §e" + DescentClientState.weaponViewMode.label() + " §7(V)");
	}

	private Text attitudeStatusLabel() {
		String att = DescentClientState.attitudeValid ? "§aVALID" : "§cINVALID";
		String primed = ShipAttitudeClient.isPrimed() ? "§aprimed" : "§cnot primed";
		return Text.literal("Статус: " + att + " §7/ " + primed);
	}

	private static void send(String action) {
		ClientPlayNetworking.send(new ModNetworking.ActionPayload(action));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xE8F4FF);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("§7" + status), width / 2, 20, 0xA0C0D0);

		String live = "§8полёт " + (DescentClientState.enabled ? "§aON" : "§cOFF")
				+ " §8· attitude " + (DescentClientState.attitudeValid ? "§aOK" : "§c—")
				+ " §8· FA " + (DescentClientState.flightAssist ? "§aON" : "§7off")
				+ " §8· E §f" + (int) DescentClientState.energy;
		context.drawCenteredTextWithShadow(textRenderer, Text.literal(live), width / 2, height - 40, 0x8090A0);

		context.drawCenteredTextWithShadow(textRenderer,
				Text.literal("§8P / креатив → «Настройки DRMD»"),
				width / 2, height - 12, 0x607080);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
