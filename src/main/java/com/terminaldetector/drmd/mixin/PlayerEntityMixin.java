package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.world.gravity.FootGravitySystem;
import com.terminaldetector.drmd.world.gravity.GravityMount;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
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

	/**
	 * While foot-gravity holds and the standing box fits, block the squeeze into
	 * swimming/crawling (~1-cube) that vanilla applies when AABBs clip the mount face.
	 */
	@Inject(method = "canChangeIntoPose", at = @At("HEAD"), cancellable = true)
	private void drmd$blockCrawlUnderFootGravity(EntityPose pose, CallbackInfoReturnable<Boolean> cir) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!FootGravitySystem.isActive(self.getUuid())) return;
		if (pose != EntityPose.SWIMMING) return;
		if (!GravityMount.hasStandingClearance(self)) return;
		cir.setReturnValue(false);
	}
}
