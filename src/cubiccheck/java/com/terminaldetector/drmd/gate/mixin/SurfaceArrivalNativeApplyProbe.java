package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeSurfaceArrivalCheck;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="dev.fallingcloud.bettercubicchunks.server.CubeApplier",remap=false)
public abstract class SurfaceArrivalNativeApplyProbe {
    @Inject(method="applyLoaded",at=@At("HEAD"),remap=false)
    private void entering(Chunk chunk,int cubeY,@Coerce Object data,CallbackInfo ci){if(NativeSurfaceArrivalCheck.ENABLED)NativeSurfaceArrivalCheck.nativeApply(chunk,cubeY,true);}
    @Inject(method="applyLoaded",at=@At("RETURN"),remap=false)
    private void returned(Chunk chunk,int cubeY,@Coerce Object data,CallbackInfo ci){if(NativeSurfaceArrivalCheck.ENABLED)NativeSurfaceArrivalCheck.nativeApply(chunk,cubeY,false);}
}
