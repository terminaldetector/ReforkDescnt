package com.terminaldetector.drmd.shootcheck;

import com.terminaldetector.drmd.AuditRenderCheck;
import com.terminaldetector.drmd.client.planet.HorizonVertexBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=HorizonVertexBuffer.class,remap=false)
public class AuditHorizonProbe {
    @Inject(method="draw",at=@At("HEAD"),remap=false)
    private static void auditDraw(Matrix4f matrix,CallbackInfo ci) {
        AuditRenderCheck.horizonDrawn();com.terminaldetector.drmd.HorizonAnchorCheck.drawn(matrix);
    }
}
