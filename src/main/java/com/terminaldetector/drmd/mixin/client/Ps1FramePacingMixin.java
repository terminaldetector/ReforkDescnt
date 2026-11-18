package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Limit the existing actual render-loop cap; never write vanilla FPS options or tick clocks. */
@Mixin(MinecraftClient.class)
public class Ps1FramePacingMixin {
    @Inject(method="getFramerateLimit",at=@At("RETURN"),cancellable=true)
    private void drmd$ps1FrameLimit(CallbackInfoReturnable<Integer> result){
        var client=(MinecraftClient)(Object)this;
        boolean mainWorld=client.world!=null&&client.currentScreen==null&&!client.gameRenderer.isRenderingPanorama();
        int original=result.getReturnValueI();
        int capped=DescentConfig.ps1Pacing.limit(original,DescentConfig.presentationProfile,mainWorld,OffscreenWorldView.busy());
        if(capped!=original)result.setReturnValue(capped);
    }
}
