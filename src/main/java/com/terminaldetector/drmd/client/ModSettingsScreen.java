package com.terminaldetector.drmd.client;

import com.terminaldetector.drmd.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * In-game DRMD settings — opened from the creative-tab "Mod Settings" item.
 * All actions go through existing ActionPayload / client helpers (no console).
 */
public class ModSettingsScreen extends Screen {
	private String status = "Настройки DRMD 6DOF";

	public ModSettingsScreen() {
		super(Text.translatable("screen.drmd.settings"));
	}

	@Override
	protected void init() {
		clearChildren();
		int cx = width / 2;
		int y = 40;
		int w = 200;
		int h = 20;
		int gap = 24;

		addDrawableChild(ButtonWidget.builder(thrusterLabel(), b -> {
			send("toggle");
			status = "Thrusters: переключены (H тоже) — закрой и открой меню, чтобы обновить";
		}).dimensions(cx - w / 2, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(faLabel(), b -> {
			send("flightassist");
			status = "Flight Assist переключён";
		}).dimensions(cx - w / 2, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Always-Run / форсаж"), b -> {
			send("alwaysrun");
			status = "Always-Run переключён";
		}).dimensions(cx - w / 2, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(radarLabel(), b -> {
			send("radar");
			status = "Радар переключён";
		}).dimensions(cx - w / 2, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Строительство ON/OFF"), b -> {
			send("construct");
			status = "Construction Mode переключён";
		}).dimensions(cx - w / 2, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(presetLabel(), b -> {
			send("energy_cycle");
			status = "Пресет энергии сменён";
		}).dimensions(cx - w / 2, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Сброс крена / level"), b -> {
			send("reset_roll");
			status = "Крен сброшен";
		}).dimensions(cx - w / 2, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Лифт уровней мира"), b -> {
			send("level_lift");
			status = "Переход на следующий уровень";
		}).dimensions(cx - w / 2, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Запуск реактора + набор"), b -> {
			send("reactor_start");
			status = "Reactor Room активирован";
		}).dimensions(cx - w / 2, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Выдать стартовый набор"), b -> {
			send("starter_kit");
			status = "Стартовый набор выдан";
		}).dimensions(cx - w / 2, y, w, h).build());
		y += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Оружейная мастерская (M)"), b -> {
			close();
			DescentClient.openWorkshop();
		}).dimensions(cx - w / 2, y, w, h).build());
		y += gap + 8;

		addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> close())
				.dimensions(cx - 50, y, 100, h).build());
	}

	private Text thrusterLabel() {
		return Text.literal("6DoF thrusters: " + (DescentClientState.enabled ? "§aON" : "§cOFF"));
	}

	private Text faLabel() {
		return Text.literal("Flight Assist: " + (DescentClientState.flightAssist ? "§aON" : "§cOFF"));
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
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xE8F4FF);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("§7" + status), width / 2, 24, 0xA0C0D0);
		context.drawCenteredTextWithShadow(textRenderer,
				Text.literal("§8Креатив → вкладка DRMD 6DOF → «Настройки DRMD»"),
				width / 2, height - 18, 0x708090);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
