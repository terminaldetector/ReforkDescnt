package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.NativeUfoEngineCheck;
import com.terminaldetector.drmd.world.mega.UfoControlSystem;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=UfoControlSystem.class,remap=false)
public class UfoControlInputProbe {
    @Inject(method="receive",at=@At("RETURN"),remap=false)
    private static void accepted(ServerPlayerEntity player,UfoControlSystem.Input input,CallbackInfoReturnable<Boolean> result){
        if(result.getReturnValueZ())NativeUfoEngineCheck.accepted(player,input);
    }
}
