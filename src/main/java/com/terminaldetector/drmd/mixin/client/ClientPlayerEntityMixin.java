package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.DescentClientState;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Creative double-tap space re-arms {@code abilities.flying} every few ticks. That path injects
 * ±flySpeed outside {@code travel()}, which shreds the 6DoF hull even when
 * {@link com.terminaldetector.drmd.mixin.client.PlayerFlightTravelMixin} cancels travel.
 *
 * <p>Clear flying at the start of {@code tickMovement} while Descent owns the pilot. {@code allowFlying}
 * stays set so H-off restores normal creative flight for building.
 */
@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
	@Inject(method = "tickMovement", at = @At("HEAD"))
	private void drmd$suppressCreativeFly(CallbackInfo ci) {
		ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
		if (!DescentClientState.enabled) return;
		if (self.getAbilities().flying) {
			self.getAbilities().flying = false;
		}
	}
}
