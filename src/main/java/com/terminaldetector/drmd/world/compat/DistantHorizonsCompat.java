package com.terminaldetector.drmd.world.compat;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Optional DH cooperation. The cubic fork's render band follows the eye vertically. */
public final class DistantHorizonsCompat {
    private static final String API = "com.seibel.distanthorizons.api.";
    private static final double BLEND_BLOCKS = 32;
    private static Boolean present;

    private DistantHorizonsCompat() {}

    public static boolean isPresent() {
        if (present == null) {
            var loader = FabricLoader.getInstance();
            present = loader.isModLoaded("distanthorizons") || loader.isModLoaded("distant_horizons");
        }
        return present;
    }

    /** Let a running cubic DH provider own distant terrain when it exposes real vertical coverage. */
    public static boolean hasCubicTerrain(World world) {
        if (!isPresent() || RendererLookup.renderer == null
                || CubicAvailability.backend(world) != CubicAvailability.Backend.NATIVE_CUBIC) return false;
        try {
            var renderer = RendererLookup.renderer;
            return renderer.enabled() && renderer.cubic != null && renderer.cubic.active(world)
                    && renderer.cubic.radius(world) > 0;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    /**
     * Multiplicative opacity of Ungravity's ground backdrop. Native cubic DH may own visible
     * volumes near the camera while the planet's lowest ground is outside its vertical band.
     */
    public static double horizonShare(World world, double cameraY, double groundY) {
        if (!isPresent() || RendererLookup.renderer == null) return 1;
        try {
            var renderer = RendererLookup.renderer;
            if (!renderer.enabled()) return 1;
            return switch (CubicAvailability.backend(world)) {
                case STOCK -> 0;
                case UNSUPPORTED -> 1;
                case NATIVE_CUBIC -> renderer.cubic == null || !renderer.cubic.active(world) ? 1
                    : uncoveredGround(cameraY, groundY, renderer.cubic.radius(world));
            };
        } catch (ReflectiveOperationException | LinkageError e) {
            return 1;
        }
    }

    static double uncoveredGround(double cameraY, double groundY, int verticalRadius) {
        if (verticalRadius <= 0 || !Double.isFinite(cameraY) || !Double.isFinite(groundY)) return 1;
        double fraction = Math.max(0, Math.min(1, (cameraY - verticalRadius - groundY) / BLEND_BLOCKS));
        return fraction * fraction * (3 - 2 * fraction);
    }

    public static void logStatus() {
        if (isPresent()) DescentMod.LOGGER.info("Distant Horizons detected. Far ground uses the active terrain coverage.");
    }

    private static final class RendererLookup {
        private static final Renderer renderer = find();

        private static Renderer find() {
            try {
                return new Renderer(Class.forName(API + "DhApi$Delayed").getField("configs"),
                    Class.forName(API + "interfaces.config.IDhApiConfig").getMethod("graphics"),
                    Class.forName(API + "interfaces.config.client.IDhApiGraphicsConfig").getMethod("renderingMode"),
                    Class.forName(API + "interfaces.config.IDhApiConfigValue").getMethod("getValue"),
                    CubicRenderer.find());
            } catch (ReflectiveOperationException | LinkageError e) {
                return null;
            }
        }
    }

    private record Renderer(Field configs, Method graphics, Method mode, Method value, CubicRenderer cubic) {
        boolean enabled() throws ReflectiveOperationException {
            Object config = configs.get(null);
            if (config == null) return false;
            Object currentMode = value.invoke(mode.invoke(graphics.invoke(config)));
            return currentMode instanceof Enum<?> setting && setting.name().equals("DEFAULT");
        }
    }

    private record CubicRenderer(Method predicate, Method viewDistance) {
        static CubicRenderer find() {
            try {
                Class<?> type = Class.forName(API + "methods.cubic.DhApiCubic");
                return new CubicRenderer(type.getMethod("isCubicLevel", Object.class),
                    type.getMethod("getVerticalViewDistanceInBlocks", Object.class));
            } catch (ReflectiveOperationException | LinkageError e) {
                return null;
            }
        }

        boolean active(World world) throws ReflectiveOperationException {
            return Boolean.TRUE.equals(predicate.invoke(null, world));
        }

        int radius(World world) throws ReflectiveOperationException {
            return (Integer) viewDistance.invoke(null, world);
        }
    }
}
