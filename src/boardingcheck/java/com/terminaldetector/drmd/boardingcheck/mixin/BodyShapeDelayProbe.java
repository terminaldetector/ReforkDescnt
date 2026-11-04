package com.terminaldetector.drmd.boardingcheck.mixin;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.gate.NativeBodyIndexCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Test-only delayed delivery of real tracked geometry; never substitutes a hull. */
@Mixin(BlockBodyEntity.class)
public abstract class BodyShapeDelayProbe {
 @Invoker(value="receiveShape",remap=false) protected abstract void replayShape(boolean baseline);
 @Inject(method="receiveShape",at=@At("HEAD"),cancellable=true,remap=false)
 private void delay(boolean baseline,CallbackInfo ci){if(NativeBodyIndexCheck.delayShape((BlockBodyEntity)(Object)this,()->replayShape(true)))ci.cancel();}
}
