package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeHybridCavityCheck;
import com.terminaldetector.drmd.world.geometry.MicroWaterVolume;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Count clipped faces only while the genuine fluid handler is issuing this call. */
@Mixin(value=MicroWaterVolume.class,remap=false)
public class HybridWaterFacesProbe {
    @Inject(method="faces",at=@At("RETURN"),remap=false)
    private void faces(double height,MicroWaterVolume.Outside outside,CallbackInfoReturnable<List<MicroWaterVolume.Quad>> ci){NativeHybridCavityCheck.waterFaces(ci.getReturnValue().size());}
}
