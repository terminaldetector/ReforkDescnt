package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.NativeViewmodelCheck;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Snapshot the actual generated warhead geometry at its production emission site. */
@Mixin(targets="com.terminaldetector.drmd.client.render.WeaponViewRenderer",remap=false)
public class ViewmodelBoxProbe {
    @Inject(method="drawBox",at=@At("HEAD"),remap=false)
    private static void box(MatrixStack matrices,VertexConsumerProvider consumers,int argb,int light,CallbackInfo ci){
        if(NativeViewmodelCheck.ENABLED)NativeViewmodelCheck.box(matrices,argb,light);
    }
}
