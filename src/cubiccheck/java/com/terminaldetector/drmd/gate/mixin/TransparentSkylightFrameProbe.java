package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeTransparentSkylightCheck;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public class TransparentSkylightFrameProbe {
    @Inject(method="render",at=@At("RETURN"))
    private void completed(RenderTickCounter counter,boolean tick,CallbackInfo ci){if(NativeTransparentSkylightCheck.ENABLED){
        var renderer=net.minecraft.client.MinecraftClient.getInstance().gameRenderer;
        NativeTransparentSkylightCheck.rendered(((TransparentSkylightProjectionAccess)renderer).drmd$skylightFov(renderer.getCamera(),counter.getTickDelta(false),true));
    }}
}
