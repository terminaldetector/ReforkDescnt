package com.terminaldetector.drmd.client.planet;

import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.planet.PlanetMap;
import com.terminaldetector.drmd.world.store.SurfaceSection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.lang.ref.WeakReference;

/** Preserves a depth-tested ground backdrop through native terrain render handoff. */
final class GroundHorizonCoverage {
    private static final int[] FOOTPRINT = {-24, 0, 24};
    private static final double MAX_BRIDGE_OPACITY = .68;
    private static final double MIN_SKY_FILL_OPACITY = .24;
    private static final double BRIDGE_START = 16;
    private static final double BRIDGE_END = 112;
    private static final double COVERAGE_RESPONSE_SECONDS = .24;

    private WeakReference<World> observedWorld = new WeakReference<>(null);
    private long sampledTick = Long.MIN_VALUE;
    private long lastFrame;
    private double coverage;
    private double observedCoverage;

    float opacity(MinecraftClient client, Vec3d eye, long seed, float altitudeOpacity, double clip) {
        var world = client.world;
        if (observedWorld.get() != world) {
            observedWorld = new WeakReference<>(world);
            sampledTick = Long.MIN_VALUE;
            lastFrame = 0;
        }
        if (altitudeOpacity >= 1) return 1;
        double ground = groundHeight(seed, eye.x, eye.z);
        double drop = Math.max(0, eye.y - ground);
        if (drop <= BRIDGE_START) return altitudeOpacity;

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
        return bridgeOpacity(altitudeOpacity, drop, coverage * inProjection);
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

    static float bridgeOpacity(float altitudeOpacity, double drop, double estimatedCoverage) {
        double bridge = smoothstep(drop, BRIDGE_START, BRIDGE_END);
        // A FULL cube can lose its rendered mesh as the eye climbs. Reserve some backdrop even
        // when residency is complete. The existing Horizon depth test protects native terrain.
        double opacity = MIN_SKY_FILL_OPACITY + (1 - estimatedCoverage) * (MAX_BRIDGE_OPACITY - MIN_SKY_FILL_OPACITY);
        return (float) (altitudeOpacity + (1 - altitudeOpacity) * bridge * opacity);
    }

    private static double smoothstep(double x, double from, double to) {
        double t = Math.max(0, Math.min(1, (x - from) / (to - from)));
        return t * t * (3 - 2 * t);
    }
}
