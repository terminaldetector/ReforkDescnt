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

import java.util.ArrayList;
import java.util.List;

/**
 * DRMD options — pause menu button, {@code ,} keybind, or Controls category.
 *
 * <p>Local render/feel toggles save to {@code drmd.properties} on every change (and on close).
 * Content scrolls so GUI-scale / short windows still reach every row.
 */
public class DescentSettingsScreen extends Screen {
	private static final int ROW = 22;
	private int contentTop = 36;
    private boolean singleColumn;
    private int panelLeft,panelWidth,contentHeight;
    private List<net.minecraft.text.OrderedText> hintLines=List.of();
	private static final int SECTION = 14;
	private static final int CONTENT_HEIGHT = 5 * SECTION + 14 * ROW + 3 * 6;
	private final Screen parent;
	private final List<Heading> headings = new ArrayList<>();
	private int scroll;
    private ButtonWidget physicsButton;
	private int viewBottom;

	private record Heading(int y, Text text) {}

	public DescentSettingsScreen(Screen parent) {
		super(Text.translatable("screen.drmd.settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		DescentConfig.reload();
		this.clearChildren();
		headings.clear();

		int cx = this.width / 2;
		singleColumn=width<360;
        panelWidth=Math.min(420,width-20); panelLeft=(width-panelWidth)/2;
        int left=panelLeft;
        int right=panelLeft+(panelWidth+10)/2;
        hintLines=textRenderer.wrapLines(Text.translatable("options.drmd.keys_hint"),Math.max(100,width-24));
        contentTop=32+hintLines.size()*9+8;
        contentHeight=CONTENT_HEIGHT+(singleColumn?10*ROW:0);
		viewBottom = this.height - 32;
		int contentH = contentHeight;
		int viewH = Math.max(ROW, viewBottom - contentTop);
		int minScroll = Math.min(0, viewH - contentH);
		scroll = MathHelper.clamp(scroll, minScroll, 0);

		int y = contentTop + scroll;
		y = section(y, "options.drmd.settings.section.cockpit");
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
		y = section(y, "options.drmd.settings.section.world_view");
        physicsButton=ButtonWidget.builder(physicsLabel(),b->{
            var profile=DescentClientState.physicsProfile;
            if(profile!=null && DescentClientState.physicsCanChange)ClientPlayNetworking.send(new ModNetworking.ActionPayload(
                profile==com.terminaldetector.drmd.physics.PhysicsProfile.EXPANDED?"physics_standard":"physics_expanded"));
        }).dimensions(right,y,150,20).build();
        refreshPhysicsButton();
        y=addRow(y,viewBottom,toggle(left,y,"options.drmd.hybrid_render",DescentConfig.hybridRender,v->{
            DescentConfig.hybridRender=v;DescentConfig.save();
        }),physicsButton);

		y = addRow(y, viewBottom,
				toggle(left, y, "options.drmd.level_sky", DescentConfig.levelSky, v -> {
					DescentConfig.levelSky = v;
					DescentConfig.save();
				}),
				toggle(right, y, "options.drmd.sky_ufo_virtual_hull", DescentConfig.skyUfoVirtualHull, v -> {
					DescentConfig.skyUfoVirtualHull = v;
					DescentConfig.save();
				}));
		y = addRow(y, viewBottom,
				toggle(left, y, "options.drmd.planet_floor", DescentConfig.planetFloor, v -> {
					DescentConfig.planetFloor = v;
					DescentConfig.save();
				}),
				toggle(right, y, "options.drmd.planet_floor_with_dh", DescentConfig.planetFloorWithDistantHorizons, v -> {
					DescentConfig.planetFloorWithDistantHorizons = v;
					DescentConfig.save();
				}));
		y = section(y, "options.drmd.settings.section.portals");
		y = addRow(y, viewBottom,
				toggle(left, y, "options.drmd.mirror_reflection", DescentConfig.mirrorReflection, v -> {
					DescentConfig.mirrorReflection = v;
					DescentConfig.save();
				}),
				toggle(right, y, "options.drmd.portal_see_through", DescentConfig.portalSeeThrough, v -> {
					DescentConfig.portalSeeThrough = v;
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
		y = section(y, "options.drmd.settings.section.flight");
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
		y = section(y, "options.drmd.settings.section.tools");
		y += 6;
		y = addRow(y, viewBottom,
				ButtonWidget.builder(Text.translatable("options.drmd.export_diagnostics"), b -> {
					java.nio.file.Path written = com.terminaldetector.drmd.client.diag.DiagnosticsExport.write();
					b.setMessage(Text.literal(written == null
							? "§cCould not write — see the log"
							: "§aSaved: " + written.getFileName()));
					// The full path goes to chat, because the button is too narrow for it and the point of
					// the report is being able to find and send the file.
					if (this.client != null && this.client.player != null) {
						this.client.player.sendMessage(Text.literal(written == null
								? "§cDRMD diagnostics could not be written — see the log."
								: "§aDRMD diagnostics written to §f" + written), false);
					}
				})
						.tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
								Text.translatable("options.drmd.export_diagnostics_hint")))
						.dimensions(left, y, 310, 20).build(), null);
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
		y = addRow(y, viewBottom,
				ButtonWidget.builder(Text.translatable("screen.drmd.creative_tools"),
								b -> {
									if (this.client != null) {
										this.client.setScreen(new CreativeToolsScreen(this));
									}
								})
						.tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
								Text.translatable("options.drmd.creative_only")))
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

    private int addRow(int y,int viewBottom,ClickableWidget a,ClickableWidget b) {
        int colWidth=singleColumn||b==null?panelWidth:(panelWidth-10)/2;
        if(a!=null) {
            a.setX(panelLeft); a.setY(y); a.setWidth(colWidth);
            if(y>=contentTop && y+20<=viewBottom) addDrawableChild(a);
        }
        int secondY=singleColumn&&a!=null&&b!=null?y+ROW:y;
        if(b!=null) {
            b.setX(singleColumn?panelLeft:panelLeft+colWidth+10); b.setY(secondY); b.setWidth(colWidth);
            if(secondY>=contentTop && secondY+20<=viewBottom) addDrawableChild(b);
        }
        return secondY+ROW;
    }

    private Text physicsLabel() {
        var profile=DescentClientState.physicsProfile;
        return Text.translatable("options.drmd.physics").append(": ").append(Text.translatable(profile==null?
            "options.drmd.physics.syncing":"options.drmd.physics."+profile.name().toLowerCase(java.util.Locale.ROOT)));
    }
    private void refreshPhysicsButton() {
        if(physicsButton==null)return;
        physicsButton.setMessage(physicsLabel());
        physicsButton.active=DescentClientState.physicsProfile!=null && DescentClientState.physicsCanChange;
        physicsButton.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable(
            DescentClientState.physicsCanChange?"options.drmd.physics.tip":"options.drmd.physics.locked")));
    }
    @Override public void tick() {super.tick();refreshPhysicsButton();}

	private int section(int y, String key) {
		headings.add(new Heading(y, Text.translatable(key)));
		return y + SECTION;
	}

	private ButtonWidget toggle(int x, int y, String key, boolean initial,
								java.util.function.Consumer<Boolean> setter) {
		boolean[] state = {initial};
		return ButtonWidget.builder(label(key, state[0]), b -> {
					state[0] = !state[0];
					setter.accept(state[0]);
					b.setMessage(label(key, state[0]));
				})
				.tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable(key)))
                .dimensions(x, y, 150, 20).build();
	}

	private static Text label(String key, boolean on) {
		return Text.translatable(key).append(": ")
				.append(Text.translatable(on ? "options.on" : "options.off"));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseY < contentTop || mouseY > viewBottom) {
			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}
		int before = scroll;
		scroll += Integer.signum((int) Math.signum(verticalAmount)) * ROW;
		int contentH = contentHeight;
		int viewH = Math.max(ROW, viewBottom - contentTop);
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
		for(int i=0;i<hintLines.size();i++)
            context.drawTextWithShadow(textRenderer,hintLines.get(i),12,28+i*9,0x7A8A80);
		for (Heading heading : headings) {
			if (heading.y() >= contentTop && heading.y() + 9 <= viewBottom) {
				context.drawTextWithShadow(this.textRenderer, heading.text(), panelLeft,
						heading.y(), 0x91BFA1);
			}
		}
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
			Text full=Text.translatable(key).append(": ").append(String.format(format,value()*displayScale));
            setMessage(full);
            setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(full));
		}

		@Override
		protected void applyValue() {
			setter.accept(value());
		}
	}
}
