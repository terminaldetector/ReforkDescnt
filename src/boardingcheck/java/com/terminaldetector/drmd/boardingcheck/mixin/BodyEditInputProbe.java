package com.terminaldetector.drmd.boardingcheck.mixin;
import com.terminaldetector.drmd.client.flight.BodyEditClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BodyEditClient.class)
public class BodyEditInputProbe {
 @Inject(method="mine",at=@At("RETURN"),remap=false)
 private static void mine(MinecraftClient c,boolean held,CallbackInfoReturnable<Boolean> ci){if(held&&Boolean.getBoolean("drmd.gate.bodyEdit"))com.terminaldetector.drmd.gate.NativeBodyEditCheck.input(false,ci.getReturnValue());}
 @Inject(method="place",at=@At("RETURN"),remap=false)
 private static void place(MinecraftClient c,CallbackInfoReturnable<Boolean> ci){if(Boolean.getBoolean("drmd.gate.bodyEdit"))com.terminaldetector.drmd.gate.NativeBodyEditCheck.input(true,ci.getReturnValue());}
}
