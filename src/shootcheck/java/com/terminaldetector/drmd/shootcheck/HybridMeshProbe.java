package com.terminaldetector.drmd.shootcheck;

import com.terminaldetector.drmd.HybridWeaponCheck;
import com.terminaldetector.drmd.client.render.HybridWeaponRenderer;
import com.terminaldetector.drmd.workshop.ClusterModule;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=HybridWeaponRenderer.class,remap=false)
public class HybridMeshProbe {
    @Inject(method="draw",at=@At("HEAD"),remap=false)
    private static void observe(MatrixStack matrices, VertexConsumerProvider consumers, ClusterModule module,
                                float recoil,float load,int light,CallbackInfo ci) {
        HybridWeaponCheck.recordDraw(module,recoil);
    }
}
