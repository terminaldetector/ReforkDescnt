package com.terminaldetector.drmd.mixin.client;
import net.minecraft.client.MinecraftClient;
import com.terminaldetector.drmd.client.input.DroneCombatInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(MinecraftClient.class)
public class DroneCombatInputMixin {
 @Inject(method="doAttack",at=@At("HEAD"),cancellable=true)
 private void attack(CallbackInfoReturnable<Boolean> ci){if(DroneCombatInput.ownsMouse((MinecraftClient)(Object)this))ci.setReturnValue(false);}
 @Inject(method="handleBlockBreaking",at=@At("HEAD"),cancellable=true)
 private void breaking(boolean b,CallbackInfo ci){if(DroneCombatInput.ownsMouse((MinecraftClient)(Object)this))ci.cancel();}
 @Inject(method="doItemUse",at=@At("HEAD"),cancellable=true)
 private void use(CallbackInfo ci){if(DroneCombatInput.ownsMouse((MinecraftClient)(Object)this))ci.cancel();}
}
