package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeMirrorCheck;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Camera.class,priority=500)
public abstract class NativeMirrorCameraProbe {
    @Inject(method="update",at=@At("TAIL"))
    private void camera(CallbackInfo ci){if(Boolean.getBoolean("drmd.gate.mirrors"))NativeMirrorCheck.overrideCamera((Camera)(Object)this);}
}
