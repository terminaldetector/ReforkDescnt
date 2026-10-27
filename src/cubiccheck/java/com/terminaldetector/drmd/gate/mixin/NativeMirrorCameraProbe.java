package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeMirrorCheck;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Camera.class,priority=2000)
public abstract class NativeMirrorCameraProbe {
    @Inject(method="update",at=@At("TAIL"))
    private void camera(CallbackInfo ci){
        if(Boolean.getBoolean("drmd.gate.contacts"))com.terminaldetector.drmd.gate.NativeBodyCollisionCheck.camera((Camera)(Object)this);
        else if(Boolean.getBoolean("drmd.gate.gun"))com.terminaldetector.drmd.gate.NativePortalGunCheck.camera((Camera)(Object)this);
        else if(Boolean.getBoolean("drmd.gate.actors"))com.terminaldetector.drmd.gate.NativeActorFrameCheck.camera((Camera)(Object)this);
        else if(Boolean.getBoolean("drmd.gate.mirrors"))NativeMirrorCheck.overrideCamera((Camera)(Object)this);
    }
}
