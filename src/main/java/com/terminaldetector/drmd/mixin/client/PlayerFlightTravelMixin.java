package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.flight.DescentFlightMotion;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the local pilot's movement to the 6DoF integrator.
 *
 * <p>Injected on {@code PlayerEntity.travel} rather than {@code LivingEntity.travel} on purpose:
 * the creative-flight branch that rewrites Y lives in the {@code PlayerEntity} override and runs
 * around the {@code super} call, so cancelling the superclass method would leave that clobber in
 * place.
 */
@Mixin(PlayerEntity.class)
public class PlayerFlightTravelMixin {
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void drmd$descentTravel(Vec3d movementInput, CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!self.getWorld().isClient) return;
		if (!DescentFlightMotion.drives(self)) return;
		DescentFlightMotion.travel(self);
		ci.cancel();
	}
}
