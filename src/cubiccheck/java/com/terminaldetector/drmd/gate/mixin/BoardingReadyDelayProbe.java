package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeUfoReloadCheck;
import com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BoardingSystem.class)
public class BoardingReadyDelayProbe {
    @Inject(target=@Desc(value="ready",args={ServerPlayerEntity.class,int.class},ret=boolean.class),at=@At("HEAD"),cancellable=true,remap=false)
    private static void delay(ServerPlayerEntity p,int version,CallbackInfoReturnable<Boolean> ci){if(NativeUfoReloadCheck.delayReady(p,version))ci.setReturnValue(false);}
}
