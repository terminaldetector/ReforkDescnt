package com.terminaldetector.drmd.gate.mixin;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Test-only accessor: setters are used exclusively on a newly constructed comparator camera. */
@Mixin(Camera.class)
public interface CameraHeightCacheProbe {
    @Accessor("cameraY") float gate$cameraY();
    @Accessor("lastCameraY") float gate$lastCameraY();
    @Accessor("cameraY") void gate$cameraY(float value);
    @Accessor("lastCameraY") void gate$lastCameraY(float value);
}
