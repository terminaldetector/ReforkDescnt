package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeHybridCavityCheck;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Only observes the real registered FluidRenderHandler's normal begin/complete path. */
@Mixin(targets="com.terminaldetector.drmd.client.render.terrain.CarvedWaterRendering$Handler",remap=false)
public class HybridFluidHandlerProbe {
    @Inject(method="renderFluid",at=@At("HEAD"),remap=false)
    private void begin(BlockPos pos,BlockRenderView world,VertexConsumer vertices,BlockState state,FluidState fluid,CallbackInfo ci){NativeHybridCavityCheck.waterBegin(pos,state,fluid);}
    @Inject(method="renderFluid",at=@At("RETURN"),remap=false)
    private void complete(CallbackInfo ci){NativeHybridCavityCheck.waterEnd();}
}
