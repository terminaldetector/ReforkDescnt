package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.compat.NativeDeferredFluids;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowableFluid.class)
public abstract class NativeDeferredFluidMixin {
    @Inject(method="onScheduledTick",at=@At("RETURN"))
    private void drmd$retainUnknownFluidBoundary(World world,BlockPos pos,FluidState state,CallbackInfo ci) {
        NativeDeferredFluids.observed(world,pos,state);
    }
}
