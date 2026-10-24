package com.terminaldetector.drmd.shootcheck;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The inverted vanilla crosshair must not replace the centre projectile's measured pixels. */
@Mixin(value = InGameHud.class, remap = false)
public class WeaponCrosshairMask {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, remap = false)
    private void noCrosshair(DrawContext context, RenderTickCounter counter, CallbackInfo ci) {
        ci.cancel();
    }
}
