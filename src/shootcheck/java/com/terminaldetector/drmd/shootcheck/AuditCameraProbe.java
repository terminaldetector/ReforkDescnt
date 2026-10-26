package com.terminaldetector.drmd.shootcheck;

import com.terminaldetector.drmd.AuditRenderCheck;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render-only stress sweep; reported camera speed is not a production player speed claim. */
@Mixin(value=Camera.class,priority=2000)
public class AuditCameraProbe {
    @Inject(method="update",at=@At("RETURN"))
    private void auditCamera(BlockView area,Entity focused,boolean third,boolean inverse,float delta,CallbackInfo ci) {
        AuditRenderCheck.overrideCamera((Camera)(Object)this);
        com.terminaldetector.drmd.HorizonAnchorCheck.overrideCamera((Camera)(Object)this);
        com.terminaldetector.drmd.RepairVisualCheck.overrideCamera((Camera)(Object)this);
        com.terminaldetector.drmd.MirrorJoinCheck.overrideCamera((Camera)(Object)this);
    }
}
