package com.terminaldetector.drmd.workshop;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Weapon Workshop UI — 4 tabs (Stats / Projectile / Flak / Guidance) + templates + code export.
 * Port of SCK menu/workshop.lua schema-driven editors.
 */
public class WorkshopScreen extends Screen {
	private enum Tab { STATS, PROJECTILE, FLAK, GUIDANCE }

	private Tab tab = Tab.STATS;
	private WeaponConfig config = new WeaponConfig();
	private final List<TextFieldWidget> fields = new ArrayList<>();
	private String status = "Ready";

	public WorkshopScreen() {
		super(Text.literal("DRMD Weapon Workshop"));
	}

	@Override
	protected void init() {
		clearChildren();
		fields.clear();
		int bx = 10;
		addDrawableChild(ButtonWidget.builder(Text.literal("Stats"), b -> switchTab(Tab.STATS))
				.dimensions(bx, 8, 60, 18).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Projectile"), b -> switchTab(Tab.PROJECTILE))
				.dimensions(bx + 64, 8, 70, 18).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Flak"), b -> switchTab(Tab.FLAK))
				.dimensions(bx + 138, 8, 50, 18).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Guidance"), b -> switchTab(Tab.GUIDANCE))
				.dimensions(bx + 192, 8, 70, 18).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Cannon"), b -> loadTemplate("cannon"))
				.dimensions(width - 210, 8, 60, 18).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Missile"), b -> loadTemplate("missile"))
				.dimensions(width - 146, 8, 60, 18).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Flak T"), b -> loadTemplate("flak"))
				.dimensions(width - 82, 8, 40, 18).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Seeker"), b -> loadTemplate("seeker"))
				.dimensions(width - 38, 8, 30, 18).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Copy Java"), b -> {
			String code = CodeGenerator.generateJava(config);
			if (client != null) client.keyboard.setClipboard(code);
			status = "Copied generated Java to clipboard";
		}).dimensions(10, height - 24, 90, 18).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
				.dimensions(width - 70, height - 24, 60, 18).build());

		rebuildFields();
	}

	private void switchTab(Tab t) {
		flushFields();
		tab = t;
		init();
	}

	private void loadTemplate(String name) {
		config = WeaponConfig.template(name);
		status = "Loaded template: " + name;
		init();
	}

	private Map<String, Object> currentMap() {
		return switch (tab) {
			case STATS -> config.weapon;
			case PROJECTILE -> config.projectile;
			case FLAK -> config.flak;
			case GUIDANCE -> config.guidance;
		};
	}

	private List<WeaponSchema.Field> currentSchema() {
		return switch (tab) {
			case STATS -> WeaponSchema.WEAPON;
			case PROJECTILE -> WeaponSchema.PROJECTILE;
			case FLAK -> WeaponSchema.FLAK;
			case GUIDANCE -> WeaponSchema.GUIDANCE;
		};
	}

	private void rebuildFields() {
		Map<String, Object> data = currentMap();
		List<WeaponSchema.Field> schema = currentSchema();
		int y = 36;
		String lastSection = "";
		for (WeaponSchema.Field f : schema) {
			if (!f.section().equals(lastSection)) {
				lastSection = f.section();
				y += 6;
			}
			TextFieldWidget tf = new TextFieldWidget(textRenderer, 120, y, 160, 16, Text.literal(f.label()));
			tf.setText(String.valueOf(data.getOrDefault(f.key(), f.defaultValue())));
			tf.setChangedListener(s -> data.put(f.key(), parse(f, s)));
			fields.add(tf);
			addSelectableChild(tf);
			y += 20;
			if (y > height - 40) break;
		}
	}

	private Object parse(WeaponSchema.Field f, String s) {
		try {
			return switch (f.type()) {
				case INT -> Integer.parseInt(s.trim());
				case FLOAT -> Float.parseFloat(s.trim());
				case BOOL -> Boolean.parseBoolean(s.trim());
				case ENUM, STRING -> s;
			};
		} catch (Exception e) {
			return f.defaultValue();
		}
	}

	private void flushFields() {
		List<WeaponSchema.Field> schema = currentSchema();
		Map<String, Object> data = currentMap();
		for (int i = 0; i < fields.size() && i < schema.size(); i++) {
			data.put(schema.get(i).key(), parse(schema.get(i), fields.get(i).getText()));
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
		List<WeaponSchema.Field> schema = currentSchema();
		int y = 36;
		String lastSection = "";
		int i = 0;
		for (WeaponSchema.Field f : schema) {
			if (i >= fields.size()) break;
			if (!f.section().equals(lastSection)) {
				lastSection = f.section();
				context.drawTextWithShadow(textRenderer, Text.literal("§e" + lastSection), 10, y, 0xFFFFAA);
				y += 6;
			}
			context.drawTextWithShadow(textRenderer, Text.literal(f.label()), 10, y + 4, 0xCCDDEE);
			fields.get(i).setY(y);
			fields.get(i).render(context, mouseX, mouseY, delta);
			y += 20;
			i++;
		}
		context.drawTextWithShadow(textRenderer, Text.literal(status), 110, height - 20, 0x88FF88);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
