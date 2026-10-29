package com.terminaldetector.drmd.boardingcheck.mixin;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Camera.class,priority=2000)
public class HullCameraProbe {
    @Inject(method="update",at=@At("TAIL"))
    private void camera(CallbackInfo ci){if(Boolean.getBoolean("drmd.gate.contacts"))com.terminaldetector.drmd.gate.NativeBodyCollisionCheck.camera((Camera)(Object)this);}
}
