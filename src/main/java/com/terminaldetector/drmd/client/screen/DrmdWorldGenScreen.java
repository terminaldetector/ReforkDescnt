package com.terminaldetector.drmd.client.screen;

import com.terminaldetector.drmd.world.DrmdServerConfig;
import com.terminaldetector.drmd.world.WorldFeatures;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * World/biome generation options, off a button on the vanilla Create World screen
 * ({@code CreateWorldScreenMixin}).
 *
 * <p>Backs {@link DrmdServerConfig} directly rather than staging changes for a "confirm" step: every
 * click here writes {@code config/drmd-server.properties} immediately and applies to
 * {@link WorldFeatures} live, the same apply-on-click behaviour {@code DescentSettingsScreen} already
 * uses for its own toggles. That is also why this reads its current values straight from those two
 * classes' static fields on every {@link #init()} instead of caching them on the screen — a value
 * changed here has nowhere else to live in between.
 *
 * <p>Global, not per-world: {@code DescentSession.seedWorld} locks the resolved world kind into the
 * new world's own save data the moment it is first seeded, so a later visit to this screen changes
 * what the <em>next</em> world becomes without touching any world already created — the same rule the
 * one setting exposed here before this screen existed ({@code psychedelicWorlds}) already followed.
 */
public class DrmdWorldGenScreen extends Screen {
	private final Screen parent;

	public DrmdWorldGenScreen(Screen parent) {
		super(Text.translatable("screen.drmd.worldgen"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		DrmdServerConfig.load();
		this.clearChildren();

		int cx = this.width / 2;
		int left = cx - 155;
		int right = cx + 5;
		int y = 40;

		y = kindRow(cx, y) + 8;

		y = row(y,
				toggle(left, y, "options.drmd.nether_band", WorldFeatures.NETHER_BAND,
						v -> save(DrmdServerConfig.worldKind, v, WorldFeatures.END_BAND,
								WorldFeatures.KLONDIKE_ISLANDS, WorldFeatures.ORBIT_JUNK,
								WorldFeatures.MACRO_WORLDGEN, WorldFeatures.SURFACE_DISTRICTS)),
				toggle(right, y, "options.drmd.end_band", WorldFeatures.END_BAND,
						v -> save(DrmdServerConfig.worldKind, WorldFeatures.NETHER_BAND, v,
								WorldFeatures.KLONDIKE_ISLANDS, WorldFeatures.ORBIT_JUNK,
								WorldFeatures.MACRO_WORLDGEN, WorldFeatures.SURFACE_DISTRICTS)));
		y = row(y,
				toggle(left, y, "options.drmd.klondike_islands", WorldFeatures.KLONDIKE_ISLANDS,
						v -> save(DrmdServerConfig.worldKind, WorldFeatures.NETHER_BAND, WorldFeatures.END_BAND,
								v, WorldFeatures.ORBIT_JUNK,
								WorldFeatures.MACRO_WORLDGEN, WorldFeatures.SURFACE_DISTRICTS)),
				toggle(right, y, "options.drmd.macro_worldgen", WorldFeatures.MACRO_WORLDGEN,
						v -> save(DrmdServerConfig.worldKind, WorldFeatures.NETHER_BAND, WorldFeatures.END_BAND,
								WorldFeatures.KLONDIKE_ISLANDS, WorldFeatures.ORBIT_JUNK,
								v, WorldFeatures.SURFACE_DISTRICTS)));
		row(y,
				toggle(left, y, "options.drmd.surface_districts", WorldFeatures.SURFACE_DISTRICTS,
						v -> save(DrmdServerConfig.worldKind, WorldFeatures.NETHER_BAND, WorldFeatures.END_BAND,
								WorldFeatures.KLONDIKE_ISLANDS, WorldFeatures.ORBIT_JUNK,
								WorldFeatures.MACRO_WORLDGEN, v)),
				toggle(right, y, "options.drmd.orbit_junk", WorldFeatures.ORBIT_JUNK,
						v -> save(DrmdServerConfig.worldKind, WorldFeatures.NETHER_BAND, WorldFeatures.END_BAND,
								WorldFeatures.KLONDIKE_ISLANDS, v,
								WorldFeatures.MACRO_WORLDGEN, WorldFeatures.SURFACE_DISTRICTS)));

		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
				.dimensions(cx - 100, this.height - 28, 200, 20).build());
	}

	/** Three-way world-kind choice — a full-width button each, current pick bolded green. */
	private int kindRow(int cx, int y) {
		int w = 320 / 3 - 4;
		int x0 = cx - 160;
		kindButton(x0, y, w, DrmdServerConfig.WorldKind.STOCK, "options.drmd.kind.stock");
		kindButton(x0 + w + 6, y, w, DrmdServerConfig.WorldKind.PSYCHEDELIC, "options.drmd.kind.psychedelic");
		kindButton(x0 + (w + 6) * 2, y, w, DrmdServerConfig.WorldKind.INFINITE_MEGACITY,
				"options.drmd.kind.infinite_megacity");
		return y + 20;
	}

	private void kindButton(int x, int y, int w, DrmdServerConfig.WorldKind kind, String key) {
		boolean current = DrmdServerConfig.worldKind == kind;
		Text label = current
				? Text.translatable(key).formatted(Formatting.GREEN, Formatting.BOLD)
				: Text.translatable(key);
		addDrawableChild(ButtonWidget.builder(label, b -> {
					save(kind, WorldFeatures.NETHER_BAND, WorldFeatures.END_BAND, WorldFeatures.KLONDIKE_ISLANDS,
							WorldFeatures.ORBIT_JUNK, WorldFeatures.MACRO_WORLDGEN, WorldFeatures.SURFACE_DISTRICTS);
					clearAndInit();
				})
				.dimensions(x, y, w, 20).build());
	}

	private ButtonWidget toggle(int x, int y, String key, boolean value, java.util.function.Consumer<Boolean> onFlip) {
		return ButtonWidget.builder(label(key, value), b -> {
					onFlip.accept(!value);
					clearAndInit();
				})
				.dimensions(x, y, 150, 20).build();
	}

	private int row(int y, ButtonWidget a, ButtonWidget b) {
		addDrawableChild(a);
		addDrawableChild(b);
		return y + 22;
	}

	private static Text label(String key, boolean on) {
		return Text.translatable(key).append(": ")
				.append(Text.translatable(on ? "options.on" : "options.off"));
	}

	private static void save(DrmdServerConfig.WorldKind kind, boolean netherBand, boolean endBand,
							  boolean klondikeIslands, boolean orbitJunk, boolean macroWorldgen,
							  boolean surfaceDistricts) {
		DrmdServerConfig.save(kind, netherBand, endBand, klondikeIslands, orbitJunk, macroWorldgen, surfaceDistricts);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0x5FE08A);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("options.drmd.worldgen_hint"), this.width / 2, 24, 0x7A8A80);
	}

	@Override
	public void close() {
		if (this.client != null) this.client.setScreen(parent);
	}
}
