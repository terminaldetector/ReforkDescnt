package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.atmosphere.AtmosphereRules;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * End / near-space edge: fluid has no preferred down — blobs hang in weightlessness.
 */
@Mixin(FlowableFluid.class)
public abstract class FlowableFluidMixin {
	@Inject(method = "getVelocity", at = @At("HEAD"), cancellable = true)
	private void drmd$weightlessVelocity(BlockView world, BlockPos pos, FluidState state,
										 CallbackInfoReturnable<Vec3d> cir) {
		World w = world instanceof World ww ? ww : null;
		if (AtmosphereRules.isWeightlessFluid(w, pos.getY())) {
			cir.setReturnValue(Vec3d.ZERO);
		}
	}
}
