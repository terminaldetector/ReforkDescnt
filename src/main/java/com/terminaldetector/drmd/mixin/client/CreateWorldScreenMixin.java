package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.screen.DrmdWorldGenScreen;
import com.terminaldetector.drmd.world.profile.UngravityChunkGenerator;
import com.terminaldetector.drmd.world.profile.WorldProfile;
import com.terminaldetector.drmd.world.profile.WorldProfileNative;
import net.minecraft.client.gui.screen.NoticeScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.FixedBiomeSource;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * "DRMD World Generation" entry on the vanilla Create World screen — same {@code addDrawableChild} at
 * end of {@code init} technique as {@link GameMenuScreenMixin}, for the same reason: it is the one
 * that survives third-party launcher overlays rebuilding the menu (see that class's doc comment).
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {
    @Unique private DrmdWorldGenScreen.Draft drmd$draft;
    @Unique private DimensionOptionsRegistryHolder drmd$vanillaDimensions;

	protected CreateWorldScreenMixin(Text title) {
		super(title);
	}

	@Inject(method = "init", at = @At("RETURN"))
	private void drmd$addWorldGenButton(CallbackInfo ci) {
        var options = ((CreateWorldScreen) (Object) this).getWorldCreator().getGeneratorOptionsHolder();
        WorldProfile profile = WorldProfile.fromOptions(options.generatorOptions());
        if (drmd$draft == null && profile != null) drmd$draft = DrmdWorldGenScreen.Draft.of(profile);
		// Top-right, same corner GameMenuScreenMixin uses — clear of vanilla's centred column of
		// world-creation buttons and of the same launcher overlays that motivated that placement.
		this.addDrawableChild(ButtonWidget.builder(
                        drmd$draft == null ? Text.translatable("menu.drmd.worldgen") : DrmdWorldGenScreen.name(drmd$draft.mode()),
						b -> {
							if (this.client != null) {
                                CreateWorldScreen screen = (CreateWorldScreen) (Object) this;
                                var draft = drmd$draft == null ? DrmdWorldGenScreen.Draft.defaults() : drmd$draft;
                                this.client.setScreen(new DrmdWorldGenScreen(screen, draft, (selection, seed) -> {
                                    drmd$draft = selection;
                                    screen.getWorldCreator().setSeed(seed);
                                }));
							}
						})
				.dimensions(this.width - 128, 8, 120, 20)
				.build());
	}

    @Inject(method = "createLevel", at = @At("HEAD"), cancellable = true)
    private void drmd$applyWorldProfile(CallbackInfo callback) {
        if (drmd$draft == null) return;
        var creator = ((CreateWorldScreen) (Object) this).getWorldCreator();
        var options = creator.getGeneratorOptionsHolder();
        var registries = options.getCombinedRegistryManager();
        long seed = options.generatorOptions().getSeed();
        WorldProfile profile = drmd$draft.resolve(seed);
        DimensionOptionsRegistryHolder dimensions = options.selectedDimensions();
        if (!(dimensions.getChunkGenerator() instanceof UngravityChunkGenerator)) drmd$vanillaDimensions = dimensions;

        if (profile.mode() == WorldProfile.Mode.UNGRAVITY) {
            var type = WorldProfileNative.dimension(registries, options.dataPackContents(), profile.scale());
            if (type == null) {
                drmd$invalidProfile("cubic_required", callback);
                return;
            }
            var biome = registries.get(RegistryKeys.BIOME).entryOf(BiomeKeys.PLAINS);
            dimensions = new DimensionOptionsRegistryHolder(Map.of(DimensionOptions.OVERWORLD,
                    new DimensionOptions(type, new UngravityChunkGenerator(new FixedBiomeSource(biome), profile))));
        } else {
            dimensions = drmd$vanillaDimensions;
            if (dimensions == null || dimensions.getOrEmpty(DimensionOptions.OVERWORLD).orElseThrow()
                    .dimensionTypeEntry().value().height() != 384) {
                dimensions = registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.DEFAULT).createDimensionsRegistryHolder();
            }
            if (dimensions.getOrEmpty(DimensionOptions.OVERWORLD).orElseThrow().dimensionTypeEntry().value().height() != 384) {
                drmd$invalidProfile("stock_required", callback);
                return;
            }
        }
        GeneratorOptions worldOptions = WorldProfile.attach(
                new GeneratorOptions(seed, creator.shouldGenerateStructures(), creator.isBonusChestEnabled()), profile);
        creator.setGeneratorOptionsHolder(options.with(worldOptions, dimensions));
    }

    @Unique
    private void drmd$invalidProfile(String key, CallbackInfo callback) {
        if (client != null) {
            client.setScreen(new NoticeScreen(() -> client.setScreen(this),
                    Text.translatable("screen.drmd.world_profile.title"),
                    Text.translatable("screen.drmd.world_profile." + key)));
        }
        callback.cancel();
    }
}
