package com.terminaldetector.drmd.shootcheck;

import com.terminaldetector.drmd.AuditRenderCheck;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screenshots after the complete world+GUI frame, not from a tick or before modal rendering. */
@Mixin(GameRenderer.class)
public class AuditFrameProbe {
    @Inject(method="render",at=@At("RETURN"))
    private void auditFrame(RenderTickCounter counter,boolean tick,CallbackInfo ci) {com.terminaldetector.drmd.AuditSwarmProfile.frameFinished();AuditRenderCheck.rendered();com.terminaldetector.drmd.RepairVisualCheck.rendered();}
}
