package com.terminaldetector.drmd.boardingcheck;
import net.minecraft.client.render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public class FrameProbe {
    @Inject(method="render",at=@At("RETURN"))
    private void frame(RenderTickCounter counter,boolean tick,CallbackInfo ci){com.terminaldetector.drmd.gate.NativeBoardingCheck.rendered();}
}
