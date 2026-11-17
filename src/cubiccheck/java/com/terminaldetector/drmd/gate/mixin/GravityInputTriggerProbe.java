package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.GravityInputRouteTrace;
import com.terminaldetector.drmd.inventory.DroneArsenal;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=DroneArsenal.class,remap=false)
public class GravityInputTriggerProbe {
    @Inject(method="receive",at=@At("RETURN"),remap=false)
    private static void received(ServerPlayerEntity player,int mask,CallbackInfo ci){GravityInputRouteTrace.trigger(player,mask);}
}
