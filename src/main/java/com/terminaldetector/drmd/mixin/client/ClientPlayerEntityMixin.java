package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.world.gravity.FootGravitySystem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Creative double-tap space re-arms {@code abilities.flying} mid-{@code tickMovement} and injects
 * ±flySpeed <em>before</em> {@code travel()}. Clearing only at HEAD left a window where flying came
 * back, abilities packets fought the server, and any tick {@code DescentFlightMotion.drives} was
 * false shredded the hull.
 *
 * <p>While Descent owns the pilot: force {@code flying=false} and {@code allowFlying=false} at HEAD
 * and again at RETURN so the double-tap toggle cannot stick. {@link
 * com.terminaldetector.drmd.flight.FlightSystem#disable} restores creative {@code allowFlying}.
 */
@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
	@Inject(method = "tickMovement", at = @At("HEAD"))
	private void drmd$suppressCreativeFlyHead(CallbackInfo ci) {
		lockOutVanillaFly();
	}

	@Inject(method = "tickMovement", at = @At("RETURN"))
	private void drmd$suppressCreativeFlyTail(CallbackInfo ci) {
		lockOutVanillaFly();
	}

	@Inject(method = "wouldCollideAt", at = @At("RETURN"), cancellable = true)
	private void drmd$ignoreFalseEscapeFromLocalSupport(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) return;
		ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
		if (!FootGravitySystem.isActive(self) || FootGravitySystem.getUp(self).y > 0.999999) return;
		// Vanilla searches whole world-Y columns around the feet. A wall can fill one of
		// those columns while the local-gravity body is entirely clear on the other side.
		var body = self.getBoundingBox().contract(1.0e-7);
		if (!self.getWorld().getBlockCollisions(self, body).iterator().hasNext()) cir.setReturnValue(false);
	}

	private void lockOutVanillaFly() {
		if (!DescentClientState.enabled) return;
		ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
		var ab = self.getAbilities();
		boolean dirty = false;
		if (ab.flying) {
			ab.flying = false;
			dirty = true;
		}
		// Blocks the mid-method double-tap that sets flying=true when allowFlying is set.
		if (ab.allowFlying) {
			ab.allowFlying = false;
			dirty = true;
		}
		if (dirty) {
			self.sendAbilitiesUpdate();
		}
	}
}
