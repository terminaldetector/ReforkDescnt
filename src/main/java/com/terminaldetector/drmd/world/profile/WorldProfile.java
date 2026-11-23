package com.terminaldetector.drmd.world.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.gen.GeneratorOptions;

/** Immutable creation settings stored in this save's WorldGenSettings. */
public record WorldProfile(int schema, Mode mode, Preset preset, Scale scale,
                           Performance performance, Start start, long seed) {
    public static final int CURRENT_SCHEMA = 1;

    public enum Mode { VANILLA, UNGRAVITY }

    public enum Preset {
        NONE, UNGRAVITY_CONTINUUM, INFINITY_PSYCHEDELIC, VERTICAL_MEGACITY, ABYSS_COMPLEX, ORBITAL_RUINS
    }

    public enum Scale { STOCK, CUBIC_DEFAULT, HUGE }

    public enum Performance {
        COMPACT(6, 750_000), BALANCED(10, 1_500_000), EXTENDED(16, 3_000_000);

        private final int verticalDistance;
        private final long scheduleBudgetNanos;

        Performance(int verticalDistance, long scheduleBudgetNanos) {
            this.verticalDistance = verticalDistance;
            this.scheduleBudgetNanos = scheduleBudgetNanos;
        }

        public int verticalDistance() { return verticalDistance; }
        public long scheduleBudgetNanos() { return scheduleBudgetNanos; }
    }

    public enum Start { SURVIVAL, GLIDER_DOCK }

    public static final Codec<WorldProfile> CODEC = RecordCodecBuilder.<WorldProfile>create(instance -> instance.group(
            Codec.intRange(CURRENT_SCHEMA, CURRENT_SCHEMA).fieldOf("schema").forGetter(WorldProfile::schema),
            enumCodec(Mode.class).fieldOf("mode").forGetter(WorldProfile::mode),
            enumCodec(Preset.class).fieldOf("preset").forGetter(WorldProfile::preset),
            enumCodec(Scale.class).fieldOf("scale").forGetter(WorldProfile::scale),
            enumCodec(Performance.class).fieldOf("performance").forGetter(WorldProfile::performance),
            enumCodec(Start.class).fieldOf("start").forGetter(WorldProfile::start),
            Codec.LONG.fieldOf("seed").forGetter(WorldProfile::seed)
    ).apply(instance, WorldProfile::new)).validate(profile -> {
        boolean valid = profile.mode == Mode.VANILLA
                ? profile.preset == Preset.NONE && profile.scale == Scale.STOCK
                    && profile.start == Start.SURVIVAL && profile.performance == Performance.BALANCED
                : profile.preset != Preset.NONE && profile.scale != Scale.STOCK;
        return valid ? DataResult.success(profile) : DataResult.error(() -> "Invalid Ungravity world profile");
    });

    public static WorldProfile vanilla(long seed) {
        return new WorldProfile(CURRENT_SCHEMA, Mode.VANILLA, Preset.NONE, Scale.STOCK,
                Performance.BALANCED, Start.SURVIVAL, seed);
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(value -> {
            try {
                return DataResult.success(Enum.valueOf(type, value));
            } catch (IllegalArgumentException invalid) {
                return DataResult.error(() -> "Unknown world profile " + type.getSimpleName() + ": " + value);
            }
        }, Enum::name);
    }

    public interface Options {
        WorldProfile drmd$getWorldProfile();
        void drmd$setWorldProfile(WorldProfile profile);
    }

    public static WorldProfile fromOptions(GeneratorOptions options) {
        return ((Options) options).drmd$getWorldProfile();
    }

    public static GeneratorOptions attach(GeneratorOptions options, WorldProfile profile) {
        if (profile != null && options.getSeed() != profile.seed) {
            throw new IllegalArgumentException("World profile seed does not match generator seed");
        }
        ((Options) options).drmd$setWorldProfile(profile);
        return options;
    }

    /** Null indicates a save created without the Stage B profile. */
    public static WorldProfile get(World world) {
        if (world instanceof ServerWorld server) {
            return fromOptions(server.getServer().getSaveProperties().getGeneratorOptions());
        }
        return world.isClient() ? WorldProfileNetworking.clientProfile() : null;
    }

    public static boolean isUngravity(World world) {
        WorldProfile profile = get(world);
        return profile != null && profile.mode == Mode.UNGRAVITY && World.OVERWORLD.equals(world.getRegistryKey());
    }

    /** Legacy worlds retain their existing configuration and dimension behavior. */
    public static boolean allowsWorldSystems(World world) {
        if (world == null) return false;
        WorldProfile profile = get(world);
        return profile == null || profile.mode == Mode.UNGRAVITY && World.OVERWORLD.equals(world.getRegistryKey());
    }
}
