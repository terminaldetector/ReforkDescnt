package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.flight.FlightMotion;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
	/**
	 * Must cancel at {@link PlayerEntity#travel} — not only {@code LivingEntity.travel}.
	 * Creative mode calls {@code super.travel} then rewrites velocity from the pre-travel Y
	 * ({@code abilities.flying}), which zeroes 6DoF thrust and freezes the model.
	 */
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void drmd$sixDofOrSkipCreativeFly(Vec3d movementInput, CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (FlightMotion.applyTravel(self)) {
			ci.cancel();
		}
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
	private void drmd$write(NbtCompound nbt, CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		DescentPlayerData.get(self).writeNbt(nbt);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
	private void drmd$read(NbtCompound nbt, CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		DescentPlayerData.get(self).readNbt(nbt);
	}
}
