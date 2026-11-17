package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativePs1ColorCheck;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The real low-resolution scene after its actual outline composite. No supplied texture. */
@Mixin(targets="com.terminaldetector.drmd.client.presentation.SceneTargets",remap=false)
public class Ps1ColorSourceProbe {
    @Inject(method="compositeOutlines",at=@At("RETURN"),remap=false)
    private void source(MinecraftClient client,CallbackInfo ci){if(NativePs1ColorCheck.ENABLED)NativePs1ColorCheck.source(this);}
}
