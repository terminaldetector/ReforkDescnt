package com.terminaldetector.drmd.world.profile;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terminaldetector.drmd.DescentMod;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GeneratorOptions;

import java.util.Optional;

public final class WorldProfiles {
    private WorldProfiles() {}

    private record GeneratorProfile(GeneratorOptions options, Optional<WorldProfile> profile) {}

    public static MapCodec<GeneratorOptions> extendGeneratorOptionsCodec(MapCodec<GeneratorOptions> original) {
        return RecordCodecBuilder.<GeneratorProfile>mapCodec(instance -> instance.group(
                original.forGetter(GeneratorProfile::options),
                WorldProfile.CODEC.optionalFieldOf("drmd_profile").forGetter(GeneratorProfile::profile)
        ).apply(instance, GeneratorProfile::new)).flatXmap(entry -> {
            WorldProfile profile = entry.profile.orElse(null);
            if (profile != null && profile.seed() != entry.options.getSeed()) {
                return DataResult.error(() -> "World seed and Ungravity profile seed differ");
            }
            return DataResult.success(WorldProfile.attach(entry.options, profile));
        }, options -> DataResult.success(new GeneratorProfile(options, Optional.ofNullable(WorldProfile.fromOptions(options)))));
    }

    public static void register() {
        Registry.register(Registries.CHUNK_GENERATOR, Identifier.of(DescentMod.MOD_ID, "ungravity_volume"),
                UngravityChunkGenerator.CODEC);
        if (WorldProfileNative.hasHugePacking()) {
            var mod = FabricLoader.getInstance().getModContainer(DescentMod.MOD_ID).orElseThrow();
            ResourceManagerHelper.registerBuiltinResourcePack(Identifier.of(DescentMod.MOD_ID, "ungravity_huge"), mod,
                    Text.translatable("pack.drmd.ungravity_huge"), ResourcePackActivationType.DEFAULT_ENABLED);
        }
        WorldProfileNetworking.register();
    }
}
