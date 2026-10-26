package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.DescentPlayerData;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-side twin of {@code PlayerFlightTravelMixin}: while 6DoF owns the pilot, cancel vanilla
 * {@code PlayerEntity.travel} (creative Y×0.6, air drag, gravity) and step the hull from the
 * integrator velocity already stored on {@link DescentPlayerData}.
 *
 * <p>{@link com.terminaldetector.drmd.flight.FlightSystem#tick} refreshes that velocity at
 * {@code END_SERVER_TICK}; this mixin applies the previous tick's result during entity movement.
 */
@Mixin(PlayerEntity.class)
public class ServerPlayerFlightTravelMixin {
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void drmd$serverDescentTravel(Vec3d movementInput, CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (self.getWorld().isClient) return;
		if (!(self instanceof ServerPlayerEntity sp)) return;
		if (sp.isSpectator() || sp.hasVehicle()) return;

		DescentPlayerData data = DescentPlayerData.get(sp);
		if (!data.isEnabled()) return;

		if (sp.getAbilities().flying) {
			sp.getAbilities().flying = false;
			sp.sendAbilitiesUpdate();
		}

		Vec3d perTick = data.getFlightVelocity().multiply(1.0 / DescentMod.TICKS_PER_SECOND);
		sp.setVelocity(perTick);
		sp.move(MovementType.SELF, perTick);
		if (sp.horizontalCollision || sp.verticalCollision) {
			Vec3d after = sp.getVelocity().multiply(0.86);
			sp.setVelocity(after);
			data.setFlightVelocity(after.multiply(DescentMod.TICKS_PER_SECOND));
		}
		sp.fallDistance = 0f;
		ci.cancel();
	}
}
