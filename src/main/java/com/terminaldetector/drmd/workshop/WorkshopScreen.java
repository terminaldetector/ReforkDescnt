package com.terminaldetector.drmd.workshop;

import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import com.terminaldetector.drmd.weapon.registry.WeaponRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Weapon Workshop — Stats / Projectile / Flak / Guidance / Clusters.
 * Clusters tab ports SCK menu/clusters.lua and applies to live weapons via ConstructionRegistry.
 */
public class WorkshopScreen extends Screen {
	private enum Tab { STATS, PROJECTILE, FLAK, GUIDANCE, CLUSTERS }

	private Tab tab = Tab.STATS;
	private WeaponConfig config = new WeaponConfig();
	private final List<TextFieldWidget> fields = new ArrayList<>();
	private String status = "Ready — construction adapted to DRMD weapons";
	private String selectedCluster = "Center";
	private int selectedModule = -1;
	private final List<ButtonWidget> clusterButtons = new ArrayList<>();

	public WorkshopScreen() {
		super(Text.literal("DRMD Weapon Workshop"));
	}

	@Override
	protected void init() {
		clearChildren();
		fields.clear();
		clusterButtons.clear();

		int bx = 8;
		addTab("Stats", Tab.STATS, bx, 60);
		addTab("Projectile", Tab.PROJECTILE, bx + 64, 72);
		addTab("Flak", Tab.FLAK, bx + 140, 50);
		addTab("Guidance", Tab.GUIDANCE, bx + 194, 70);
		addTab("Clusters", Tab.CLUSTERS, bx + 268, 70);

		addDrawableChild(ButtonWidget.builder(Text.literal("Cannon"), b -> loadTemplate("cannon"))
				.dimensions(width - 250, 8, 56, 18).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Missile"), b -> loadTemplate("missile"))
				.dimensions(width - 190, 8, 56, 18).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Flak T"), b -> loadTemplate("flak"))
				.dimensions(width - 130, 8, 48, 18).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Seeker"), b -> loadTemplate("seeker"))
				.dimensions(width - 78, 8, 48, 18).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Copy Java"), b -> {
			flushFields();
			String code = CodeGenerator.generateJava(config);
			if (client != null) client.keyboard.setClipboard(code);
			status = "Copied Java + clusters to clipboard";
		}).dimensions(10, height - 24, 80, 18).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Load Held"), b -> loadHeldWeapon())
				.dimensions(96, height - 24, 70, 18).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Apply Build"), b -> applyConstruction())
				.dimensions(172, height - 24, 80, 18).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Reset Layout"), b -> resetLayout())
				.dimensions(258, height - 24, 80, 18).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
				.dimensions(width - 70, height - 24, 60, 18).build());

		if (tab == Tab.CLUSTERS) rebuildClusterUi();
		else rebuildFields();
	}

	private void addTab(String label, Tab t, int x, int w) {
		addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> switchTab(t))
				.dimensions(x, 8, w, 18).build());
	}

	private void switchTab(Tab t) {
		flushFields();
		tab = t;
		init();
	}

	private void loadTemplate(String name) {
		config = WeaponConfig.template(name);
		status = "Loaded template: " + name + " (clusters from " + config.targetWeaponId + ")";
		init();
	}

	private void loadHeldWeapon() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return;
		ItemStack stack = mc.player.getMainHandStack();
		if (!(stack.getItem() instanceof DescentWeaponItem wep)) {
			status = "Hold a DRMD weapon to load its construction";
			return;
		}
		String id = wep.getDef().id;
		config = WeaponConfig.fromExistingWeapon(id);
		var def = WeaponRegistry.get(id);
		if (def != null) {
			config.weapon.put("PrintName", def.displayName);
			config.weapon.put("FireRate", def.fireRate);
			config.weapon.put("Damage", def.damage);
			config.weapon.put("EnergyCost", def.energyCost);
			config.weapon.put("SplashRadius", def.splashRadius);
			config.projectile.put("InitialVelocity", def.speed);
		}
		status = "Loaded construction for weapon_d6_" + id
				+ (ConstructionRegistry.hasOverride(id) ? " (override)" : " (default layout)");
		tab = Tab.CLUSTERS;
		init();
	}

	private void applyConstruction() {
		flushFields();
		String id = config.targetWeaponId;
		if (id == null || id.isEmpty()) {
			id = ConstructionRegistry.normalize(String.valueOf(config.weapon.get("ClassName")));
			config.targetWeaponId = id;
		}
		List<ClusterModule> mods = new ArrayList<>();
		for (ClusterModule m : config.clusters) mods.add(m.copy());
		WeaponClusters.sort(mods);

		// Persist locally + ask server
		ConstructionRegistry.setOverride(id, mods);
		ClientPlayNetworking.send(new ModNetworking.ConstructionPayload(id, ClusterModule.listToNbt(mods)));
		status = "Applied construction to weapon_d6_" + id + " (" + mods.size() + " modules, "
				+ WeaponClusters.extractMuzzles(mods).size() + " muzzles)";
	}

	private void resetLayout() {
		String id = config.targetWeaponId;
		ConstructionRegistry.clearOverride(id);
		ClientPlayNetworking.send(new ModNetworking.ConstructionPayload(id, new net.minecraft.nbt.NbtList()));
		config.loadDefaultClusters(id);
		status = "Reset " + id + " to default Doom-style layout";
		init();
	}

	private Map<String, Object> currentMap() {
		return switch (tab) {
			case STATS -> config.weapon;
			case PROJECTILE -> config.projectile;
			case FLAK -> config.flak;
			case GUIDANCE -> config.guidance;
			case CLUSTERS -> config.weapon; // unused
		};
	}

	private List<WeaponSchema.Field> currentSchema() {
		return switch (tab) {
			case STATS -> WeaponSchema.WEAPON;
			case PROJECTILE -> WeaponSchema.PROJECTILE;
			case FLAK -> WeaponSchema.FLAK;
			case GUIDANCE -> WeaponSchema.GUIDANCE;
			case CLUSTERS -> List.of();
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

	private void rebuildClusterUi() {
		int y = 32;
		// Target weapon id field
		TextFieldWidget target = new TextFieldWidget(textRenderer, 120, y, 120, 16, Text.literal("target"));
		target.setText(config.targetWeaponId);
		target.setChangedListener(s -> config.targetWeaponId = ConstructionRegistry.normalize(s));
		fields.add(target);
		addSelectableChild(target);

		addDrawableChild(ButtonWidget.builder(Text.literal("Load Defaults"), b -> {
			config.loadDefaultClusters(config.targetWeaponId);
			status = "Loaded default layout for " + config.targetWeaponId;
			init();
		}).dimensions(250, y, 90, 16).build());

		y = 54;
		for (int i = 0; i < WeaponClusters.ORDER.length; i++) {
			String cn = WeaponClusters.ORDER[i];
			int fi = i;
			ButtonWidget btn = ButtonWidget.builder(Text.literal(cn), b -> {
				selectedCluster = cn;
				selectedModule = -1;
				init();
			}).dimensions(10 + i * 70, y, 66, 18).build();
			clusterButtons.add(btn);
			addDrawableChild(btn);
		}

		y = 80;
		List<ClusterModule> inZone = WeaponClusters.inCluster(config.clusters, selectedCluster);
		addDrawableChild(ButtonWidget.builder(Text.literal("+ Module"), b -> {
			ClusterModule m = WeaponClusters.make(selectedCluster, inZone.size() + 1,
					"mod_" + (inZone.size() + 1), "barrel", 19, 0, 0, 1f, true, config.clusters.size() + 1);
			config.clusters.add(m);
			WeaponClusters.sort(config.clusters);
			selectedModule = WeaponClusters.inCluster(config.clusters, selectedCluster).size() - 1;
			status = "Added module to " + selectedCluster;
			init();
		}).dimensions(10, y, 70, 16).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("− Remove"), b -> {
			List<ClusterModule> zone = WeaponClusters.inCluster(config.clusters, selectedCluster);
			if (selectedModule >= 0 && selectedModule < zone.size()) {
				config.clusters.remove(zone.get(selectedModule));
				selectedModule = -1;
				status = "Removed module";
				init();
			}
		}).dimensions(86, y, 70, 16).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Toggle Muzzle"), b -> {
			List<ClusterModule> zone = WeaponClusters.inCluster(config.clusters, selectedCluster);
			if (selectedModule >= 0 && selectedModule < zone.size()) {
				ClusterModule m = zone.get(selectedModule);
				m.muzzle = !m.muzzle;
				if (m.muzzle && m.muzzleIdx <= 0) m.muzzleIdx = 1;
				status = m.name + " muzzle=" + m.muzzle + " idx=" + m.muzzleIdx;
				init();
			}
		}).dimensions(162, y, 90, 16).build());

		y = 102;
		List<ClusterModule> zone = WeaponClusters.inCluster(config.clusters, selectedCluster);
		for (int i = 0; i < zone.size(); i++) {
			ClusterModule m = zone.get(i);
			int idx = i;
			String label = String.format("#%d %s [%s] %s", m.slot, m.name, m.model,
					m.muzzle ? "M" + m.muzzleIdx : "-");
			addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
				selectedModule = idx;
				init();
			}).dimensions(10, y, 220, 14).build());
			y += 16;
			if (y > height - 120) break;
		}

		// Edit selected module transforms
		if (selectedModule >= 0 && selectedModule < zone.size()) {
			ClusterModule m = zone.get(selectedModule);
			int ey = Math.min(y + 8, height - 110);
			addEdit(ey, "fwd", m.posFwd, v -> m.posFwd = v);
			addEdit(ey + 18, "rgt", m.posRgt, v -> m.posRgt = v);
			addEdit(ey + 36, "up", m.posUp, v -> m.posUp = v);
			addEdit(ey + 54, "scale", m.scaleX, v -> m.scaleX = m.scaleY = m.scaleZ = v);
			addEdit(ey + 72, "muzzleIdx", m.muzzleIdx, v -> m.muzzleIdx = Math.max(1, v.intValue()));

			TextFieldWidget nameF = new TextFieldWidget(textRenderer, width - 160, ey, 140, 14, Text.literal("name"));
			nameF.setText(m.name);
			nameF.setChangedListener(s -> m.name = s);
			fields.add(nameF);
			addSelectableChild(nameF);

			TextFieldWidget modelF = new TextFieldWidget(textRenderer, width - 160, ey + 18, 140, 14, Text.literal("model"));
			modelF.setText(m.model);
			modelF.setChangedListener(s -> m.model = s);
			fields.add(modelF);
			addSelectableChild(modelF);
		}
	}

	private void addEdit(int y, String label, float value, java.util.function.Consumer<Float> setter) {
		TextFieldWidget tf = new TextFieldWidget(textRenderer, width - 160, y, 60, 14, Text.literal(label));
		tf.setText(String.valueOf(value));
		tf.setChangedListener(s -> {
			try { setter.accept(Float.parseFloat(s.trim())); } catch (Exception ignored) {}
		});
		fields.add(tf);
		addSelectableChild(tf);
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
		if (tab == Tab.CLUSTERS) return;
		List<WeaponSchema.Field> schema = currentSchema();
		Map<String, Object> data = currentMap();
		for (int i = 0; i < fields.size() && i < schema.size(); i++) {
			data.put(schema.get(i).key(), parse(schema.get(i), fields.get(i).getText()));
		}
		Object cn = data.get("ClassName");
		if (cn != null) config.targetWeaponId = ConstructionRegistry.normalize(String.valueOf(cn));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);

		if (tab == Tab.CLUSTERS) {
			renderClusters(context);
		} else {
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
		}

		for (TextFieldWidget tf : fields) {
			if (tab == Tab.CLUSTERS) tf.render(context, mouseX, mouseY, delta);
		}

		context.drawTextWithShadow(textRenderer, Text.literal(status), 10, height - 38, 0x88FF88);
		super.render(context, mouseX, mouseY, delta);
	}

	private void renderClusters(DrawContext context) {
		context.drawTextWithShadow(textRenderer, Text.literal("Target weapon id"), 10, 34, 0xCCDDEE);
		context.drawTextWithShadow(textRenderer,
				Text.literal("§e" + selectedCluster + "§r — "
						+ WeaponClusters.LABELS[WeaponClusters.rank(selectedCluster)]),
				10, 74, 0xFFFFFF);

		// Schematic preview of modules relative to camera
		int px = width - 170, py = 54, pw = 150, ph = 90;
		context.fill(px, py, px + pw, py + ph, 0x88000000);
		context.fill(px, py, px + pw, py + 1, 0xFF445566);
		context.fill(px, py + ph - 1, px + pw, py + ph, 0xFF445566);
		context.fill(px, py, px + 1, py + ph, 0xFF445566);
		context.fill(px + pw - 1, py, px + pw, py + ph, 0xFF445566);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("VIEW"), px + pw / 2, py + 2, 0x8899AA);
		int cx = px + pw / 2, cy = py + ph / 2 + 6;
		for (ClusterModule m : config.clusters) {
			int col = colorFor(m.cluster);
			int mx = cx + (int) (m.posRgt * 1.6f);
			int my = cy - (int) (m.posUp * 1.6f);
			int s = Math.max(3, (int) (4 * m.scaleX));
			context.fill(mx - s, my - s, mx + s, my + s, col);
			if (m.muzzle) context.fill(mx - 1, my - 1, mx + 2, my + 2, 0xFFFFFFFF);
		}
		context.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF00DCFF); // camera

		if (selectedModule >= 0) {
			context.drawTextWithShadow(textRenderer, Text.literal("name"), width - 200, Math.min(height - 110, 110), 0xAAAAAA);
			context.drawTextWithShadow(textRenderer, Text.literal("model"), width - 200, Math.min(height - 92, 128), 0xAAAAAA);
			int ey = Math.min(WeaponClusters.inCluster(config.clusters, selectedCluster).size() * 16 + 110, height - 110);
			String[] labs = {"fwd", "rgt", "up", "scale", "mIdx"};
			for (int i = 0; i < labs.length; i++) {
				context.drawTextWithShadow(textRenderer, Text.literal(labs[i]), width - 200, ey + i * 18, 0xAAAAAA);
			}
		}

		int muzzles = WeaponClusters.extractMuzzles(config.clusters).size();
		context.drawTextWithShadow(textRenderer,
				Text.literal("Modules: " + config.clusters.size() + "  Muzzles: " + muzzles
						+ (ConstructionRegistry.hasOverride(config.targetWeaponId) ? "  §aOVERRIDE" : "  §7default")),
				10, height - 52, 0xAACCFF);
	}

	private static int colorFor(String cluster) {
		int i = WeaponClusters.rank(cluster);
		int[] c = WeaponClusters.COLORS[i];
		return 0xFF000000 | (c[0] << 16) | (c[1] << 8) | c[2];
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
