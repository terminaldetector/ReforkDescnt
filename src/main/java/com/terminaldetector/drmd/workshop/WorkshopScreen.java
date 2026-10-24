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
	private enum Tab { STATS, PROJECTILE, FLAK, GUIDANCE, CLUSTERS, MODELS }

	private Tab tab = Tab.STATS;
	private WeaponConfig config = new WeaponConfig();
	private final List<TextFieldWidget> fields = new ArrayList<>();
	private String status = "Ready — construction adapted to DRMD weapons";
	private String selectedCluster = "Center";
	private int selectedModule = -1;
	private String presetName = "evening-build";
    private int transformPage;
    private int modelIndex;
    private float previewYaw = -24, previewPitch = -22;
    private long previewFire;
    private int previewFaces;
    private String acceptedWeaponId="";
    private final List<String> modelLabels = new ArrayList<>();
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
        addTab("Models", Tab.MODELS, bx + 342, 60);

		addDrawableChild(ButtonWidget.builder(Text.literal("Cannon"), b -> loadTemplate("cannon"))
				.dimensions(width - 250, 32, 56, 18).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Missile"), b -> loadTemplate("missile"))
				.dimensions(width - 190, 32, 56, 18).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Flak T"), b -> loadTemplate("flak"))
				.dimensions(width - 130, 32, 48, 18).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Seeker"), b -> loadTemplate("seeker"))
				.dimensions(width - 78, 32, 48, 18).build());

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

		if (tab == Tab.MODELS) rebuildModelsUi();
        else if (tab == Tab.CLUSTERS) rebuildClusterUi();
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

		if (client == null || client.player == null || !client.player.isCreative()) {
            status = "Apply Build requires Creative (and operator on a dedicated server)"; return;
        }
        // The authoritative server saves/broadcasts the construction.

		ClientPlayNetworking.send(new ModNetworking.ConstructionPayload(id, ClusterModule.listToNbt(mods)));
		status = "Sent construction/muzzles; awaiting server: " + id + " (" + mods.size() + " modules, "
				+ WeaponClusters.extractMuzzles(mods).size() + " muzzles)";
	}

	private void resetLayout() {
		String id = config.targetWeaponId;
        if (client == null || client.player == null || !client.player.isCreative()) {
            status = "Reset Layout requires Creative"; return;
        }
        ClientPlayNetworking.send(new ModNetworking.ConstructionPayload(id, new net.minecraft.nbt.NbtList()));
		config.clusters.clear(); config.clusters.addAll(DefaultLayouts.forWeapon(id));
		status = "Reset " + id + " to default Doom-style layout";
		init();
	}

	private Map<String, Object> currentMap() {
		return switch (tab) {
			case STATS -> config.weapon;
			case PROJECTILE -> config.projectile;
			case FLAK -> config.flak;
			case GUIDANCE -> config.guidance;
			case CLUSTERS, MODELS -> config.weapon; // unused
		};
	}

	private List<WeaponSchema.Field> currentSchema() {
		return switch (tab) {
			case STATS -> WeaponSchema.WEAPON;
			case PROJECTILE -> WeaponSchema.PROJECTILE;
			case FLAK -> WeaponSchema.FLAK;
			case GUIDANCE -> WeaponSchema.GUIDANCE;
			case CLUSTERS, MODELS -> List.of();
		};
	}

	private void rebuildFields() {
		Map<String, Object> data = currentMap();
		List<WeaponSchema.Field> schema = currentSchema();
		int y = 62;
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
		int y = 62;
		// Target weapon id field
		TextFieldWidget target = new TextFieldWidget(textRenderer, 120, y, 120, 16, Text.literal("target"));
		target.setText(config.targetWeaponId);
		target.setChangedListener(s -> config.targetWeaponId = ConstructionRegistry.normalize(s));
		fields.add(target);
		addSelectableChild(target);

		addDrawableChild(ButtonWidget.builder(Text.literal("Load Defaults"), b -> {
			config.clusters.clear();
            config.clusters.addAll(DefaultLayouts.forWeapon(config.targetWeaponId));
			status = "Loaded default layout for " + config.targetWeaponId;
			init();
		}).dimensions(250, y, 90, 16).build());

		y = 84;
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

		y = 110;
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

		y = 132;
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
		if (tab == Tab.CLUSTERS || tab == Tab.MODELS) return;
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
		context.fill(0, 0, width, height, 0xb0101720);
        context.draw();
        // Screen.render performs the vanilla background/blur pass before its children in 1.21.
        // Draw custom mesh and manually rendered text fields after that pass.
        super.render(context, mouseX, mouseY, delta);

        if (tab == Tab.MODELS) {
            renderModels(context);
        } else if (tab == Tab.CLUSTERS) {
			renderClusters(context);
		} else {
			List<WeaponSchema.Field> schema = currentSchema();
			int y = 62;
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
			if (tab == Tab.CLUSTERS || tab == Tab.MODELS) tf.render(context, mouseX, mouseY, delta);
		}

		context.drawTextWithShadow(textRenderer, Text.literal(status), 10, height - 38, 0x88FF88);
	}

	private void renderClusters(DrawContext context) {
		context.drawTextWithShadow(textRenderer, Text.literal("Target weapon id"), 10, 64, 0xCCDDEE);
		context.drawTextWithShadow(textRenderer,
				Text.literal("§e" + selectedCluster + "§r — "
						+ WeaponClusters.LABELS[WeaponClusters.rank(selectedCluster)]),
				10, 104, 0xFFFFFF);

		// Schematic preview of modules relative to camera
		int px = width - 170, py = 84, pw = 150, ph = 90;
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

    private java.nio.file.Path presetDirectory() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("drmd/weapon-presets");
    }

    /** UI and client verification both enter the same selection/save/apply path. */
    public void selectHybridSample(String model) {
        flushFields();
        config = WeaponConfig.fromExistingWeapon(HybridWeaponModels.weapon(model));
        config.clusters.clear(); config.clusters.addAll(HybridWeaponModels.sample(model));
        acceptedWeaponId=""; presetName = model; modelIndex = 0; transformPage = 0; tab = Tab.MODELS;
        status = "Draft: " + model + "; Apply Build publishes construction and muzzles";
        init();
    }
    public void savePreset() {
        flushFields();
        try { WorkshopPresets.save(presetDirectory(),presetName,config); status = "Saved preset: " + presetName; }
        catch(java.io.IOException | IllegalArgumentException e) { status = "Save: " + e.getMessage(); }
    }
    public void loadPreset() {
        try { config = WorkshopPresets.load(presetDirectory(),presetName); modelIndex=0; tab=Tab.MODELS;
            status="Loaded preset: " + presetName + "; Apply Build to publish"; init(); }
        catch(java.io.IOException | IllegalArgumentException e) { status="Load: " + e.getMessage(); }
    }
    public void publishConstruction() { applyConstruction(); }
    public String acceptedWeaponId() { return acceptedWeaponId; }
    public void constructionAccepted(String id) {
        acceptedWeaponId=id;
        if(config.targetWeaponId.equals(id)) status="Server saved construction/muzzles: " + id + "; stats are export-only";
    }
    public int previewFaceCount() { return previewFaces; }
    public List<ClusterModule> draftModules() { return config.clusters.stream().map(ClusterModule::copy).toList(); }

    private void rebuildModelsUi() {
        modelLabels.clear();
        String[] ids={HybridWeaponModels.IMPULSE,HybridWeaponModels.PLASMA,HybridWeaponModels.RAIL};
        String[] labels={"Impulse", "Plasma", "Gravity rail"};
        for(int i=0;i<ids.length;i++) {
            String id=ids[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(labels[i]), b -> selectHybridSample(id))
                    .dimensions(10+i*94,60,90,18).build());
        }
        TextFieldWidget name = new TextFieldWidget(textRenderer,10,84,130,16,Text.literal("Preset name"));
        name.setMaxLength(48); name.setText(presetName); name.setChangedListener(v -> presetName=v);
        fields.add(name); addSelectableChild(name);
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> savePreset()).dimensions(146,84,48,16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Load"), b -> loadPreset()).dimensions(200,84,48,16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cycle recoil"), b -> previewFire=System.nanoTime())
                .dimensions(254,84,90,16).build());
        if(config.clusters.isEmpty()) return;
        modelIndex=Math.floorMod(modelIndex,config.clusters.size());
        ClusterModule m=config.clusters.get(modelIndex);
        addDrawableChild(ButtonWidget.builder(Text.literal("<"),b->{ modelIndex--; init(); }).dimensions(10,110,20,16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"),b->{ modelIndex++; init(); }).dimensions(164,110,20,16).build());
        String[] pages={"Position", "Angles", "Scale XYZ", "Tint RGB", "Animation"};
        addDrawableChild(ButtonWidget.builder(Text.literal(pages[transformPage]), b->{ transformPage=(transformPage+1)%pages.length; init(); })
                .dimensions(10,134,174,18).build());
        switch(transformPage) {
            case 0 -> { modelEdit("Forward",m.posFwd,v->m.posFwd=v); modelEdit("Right",m.posRgt,v->m.posRgt=v); modelEdit("Up",m.posUp,v->m.posUp=v); }
            case 1 -> { modelEdit("Pitch",m.pitch,v->m.pitch=v); modelEdit("Yaw",m.yaw,v->m.yaw=v); modelEdit("Roll",m.roll,v->m.roll=v); }
            case 2 -> { modelEdit("Scale X",m.scaleX,v->m.scaleX=Math.clamp(v,.1f,4)); modelEdit("Scale Y",m.scaleY,v->m.scaleY=Math.clamp(v,.1f,4)); modelEdit("Scale Z",m.scaleZ,v->m.scaleZ=Math.clamp(v,.1f,4)); }
            case 3 -> { modelEdit("Red",m.colorR,v->m.colorR=Math.clamp(v.intValue(),0,255)); modelEdit("Green",m.colorG,v->m.colorG=Math.clamp(v.intValue(),0,255)); modelEdit("Blue",m.colorB,v->m.colorB=Math.clamp(v.intValue(),0,255)); }
            case 4 -> { modelEdit("Bolt travel",m.kick,v->m.kick=Math.clamp(v,0,8)); modelEdit("Alpha",m.colorA,v->m.colorA=Math.clamp(v.intValue(),0,255)); }
        }
    }
    private void modelEdit(String label,float value,java.util.function.Consumer<Float> setter) {
        int y=height<300?150+modelLabels.size()*16:160+modelLabels.size()*22; modelLabels.add(label);
        TextFieldWidget tf=new TextFieldWidget(textRenderer,96,y,88,16,Text.literal(label));
        tf.setText(Float.toString(value));
        tf.setChangedListener(s->{try { float v=Float.parseFloat(s); if(Float.isFinite(v)) setter.accept(v); }catch(NumberFormatException ignored) {}});
        fields.add(tf); addSelectableChild(tf);
    }
    private void renderModels(DrawContext context) {
        int px=200,py=110,pw=Math.max(100,width-px-10),ph=Math.max(80,height-py-62);
        context.fill(px,py,px+pw,py+ph,0xff121c27);
        context.drawTextWithShadow(textRenderer,"Drag to orbit · wheel to tilt",px+6,py+6,0x99bacc);
        float elapsed=(System.nanoTime()-previewFire)/1_000_000_000f;
        float recoil=elapsed>=0&&elapsed<.4f?(float)Math.sin(Math.PI*elapsed/.4f):0;
        previewFaces=com.terminaldetector.drmd.client.render.WorkshopMeshPreview.draw(context,config.clusters,
                px,py+18,pw,ph-18,previewYaw,previewPitch,recoil);
        if(!config.clusters.isEmpty()) {
            var m=config.clusters.get(modelIndex);
            context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(m.name,128),34,114,0xc4dae5);
        }
        for(int i=0;i<modelLabels.size();i++) context.drawTextWithShadow(textRenderer,modelLabels.get(i),10,(height<300?154+i*16:164+i*22),0xc4dae5);
        context.drawTextWithShadow(textRenderer,"Live: " + config.targetWeaponId + " · meshes " + config.clusters.size(),200,height-52,0x9ac5db);
        if(previewFaces==0) context.drawTextWithShadow(textRenderer,"Select a hybrid sample",px+12,py+40,0xaaccff);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(tab==Tab.MODELS && x>=200 && y>=110 && y<height-62 && button==0) {
            previewYaw+=(float)dx; previewPitch=Math.clamp(previewPitch+(float)dy,-80,80); return true;
        }
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(tab==Tab.MODELS && x>=200 && y>=110 && y<height-62) {
            previewPitch=Math.clamp(previewPitch+(float)vertical*5,-80,80); return true;
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
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
