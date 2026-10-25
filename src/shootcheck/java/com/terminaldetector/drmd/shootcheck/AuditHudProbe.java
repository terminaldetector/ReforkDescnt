package com.terminaldetector.drmd.shootcheck;

import com.terminaldetector.drmd.AuditRenderCheck;
import com.terminaldetector.drmd.client.hud.DescentHud;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=DescentHud.class,remap=false)
public class AuditHudProbe {
    @Inject(method={"drawCanopyChrome","drawReticle","drawAttitudeLadder","drawInstrumentCluster"},at=@At("HEAD"),remap=false)
    private static void modalGuard(CallbackInfo ci) {
        if(AuditRenderCheck.active()&&MinecraftClient.getInstance().currentScreen!=null)
            throw new IllegalStateException("Flight HUD submitted panels beneath a modal screen");
    }
}
