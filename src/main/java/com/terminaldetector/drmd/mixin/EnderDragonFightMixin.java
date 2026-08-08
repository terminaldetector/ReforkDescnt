package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.end.EndReactorSession;
import com.terminaldetector.drmd.world.level.WorldLevels;
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
		// Vanilla mode: real End, real dragon fight — let vanilla's own tick run untouched.
		if (!WorldLevels.isAdvancedColumn(world)) return;
		// Always kill stock dragons; arena raise is gated. Entire path must be exception-safe —
		// an uncaught IAE here (powered_rail curves) crashed the integrated server on join.
		try {
			EndReactorSession.onDragonFightTick(world);
		} catch (Throwable t) {
			com.terminaldetector.drmd.DescentMod.LOGGER.error("EnderDragonFight mixin tick failed", t);
		}
		ci.cancel();
	}

	@Inject(method = "respawnDragon", at = @At("HEAD"), cancellable = true)
	private void drmd$noRespawn(CallbackInfo ci) {
		if (!WorldLevels.isAdvancedColumn(world)) return;
		ci.cancel();
	}
}
