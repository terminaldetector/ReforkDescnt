package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.DescentClientState;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
