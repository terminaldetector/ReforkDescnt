package com.terminaldetector.drmd.boardingcheck.mixin;
import net.minecraft.client.render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public class FrameProbe {
    @Inject(method="render",at=@At("RETURN"))
    private void frame(RenderTickCounter counter,boolean tick,CallbackInfo ci){if(Boolean.getBoolean("drmd.gate.contacts")){com.terminaldetector.drmd.gate.NativeBodyCollisionCheck.rendered();}else if(Boolean.getBoolean("drmd.gate.bodyIndex")){com.terminaldetector.drmd.gate.NativeBodyIndexCheck.rendered();}else if(Boolean.getBoolean("drmd.gate.bodyEdit")){com.terminaldetector.drmd.gate.NativeBodyEditCheck.rendered();}else if(Boolean.getBoolean("drmd.gate.shipGravity"))com.terminaldetector.drmd.gate.NativeShipGravityCheck.rendered();else com.terminaldetector.drmd.gate.NativeBoardingCheck.rendered();}
}
