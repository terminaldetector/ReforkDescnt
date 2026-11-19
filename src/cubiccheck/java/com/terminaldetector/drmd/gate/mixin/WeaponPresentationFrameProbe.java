package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeWeaponPresentationCheck;
import net.minecraft.client.render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public class WeaponPresentationFrameProbe {
    @Inject(method="render",at=@At("HEAD")) private void before(RenderTickCounter ticks,boolean tick,CallbackInfo ci){NativeWeaponPresentationCheck.beforeFrame();}
    @Inject(method="render",at=@At("RETURN")) private void after(RenderTickCounter ticks,boolean tick,CallbackInfo ci){NativeWeaponPresentationCheck.rendered();}
    @Inject(method="renderHand",at=@At("HEAD")) private void beforeHand(CallbackInfo ci){NativeWeaponPresentationCheck.beforeHand();}
    @Inject(method="renderHand",at=@At("RETURN")) private void afterHand(CallbackInfo ci){NativeWeaponPresentationCheck.afterHand();}
}
