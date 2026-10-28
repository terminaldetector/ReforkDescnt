package com.terminaldetector.drmd.mixin.client;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Keep nested views out of the outer frame's shared Fabulous transparency targets. */
@Mixin(MinecraftClient.class)
public class NestedGraphicsMixin {
 @Inject(method="isFabulousGraphicsOrBetter",at=@At("HEAD"),cancellable=true)
 private static void nested(CallbackInfoReturnable<Boolean> ci){if(com.terminaldetector.drmd.client.portal.OffscreenWorldView.busy())ci.setReturnValue(false);}
}
