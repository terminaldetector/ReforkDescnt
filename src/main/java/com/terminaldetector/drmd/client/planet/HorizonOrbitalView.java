package com.terminaldetector.drmd.client.planet;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;

/** Render-thread loaded metadata only, at most 432 authored AABB cubes; never tickets or blocks. */
public final class HorizonOrbitalView {
    private HorizonOrbitalView() {}
    public static float[] sample(MinecraftClient client,PlanetSurfaceMesh mesh,Vec3d eye) {
        if(client.world==null)return new float[HorizonOrbitalHandoff.MAX_MASKS];
        var access=WorldAccess.of(client.world);
        var loaded=new HashMap<HorizonOrbitalHandoff.Cube,Boolean>();
        return HorizonOrbitalHandoff.mask(mesh.orbitalKeys,PlanetClientState.INSTANCE.landmarksSnapshot(),
            eye.x,eye.y,eye.z,Math.max(1,client.options.getClampedViewDistance()),
            cube->loaded.computeIfAbsent(cube,c->access.isVolumeAvailable(new D6ChunkPos(c.x(),c.y(),c.z()))));
    }
}
