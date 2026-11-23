package com.terminaldetector.drmd.client.screen;

import com.terminaldetector.drmd.world.profile.WorldProfile;
import com.terminaldetector.drmd.world.profile.WorldProfile.Mode;
import com.terminaldetector.drmd.world.profile.WorldProfile.Performance;
import com.terminaldetector.drmd.world.profile.WorldProfile.Preset;
import com.terminaldetector.drmd.world.profile.WorldProfile.Scale;
import com.terminaldetector.drmd.world.profile.WorldProfile.Start;
import com.terminaldetector.drmd.world.profile.WorldProfileNative;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/** Edits only the current Create World draft. */
public final class DrmdWorldGenScreen extends Screen {
    private static final String KEY = "screen.drmd.world_profile.";
    private static final List<Preset> PRESETS = List.of(Preset.UNGRAVITY_CONTINUUM, Preset.INFINITY_PSYCHEDELIC,
            Preset.VERTICAL_MEGACITY, Preset.ABYSS_COMPLEX, Preset.ORBITAL_RUINS);

    public record Draft(Mode mode, Preset preset, Scale scale, Performance performance, Start start) {
        public static Draft defaults() {
            return new Draft(Mode.VANILLA, Preset.UNGRAVITY_CONTINUUM, Scale.CUBIC_DEFAULT,
                    Performance.BALANCED, Start.GLIDER_DOCK);
        }

        public static Draft of(WorldProfile profile) {
            return profile.mode() == Mode.VANILLA ? defaults()
                    : new Draft(profile.mode(), profile.preset(), profile.scale(), profile.performance(), profile.start());
        }

        public WorldProfile resolve(long seed) {
            return mode == Mode.VANILLA ? WorldProfile.vanilla(seed)
                    : new WorldProfile(WorldProfile.CURRENT_SCHEMA, mode, preset, scale, performance, start, seed);
        }
    }

    private final CreateWorldScreen parent;
    private final BiConsumer<Draft, String> onApply;
    private Mode mode;
    private Preset preset;
    private Scale scale;
    private Performance performance;
    private Start start;
    private String seed;
    private boolean cubicAvailable;
    private boolean hugeAvailable;
    private CyclingButtonWidget<Preset> presetButton;
    private CyclingButtonWidget<Scale> scaleButton;
    private CyclingButtonWidget<Performance> performanceButton;
    private CyclingButtonWidget<Start> startButton;
    private ButtonWidget apply;

    public DrmdWorldGenScreen(CreateWorldScreen parent, Draft draft, BiConsumer<Draft, String> onApply) {
        super(Text.translatable(KEY + "title"));
        this.parent = parent;
        this.onApply = onApply;
        this.seed = parent.getWorldCreator().getSeed();
        mode = draft.mode;
        preset = draft.preset;
        scale = draft.scale;
        performance = draft.performance;
        start = draft.start;
    }

    @Override protected void init() {
        var options = parent.getWorldCreator().getGeneratorOptionsHolder();
        var registries = options.getCombinedRegistryManager();
        cubicAvailable = WorldProfileNative.dimension(registries, options.dataPackContents(), Scale.CUBIC_DEFAULT) != null;
        hugeAvailable = WorldProfileNative.dimension(registries, options.dataPackContents(), Scale.HUGE) != null;
        if (!hugeAvailable && scale == Scale.HUGE) scale = Scale.CUBIC_DEFAULT;

        int panelWidth = Math.min(width - 24, 336);
        int x = (width - panelWidth) / 2;
        int y = Math.max(38, (height - 164) / 2);
        addDrawableChild(CyclingButtonWidget.<Mode>builder(DrmdWorldGenScreen::name).values(Mode.values()).initially(mode)
                .build(x, y, panelWidth, 20, text("mode"), (button, value) -> {
                    mode = value;
                    refreshAvailability();
                }));
        presetButton = addDrawableChild(CyclingButtonWidget.<Preset>builder(DrmdWorldGenScreen::name)
                .values(PRESETS).initially(preset).build(x, y + 23, panelWidth, 20, text("preset"),
                        (button, value) -> preset = value));
        scaleButton = addDrawableChild(CyclingButtonWidget.<Scale>builder(DrmdWorldGenScreen::name)
                .values(hugeAvailable ? List.of(Scale.CUBIC_DEFAULT, Scale.HUGE) : List.of(Scale.CUBIC_DEFAULT))
                .initially(scale).build(x, y + 46, panelWidth, 20, text("scale"), (button, value) -> {
                    scale = value;
                    refreshAvailability();
                }));
        performanceButton = addDrawableChild(CyclingButtonWidget.<Performance>builder(DrmdWorldGenScreen::name)
                .values(Performance.values()).initially(performance)
                .build(x, y + 69, panelWidth, 20, text("performance"), (button, value) -> performance = value));
        startButton = addDrawableChild(CyclingButtonWidget.<Start>builder(DrmdWorldGenScreen::name)
                .values(Start.values()).initially(start)
                .build(x, y + 92, panelWidth, 20, text("start"), (button, value) -> start = value));
        TextFieldWidget seedField = new TextFieldWidget(textRenderer, x, y + 115, panelWidth, 20, text("seed"));
        seedField.setMaxLength(128);
        seedField.setPlaceholder(text("seed"));
        seedField.setText(seed);
        seedField.setChangedListener(value -> seed = value);
        addDrawableChild(seedField);

        int half = (panelWidth - 6) / 2;
        apply = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> {
            onApply.accept(new Draft(mode, preset, scale, performance, start), seed);
            close();
        }).dimensions(x, y + 142, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(x + half + 6, y + 142, half, 20).build());
        refreshAvailability();
    }

    private void refreshAvailability() {
        boolean ung = mode == Mode.UNGRAVITY;
        presetButton.active = performanceButton.active = startButton.active = ung;
        scaleButton.active = ung && hugeAvailable;
        apply.active = !ung || (scale == Scale.HUGE ? hugeAvailable : cubicAvailable);
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xe7fff6);
        Text status = mode == Mode.VANILLA ? text("vanilla_hint")
                : apply.active ? text("cubic_hint") : text("cubic_unavailable");
        context.drawCenteredTextWithShadow(textRenderer, status, width / 2, 23, apply.active ? 0xafd2c5 : 0xffa385);
    }

    @Override public void close() { client.setScreen(parent); }

    private static Text text(String key) { return Text.translatable(KEY + key); }

    public static Text name(Enum<?> value) { return text(value.name().toLowerCase(Locale.ROOT)); }
}
