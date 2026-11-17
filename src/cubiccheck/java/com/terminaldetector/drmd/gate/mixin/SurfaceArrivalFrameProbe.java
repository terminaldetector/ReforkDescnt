package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeSurfaceArrivalCheck;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public abstract class SurfaceArrivalFrameProbe {
    @Inject(method="render",at=@At("RETURN"))
    private void arrival(RenderTickCounter counter,boolean tick,CallbackInfo ci){if(NativeSurfaceArrivalCheck.ENABLED)NativeSurfaceArrivalCheck.rendered();}
}
