package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.CubicClientGate;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public class CubicFrameProbe {
    @Inject(method="render",at=@At("RETURN"))
    private void completed(RenderTickCounter counter,boolean tick,CallbackInfo ci) {CubicClientGate.frameFinished();}
}
