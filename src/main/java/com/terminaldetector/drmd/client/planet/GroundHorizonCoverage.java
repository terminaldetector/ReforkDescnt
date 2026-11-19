package com.terminaldetector.drmd.client.planet;

import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.planet.PlanetMap;
import com.terminaldetector.drmd.world.store.SurfaceSection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Keeps the backdrop visible while the actual lower terrain streams into the current view. */
final class GroundHorizonCoverage {
    private static final int[] FOOTPRINT = {-24, 0, 24};
    private static final double MAX_BRIDGE_OPACITY = .68;
    private static final double COVERAGE_RESPONSE_SECONDS = .24;

    private World world;
    private long sampledTick = Long.MIN_VALUE;
    private long lastFrame;
    private double coverage;
    private double observedCoverage;

    float opacity(MinecraftClient client, Vec3d eye, long seed, float altitudeOpacity, double clip) {
        if (world != client.world) {
            world = client.world;
            sampledTick = Long.MIN_VALUE;
            lastFrame = 0;
        }
        if (altitudeOpacity >= 1) return 1;
        double ground = groundHeight(seed, eye.x, eye.z);
        double drop = Math.max(0, eye.y - ground);
        double bridge = smoothstep(drop, 16, 112);
        if (bridge <= 0) return altitudeOpacity;

        long tick = world.getTime();
        if (tick != sampledTick) {
            sampledTick = tick;
            observedCoverage = availableFootprint(client, eye, seed);
        }
        long now = System.nanoTime();
        if (lastFrame == 0 || now - lastFrame > 1_000_000_000L) {
            coverage = observedCoverage;
        } else {
            double dt = Math.min(.1, Math.max(0, (now - lastFrame) * 1e-9));
            double weight = 1 - Math.exp(-dt / COVERAGE_RESPONSE_SECONDS);
            coverage += (observedCoverage - coverage) * weight;
        }
        lastFrame = now;
        // A present column can still lie past the ordinary renderer's projection.
        double inProjection = 1 - smoothstep(drop, clip * .72, clip * .96);
        return combine(altitudeOpacity, bridge, coverage * inProjection);
    }

    private static double availableFootprint(MinecraftClient client, Vec3d eye, long seed) {
        if (client.worldRenderer.getCompletedChunkCount() == 0) return 0;
        var access = WorldAccess.of(client.world);
        double resident = 0;
        for (int dx : FOOTPRINT) for (int dz : FOOTPRINT) {
            double x = eye.x + dx, z = eye.z + dz;
            var surface = BlockPos.ofFloored(x, groundHeight(seed, x, z), z);
            if (access.readIfLoaded(surface).isPresent()) resident++;
        }
        return resident / (FOOTPRINT.length * FOOTPRINT.length);
    }

    private static double groundHeight(long seed, double x, double z) {
        short observed = PlanetClientState.INSTANCE.observedHeight(x, z, 0);
        if (observed != SurfaceSection.NO_HEIGHT) return observed;
        return Math.max(PlanetMap.SEA_LEVEL, PlanetMap.height(seed, x, z));
    }

    private static float combine(float altitudeOpacity, double bridge, double realCoverage) {
        double uncovered = bridge * (1 - realCoverage) * MAX_BRIDGE_OPACITY;
        return (float) (altitudeOpacity + (1 - altitudeOpacity) * uncovered);
    }

    private static double smoothstep(double x, double from, double to) {
        double t = Math.max(0, Math.min(1, (x - from) / (to - from)));
        return t * t * (3 - 2 * t);
    }
}
