package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.NativeBenchmarkRecorder;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.BooleanSupplier;

/** Client-environment mixin: observes only the integrated server, never a dedicated process. */
@Mixin(MinecraftServer.class)
public class BenchmarkServerTickProbe {
    @Unique private long drmd$benchmarkStarted;
    @Unique private boolean drmd$benchmarkOpen;
    @Inject(method="tick(Ljava/util/function/BooleanSupplier;)V",at=@At("HEAD"))
    private void started(BooleanSupplier keepTicking,CallbackInfo ci){
        if(NativeBenchmarkRecorder.ENABLED){drmd$benchmarkStarted=System.nanoTime();drmd$benchmarkOpen=true;}
    }
    @Inject(method="tick(Ljava/util/function/BooleanSupplier;)V",at=@At("RETURN"))
    private void finished(BooleanSupplier keepTicking,CallbackInfo ci){
        if(NativeBenchmarkRecorder.ENABLED&&drmd$benchmarkOpen){
            drmd$benchmarkOpen=false;
            NativeBenchmarkRecorder.serverTickFinished((MinecraftServer)(Object)this,drmd$benchmarkStarted,System.nanoTime());
        }
    }
}
