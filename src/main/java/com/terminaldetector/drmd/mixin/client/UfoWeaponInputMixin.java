package com.terminaldetector.drmd.mixin.client;
import com.terminaldetector.drmd.client.flight.UfoControlClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Ship controls own both mouse buttons until the pilot explicitly releases control. */
@Mixin(value=MinecraftClient.class,priority=2000)
public class UfoWeaponInputMixin {
    @Inject(method="doAttack",at=@At("HEAD"),cancellable=true)
    private void attack(CallbackInfoReturnable<Boolean> ci){if(UfoControlClient.active())ci.setReturnValue(false);}
    @Inject(method="handleBlockBreaking",at=@At("HEAD"),cancellable=true)
    private void breaking(boolean pressed,CallbackInfo ci){if(UfoControlClient.active())ci.cancel();}
    @Inject(method="doItemUse",at=@At("HEAD"),cancellable=true)
    private void use(CallbackInfo ci){if(UfoControlClient.active())ci.cancel();}
}
