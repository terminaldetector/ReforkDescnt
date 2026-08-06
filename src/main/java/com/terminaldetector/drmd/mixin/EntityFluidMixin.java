package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.atmosphere.AtmosphereRules;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In End / atmosphere-edge weightlessness, swimming/fluid push loses the gravity sink.
 */
@Mixin(Entity.class)
public abstract class EntityFluidMixin {
	@Inject(method = "updateMovementInFluid", at = @At("RETURN"))
	private void drmd$weightlessFluidPush(TagKey<Fluid> tag, double speed,
										  CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		World world = self.getWorld();
		if (!AtmosphereRules.isWeightlessFluid(world, self.getY())) return;
		if (!cir.getReturnValueZ()) return;
		Vec3d v = self.getVelocity();
		// Kill downward preference; keep gentle horizontal drift of the blob.
		self.setVelocity(v.x * 0.98, v.y * 0.98 + 0.01, v.z * 0.98);
	}
}
