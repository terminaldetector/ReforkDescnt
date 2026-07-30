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
 * Opened from creative-tab item or keybind.
 */
public class ModSettingsScreen extends Screen {
	private enum Tab { FLIGHT, VIEW, SESSION }

	private Tab tab = Tab.FLIGHT;
	private String status = "Клик по кнопкам — триггеры мода";
	private int refreshTicker;
	/** Cached labels so we can rebuild when sync flips ON/OFF. */
	private boolean lastEnabled;
	private boolean lastFa;
	private boolean lastRadar;
	private boolean lastAr;
	private String lastPreset = "";

	public ModSettingsScreen() {
		super(Text.translatable("screen.drmd.settings"));
	}

	@Override
	protected void init() {
		clearChildren();
		int cx = width / 2;
		int tabY = 36;
		int tabW = 88;
		addDrawableChild(tabButton(Tab.FLIGHT, cx - tabW * 3 / 2 - 4, tabY, tabW));
		addDrawableChild(tabButton(Tab.VIEW, cx - tabW / 2, tabY, tabW));
		addDrawableChild(tabButton(Tab.SESSION, cx + tabW / 2 + 4, tabY, tabW));

		int y = 68;
		int w = 220;
		int h = 20;
		int gap = 24;
		int x = cx - w / 2;

		switch (tab) {
			case FLIGHT -> buildFlight(x, y, w, h, gap);
			case VIEW -> buildView(x, y, w, h, gap);
			case SESSION -> buildSession(x, y, w, h, gap);
		}

		addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> close())
				.dimensions(cx - 50, height - 28, 100, 20).build());

		lastEnabled = DescentClientState.enabled;
		lastFa = DescentClientState.flightAssist;
		lastRadar = DescentClientState.radar;
		lastAr = DescentClientState.alwaysRun;
		lastPreset = DescentClientState.preset == null ? "" : DescentClientState.preset;
	}

	private ButtonWidget tabButton(Tab t, int x, int y, int w) {
		String mark = tab == t ? "§b▶ " : "§7";
		String label = switch (t) {
			case FLIGHT -> "Полёт";
			case VIEW -> "Обзор";
			case SESSION -> "Сессия";
		};
		return ButtonWidget.builder(Text.literal(mark + label), b -> {
			tab = t;
			status = "Вкладка: " + label;
			init();
		}).dimensions(x, y, w, 20).build();
	}

	private void buildFlight(int x, int y, int w, int h, int gap) {
		addDrawableChild(ButtonWidget.builder(thrusterLabel(), b -> {
			send("toggle");
			status = "Thrusters переключены (как H)";
			scheduleRefresh();
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("§aВключить полёт (force ON)"), b -> {
			send("enable");
			status = "Полёт принудительно ON";
			scheduleRefresh();
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("§cВыключить полёт"), b -> {
			send("disable");
			status = "Полёт OFF — пешая гравитация";
			scheduleRefresh();
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("§eПочинить полёт + обзор"), b -> {
			send("repair_flight");
			reprimeLook();
			status = "Repair: thrusters + attitude + no foot gravity";
			scheduleRefresh();
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(faLabel(), b -> {
			send("flightassist");
			status = "Flight Assist переключён";
			scheduleRefresh();
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(arLabel(), b -> {
			send("alwaysrun");
			status = "Always-Run / форсаж переключён";
			scheduleRefresh();
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Рывок (Dash)"), b -> {
			send("dash");
			status = "Dash";
		}).dimensions(x, y, w, h).build());
	}

	private void buildView(int x, int y, int w, int h, int gap) {
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
					? "Attitude OK — крути мышью, Q/E крен"
					: "Сначала включи полёт";
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(radarLabel(), b -> {
			send("radar");
			status = "Радар переключён";
			scheduleRefresh();
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Оружейная мастерская (M)"), b -> {
			close();
			DescentClient.openWorkshop();
		}).dimensions(x, y, w, h).build());
		y += gap;

		String att = DescentClientState.attitudeValid ? "§aVALID" : "§cINVALID";
		String primed = ShipAttitudeClient.isPrimed() ? "§aprimed" : "§cnot primed";
		addDrawableChild(ButtonWidget.builder(
				Text.literal("Статус: " + att + " §7/ " + primed),
				b -> {
					reprimeLook();
					status = "Статус обновлён";
					scheduleRefresh();
				}).dimensions(x, y, w, h).build());
	}

	private void buildSession(int x, int y, int w, int h, int gap) {
		addDrawableChild(ButtonWidget.builder(presetLabel(), b -> {
			send("energy_cycle");
			status = "Пресет энергии сменён";
			scheduleRefresh();
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Строительство ON/OFF"), b -> {
			send("construct");
			status = "Construction Mode переключён";
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Лифт уровней мира"), b -> {
			send("level_lift");
			status = "Переход на следующий уровень";
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Запуск реактора + набор"), b -> {
			send("reactor_start");
			status = "Reactor Room активирован";
			scheduleRefresh();
		}).dimensions(x, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Выдать стартовый набор"), b -> {
			send("starter_kit");
			status = "Стартовый набор выдан";
			scheduleRefresh();
		}).dimensions(x, y, w, h).build());
	}

	private void scheduleRefresh() {
		refreshTicker = 8; // rebuild labels after sync arrives
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

	@Override
	public void tick() {
		super.tick();
		if (refreshTicker > 0) {
			refreshTicker--;
			if (refreshTicker == 0) init();
		}
		boolean dirty = lastEnabled != DescentClientState.enabled
				|| lastFa != DescentClientState.flightAssist
				|| lastRadar != DescentClientState.radar
				|| lastAr != DescentClientState.alwaysRun
				|| !lastPreset.equals(DescentClientState.preset == null ? "" : DescentClientState.preset);
		if (dirty && refreshTicker <= 0) {
			init();
		}
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

	private static void send(String action) {
		ClientPlayNetworking.send(new ModNetworking.ActionPayload(action));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xE8F4FF);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("§7" + status), width / 2, 22, 0xA0C0D0);

		String live = "§8полёт " + (DescentClientState.enabled ? "§aON" : "§cOFF")
				+ " §8· attitude " + (DescentClientState.attitudeValid ? "§aOK" : "§c—")
				+ " §8· FA " + (DescentClientState.flightAssist ? "§aON" : "§7off")
				+ " §8· E §f" + (int) DescentClientState.energy;
		context.drawCenteredTextWithShadow(textRenderer, Text.literal(live), width / 2, height - 42, 0x8090A0);

		context.drawCenteredTextWithShadow(textRenderer,
				Text.literal("§8Креатив → DRMD 6DOF → «Настройки» · клавиша меню"),
				width / 2, height - 14, 0x607080);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
