package com.terminaldetector.drmd.mixin.client;
import com.terminaldetector.drmd.client.flight.BodyEditClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=MinecraftClient.class,priority=900)
public class BodyEditInputMixin {
 @Inject(method="doAttack",at=@At("HEAD"),cancellable=true)
 private void attack(CallbackInfoReturnable<Boolean> ci){if(BodyEditClient.mine((MinecraftClient)(Object)this,true))ci.setReturnValue(false);}
 @Inject(method="handleBlockBreaking",at=@At("HEAD"),cancellable=true)
 private void hold(boolean pressed,CallbackInfo ci){if(BodyEditClient.mine((MinecraftClient)(Object)this,pressed))ci.cancel();}
 @Inject(method="doItemUse",at=@At("HEAD"),cancellable=true)
 private void use(CallbackInfo ci){if(BodyEditClient.place((MinecraftClient)(Object)this))ci.cancel();}
}
