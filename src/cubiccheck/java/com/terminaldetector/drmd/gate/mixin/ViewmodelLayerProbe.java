package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.NativeViewmodelCheck;
import net.minecraft.client.render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe actual GPU draw after RenderLayer.startDrawing has installed shader/depth policy. */
@Mixin(RenderLayer.class)
public class ViewmodelLayerProbe {
    @Inject(method="draw",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/BufferRenderer;drawWithGlobalProgram(Lnet/minecraft/client/render/BuiltBuffer;)V",shift=At.Shift.BEFORE))
    private void before(BuiltBuffer buffer,CallbackInfo ci){if(NativeViewmodelCheck.ENABLED)NativeViewmodelCheck.beforeLayer((RenderLayer)(Object)this);}
    @Inject(method="draw",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/BufferRenderer;drawWithGlobalProgram(Lnet/minecraft/client/render/BuiltBuffer;)V",shift=At.Shift.AFTER))
    private void after(BuiltBuffer buffer,CallbackInfo ci){if(NativeViewmodelCheck.ENABLED)NativeViewmodelCheck.afterLayer((RenderLayer)(Object)this);}
}
