package com.terminaldetector.drmd.client.screen;

import com.terminaldetector.drmd.world.DrmdServerConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/** World creation defaults and optional terrain snapshots, opened from the vanilla Create World screen. */
public class DrmdWorldGenScreen extends Screen {
	private static final int CONTENT_TOP = 42;
	private static final int ROW = 22;
	private static final int FOOTER_HEIGHT = 34;
	private static final int SECTION_HEIGHT = 16;
	private static final int SECTION_GAP = 6;

	private final Screen parent;
	private final List<Heading> headings = new ArrayList<>();
	private boolean featuresHidden;
	private int scroll;
	private int viewBottom;
	private int contentHeight;
	private int hiddenFeaturesHintY = -1;

	private record Heading(int y, Text text) {}

	public DrmdWorldGenScreen(Screen parent) {
		super(Text.translatable("screen.drmd.worldgen"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		DrmdServerConfig.load();
		this.clearChildren();
		headings.clear();
		featuresHidden = DrmdServerConfig.worldModLevel == DrmdServerConfig.WorldModLevel.VANILLA;
		hiddenFeaturesHintY = -1;
		contentHeight = measureContent();

		int cx = this.width / 2;
		int left = cx - 155;
		int right = cx + 5;
		viewBottom = Math.max(CONTENT_TOP + ROW, this.height - FOOTER_HEIGHT);
		int viewHeight = viewBottom - CONTENT_TOP;
		scroll = MathHelper.clamp(scroll, Math.min(0, viewHeight - contentHeight), 0);
		int y = CONTENT_TOP + scroll;

		y = section(y, "options.drmd.worldgen.section.world_shape");
		y = addRow(y,
				modeButton(cx - 155, y, 153, DrmdServerConfig.WorldModLevel.ADVANCED,
						"options.drmd.mode.advanced"),
				modeButton(cx + 2, y, 153, DrmdServerConfig.WorldModLevel.VANILLA,
						"options.drmd.mode.vanilla"));
		y += SECTION_GAP;

		y = section(y, "options.drmd.worldgen.section.world_type");
		y = addRow(y, kindButton(cx - 155, y, 310, DrmdServerConfig.WorldKind.STOCK,
				"options.drmd.kind.stock"), null);
		y = addRow(y, kindButton(cx - 155, y, 310, DrmdServerConfig.WorldKind.PSYCHEDELIC,
				"options.drmd.kind.psychedelic"), null);
		y = addRow(y, kindButton(cx - 155, y, 310, DrmdServerConfig.WorldKind.INFINITE_MEGACITY,
				"options.drmd.kind.infinite_megacity"), null);
		y += SECTION_GAP;

		y = section(y, "options.drmd.worldgen.section.features");
		if (featuresHidden) {
			hiddenFeaturesHintY = y;
			y += 30;
		} else {
			y = addRow(y,
					featureToggle(left, y, "options.drmd.nether_band", DrmdServerConfig.WorldFeatureSetting.NETHER_BAND),
					featureToggle(right, y, "options.drmd.end_band", DrmdServerConfig.WorldFeatureSetting.END_BAND));
			y = addRow(y,
					featureToggle(left, y, "options.drmd.klondike_islands", DrmdServerConfig.WorldFeatureSetting.KLONDIKE_ISLANDS),
					featureToggle(right, y, "options.drmd.macro_worldgen", DrmdServerConfig.WorldFeatureSetting.MACRO_WORLDGEN));
			y = addRow(y,
					featureToggle(left, y, "options.drmd.surface_districts", DrmdServerConfig.WorldFeatureSetting.SURFACE_DISTRICTS),
					featureToggle(right, y, "options.drmd.orbit_junk", DrmdServerConfig.WorldFeatureSetting.ORBIT_JUNK));
		}
		y += SECTION_GAP;

		y = section(y, "options.drmd.worldgen.section.storage");
		addInView(ButtonWidget.builder(label("options.drmd.cubic_snapshots", DrmdServerConfig.cubicSnapshots), b -> {
					DrmdServerConfig.saveWorldSelection(DrmdServerConfig.worldKind,
							DrmdServerConfig.worldModLevel, !DrmdServerConfig.cubicSnapshots);
					clearAndInit();
				})
				.tooltip(Tooltip.of(Text.translatable("options.drmd.cubic_snapshots_hint")))
				.dimensions(cx - 155, y, 310, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
				.dimensions(cx - 100, this.height - 28, 200, 20).build());
	}

	private int section(int y, String key) {
		int absoluteY = y;
		headings.add(new Heading(absoluteY, Text.translatable(key)));
		return y + SECTION_HEIGHT;
	}

	private int addRow(int y, ClickableWidget a, ClickableWidget b) {
		addInView(a);
		addInView(b);
		return y + ROW;
	}

	private void addInView(ClickableWidget widget) {
		if (widget != null && widget.getY() >= CONTENT_TOP && widget.getY() + 20 <= viewBottom) {
			addDrawableChild(widget);
		}
	}

	private ButtonWidget modeButton(int x, int y, int width, DrmdServerConfig.WorldModLevel level, String key) {
		boolean current = DrmdServerConfig.worldModLevel == level;
		Text title = current ? Text.translatable(key).formatted(Formatting.GREEN, Formatting.BOLD) : Text.translatable(key);
		return ButtonWidget.builder(title, b -> {
					DrmdServerConfig.saveWorldSelection(DrmdServerConfig.worldKind, level, DrmdServerConfig.cubicSnapshots);
					clearAndInit();
				})
				.tooltip(Tooltip.of(Text.translatable(key + ".hint")))
				.dimensions(x, y, width, 20).build();
	}

	private ButtonWidget kindButton(int x, int y, int width, DrmdServerConfig.WorldKind kind, String key) {
		boolean current = DrmdServerConfig.worldKind == kind;
		Text title = current ? Text.translatable(key).formatted(Formatting.GREEN, Formatting.BOLD) : Text.translatable(key);
		return ButtonWidget.builder(title, b -> {
					DrmdServerConfig.saveWorldSelection(kind, DrmdServerConfig.worldModLevel, DrmdServerConfig.cubicSnapshots);
					clearAndInit();
				})
				.tooltip(Tooltip.of(Text.translatable(key + ".hint")))
				.dimensions(x, y, width, 20).build();
	}

	private ButtonWidget featureToggle(int x, int y, String key, DrmdServerConfig.WorldFeatureSetting feature) {
		return ButtonWidget.builder(label(key, DrmdServerConfig.configuredFeature(feature)), b -> {
					boolean next = !DrmdServerConfig.configuredFeature(feature);
					DrmdServerConfig.saveFeature(feature, next);
					clearAndInit();
				})
				.tooltip(Tooltip.of(Text.translatable(key + ".hint")))
				.dimensions(x, y, 150, 20).build();
	}

	private static Text label(String key, boolean on) {
		return Text.translatable(key).append(": ")
				.append(Text.translatable(on ? "options.on" : "options.off"));
	}

	private int measureContent() {
		int rows = 0;
		rows += SECTION_HEIGHT + ROW + SECTION_GAP;
		rows += SECTION_HEIGHT + 3 * ROW + SECTION_GAP;
		rows += SECTION_HEIGHT + (featuresHidden ? 30 : 3 * ROW) + SECTION_GAP;
		rows += SECTION_HEIGHT + ROW;
		return rows;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseY >= CONTENT_TOP && mouseY <= viewBottom && contentHeight > viewBottom - CONTENT_TOP) {
			int before = scroll;
			int step = Math.max(ROW, (int) Math.round(Math.abs(verticalAmount) * ROW));
			int minimum = Math.min(0, viewBottom - CONTENT_TOP - contentHeight);
			scroll = MathHelper.clamp(scroll + Integer.signum((int) Math.signum(verticalAmount)) * step, minimum, 0);
			if (scroll != before) {
				clearAndInit();
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0x5FE08A);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("options.drmd.worldgen_hint"), this.width / 2, 24, 0xA6B5AA);
		for (Heading heading : headings) {
			if (heading.y() >= CONTENT_TOP && heading.y() + 10 <= viewBottom) {
				context.drawTextWithShadow(this.textRenderer, heading.text(), this.width / 2 - 155, heading.y(), 0x91BFA1);
			}
		}
		if (featuresHidden && hiddenFeaturesHintY >= CONTENT_TOP && hiddenFeaturesHintY < viewBottom) {
			int textY = hiddenFeaturesHintY + 2;
			for (var line : this.textRenderer.wrapLines(Text.translatable("options.drmd.vanilla_hides_features"), 310)) {
				if (textY + 9 > viewBottom) break;
				context.drawCenteredTextWithShadow(this.textRenderer, line, this.width / 2, textY, 0x7A8A80);
				textY += 10;
			}
		}
	}

	@Override
	public void close() {
		if (this.client != null) this.client.setScreen(parent);
	}
}
