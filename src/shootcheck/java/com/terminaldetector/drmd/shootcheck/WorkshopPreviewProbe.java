package com.terminaldetector.drmd.shootcheck;

import com.terminaldetector.drmd.HybridWeaponCheck;
import com.terminaldetector.drmd.workshop.WorkshopScreen;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=WorkshopScreen.class,remap=false)
public class WorkshopPreviewProbe {
    @Inject(method="render",at=@At("RETURN"),remap=false)
    private void observe(DrawContext context,int x,int y,float delta,CallbackInfo ci) {
        context.draw(); HybridWeaponCheck.guiRendered();
    }
}
