package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.NativeHudReadabilityCheck;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DrawContext.class)
abstract class HudReadabilityDrawProbe {
    @Inject(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I", at = @At("HEAD"))
    private void hudText(TextRenderer renderer, Text text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> ci) {
        NativeHudReadabilityCheck.observeText(text);
    }

    @Inject(method = "drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V", at = @At("HEAD"))
    private void centeredLabel(TextRenderer renderer, Text text, int x, int y, int color, CallbackInfo ci) {
        NativeHudReadabilityCheck.observeLabel((DrawContext)(Object)this, renderer, text, x, y);
    }

    @Inject(method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;II)V", at = @At("HEAD"))
    private void tooltip(TextRenderer renderer, Text text, int x, int y, CallbackInfo ci) {
        NativeHudReadabilityCheck.observeTooltip(text, x, y);
    }
}
