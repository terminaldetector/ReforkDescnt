package com.terminaldetector.drmd.world.profile;

import com.terminaldetector.drmd.world.access.CubicAvailability;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.tag.TagManagerLoader;
import net.minecraft.server.DataPackContents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.dimension.DimensionType;

import java.lang.reflect.Method;
import java.util.List;

/** Read-only interoperability with the public native backend and generation window. */
public final class WorldProfileNative {
    private static final TagKey<DimensionType> CUBIC_TAG = TagKey.of(
            RegistryKeys.DIMENSION_TYPE, Identifier.of("bettercubicchunks", "cubic"));
    private static final RegistryKey<DimensionType> DEFAULT = RegistryKey.of(
            RegistryKeys.DIMENSION_TYPE, Identifier.of("bettercubicchunks", "overworld"));
    private static final RegistryKey<DimensionType> HUGE = RegistryKey.of(
            RegistryKeys.DIMENSION_TYPE, Identifier.of("drmd", "ungravity_huge"));

    private record Access(Class<?> scratchChunk, Method window, boolean hugePacking) {}

    private WorldProfileNative() {}

    private static class Backend {
        private static final Access API = load();

        private static Access load() {
            if (!FabricLoader.getInstance().isModLoaded("bettercubicchunks")) return null;
            try {
                Class<?> scratch = Class.forName("dev.fallingcloud.bettercubicchunks.server.gen.CubeScratchChunk");
                Method window = scratch.getMethod("window");
                if (!HeightLimitView.class.isAssignableFrom(window.getReturnType())) return null;
                Object packing = Class.forName("dev.fallingcloud.bettercubicchunks.core.ActivePacking")
                        .getField("PACKING").get(null);
                return new Access(scratch, window, packing instanceof Enum<?> mode && mode.name().equals("HUGE"));
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }
    }

    public static boolean hasCubeApi() { return Backend.API != null; }

    public static boolean hasHugePacking() { return hasCubeApi() && Backend.API.hugePacking; }

    public static RegistryEntry<DimensionType> dimension(DynamicRegistryManager registries, DataPackContents contents,
            WorldProfile.Scale scale) {
        if (!hasCubeApi() || scale == WorldProfile.Scale.STOCK
                || (scale == WorldProfile.Scale.HUGE && !hasHugePacking())) return null;
        RegistryKey<DimensionType> key = scale == WorldProfile.Scale.HUGE ? HUGE : DEFAULT;
        return registries.get(RegistryKeys.DIMENSION_TYPE).getEntry(key)
                .filter(entry -> supportsHeight(entry.value(), scale) && taggedCubic(contents, entry, key)).orElse(null);
    }

    private static boolean taggedCubic(DataPackContents contents, RegistryEntry<DimensionType> entry,
            RegistryKey<DimensionType> key) {
        if (entry.isIn(CUBIC_TAG)) return true;
        // At Create World time the validated pack tags can still be pending registry application.
        return contents.getContents().stream().filter(TagManagerLoader.class::isInstance)
                .map(TagManagerLoader.class::cast).flatMap(loader -> loader.getRegistryTags().stream())
                .filter(tags -> tags.key().equals(RegistryKeys.DIMENSION_TYPE))
                .flatMap(tags -> tags.tags().getOrDefault(CUBIC_TAG.id(), List.of()).stream())
                .anyMatch(member -> member.getKey().filter(key::equals).isPresent());
    }

    public static int minY(WorldProfile.Scale scale) {
        return switch (scale) {
            case STOCK -> -64;
            case CUBIC_DEFAULT -> -16384;
            case HUGE -> -65536;
        };
    }

    public static int height(WorldProfile.Scale scale) {
        return switch (scale) {
            case STOCK -> 384;
            case CUBIC_DEFAULT -> 32768;
            case HUGE -> 196608;
        };
    }

    private static boolean supportsHeight(DimensionType type, WorldProfile.Scale scale) {
        int bottom = type.minY();
        int top = bottom + type.height();
        return bottom == minY(scale) && type.height() == height(scale)
                && representable(bottom) && representable(top - 1);
    }

    private static boolean representable(int y) {
        BlockPos pos = new BlockPos(0, y, 0);
        return pos.equals(BlockPos.fromLong(pos.asLong()));
    }

    static HeightLimitView generationWindow(Chunk chunk) {
        Access api = Backend.API;
        if (api == null || !api.scratchChunk.isInstance(chunk)) return null;
        try {
            return (HeightLimitView) api.window.invoke(chunk);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read the native Ungravity generation window", failure);
        }
    }

    public static void validate(ServerWorld world, WorldProfile profile) {
        if (CubicAvailability.backend(world) != CubicAvailability.Backend.NATIVE_CUBIC
                || !supportsHeight(world.getDimension(), profile.scale())
                || !(world.getChunkManager().getChunkGenerator() instanceof UngravityChunkGenerator generator)
                || !generator.profile().equals(profile)) {
            throw new IllegalStateException("This Ungravity save requires its original native cubic scale and world profile");
        }
    }
}
