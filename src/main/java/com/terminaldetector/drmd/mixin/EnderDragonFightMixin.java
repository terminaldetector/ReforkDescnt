package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.end.EndReactorSession;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppress vanilla dragon loop — End fight is the mega-reactor base.
 */
@Mixin(EnderDragonFight.class)
public class EnderDragonFightMixin {
	@Shadow @Final private ServerWorld world;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void drmd$replaceDragonFight(CallbackInfo ci) {
		EndReactorSession.suppressDragons(world);
		EndReactorSession.ensureBase(world);
		ci.cancel();
	}

	@Inject(method = "respawnDragon", at = @At("HEAD"), cancellable = true)
	private void drmd$noRespawn(CallbackInfo ci) {
		ci.cancel();
	}
}
