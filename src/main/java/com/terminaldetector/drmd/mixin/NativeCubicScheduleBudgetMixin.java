package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.compat.CubicScheduleBudget;
import com.terminaldetector.drmd.world.compat.CubicScheduleBudgetAccess;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Carry the native scheduler's own time allowance into its recursive dependency walk. */
@Pseudo
@Mixin(targets="dev.fallingcloud.bettercubicchunks.server.CubicServerLevel",remap=false)
public abstract class NativeCubicScheduleBudgetMixin implements CubicScheduleBudgetAccess {
    @Unique private final CubicScheduleBudget drmd$generationBudget=new CubicScheduleBudget();
    @Inject(method="schedule(JJ)V",at=@At("HEAD"),remap=false)
    private void drmd$beginGenerationPass(long tick,long allowanceNanos,CallbackInfo ci){drmd$generationBudget.begin(System.nanoTime(),allowanceNanos);}
    @Inject(method="schedule(JJ)V",at=@At("RETURN"),remap=false)
    private void drmd$endGenerationPass(long tick,long allowanceNanos,CallbackInfo ci){drmd$generationBudget.end(System.nanoTime());}
    @Inject(method="advance(JLdev/fallingcloud/bettercubicchunks/core/CubeStatus;I)Z",at=@At("HEAD"),cancellable=true,remap=false)
    private void drmd$boundRecursiveGeneration(long cube,@Coerce Object desiredStatus,int depth,CallbackInfoReturnable<Boolean> ci){
        if(!drmd$generationBudget.admit(System.nanoTime()))ci.setReturnValue(false);
    }
    @Override public CubicScheduleBudget.Snapshot drmd$generationBudgetSnapshot(){return drmd$generationBudget.snapshot();}
}
