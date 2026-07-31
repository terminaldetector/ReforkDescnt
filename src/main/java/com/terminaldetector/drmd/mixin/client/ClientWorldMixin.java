package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.sky.LevelSky;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tint the sky by level band.
 *
 * <p>One hook covers both the sky dome and the fog: vanilla's background renderer takes its fog
 * colour from this same call in the overworld, so a colour blended here carries through to the
 * horizon haze without a second, more version-fragile injection into the renderer itself.
 *
 * <p>Scoped to the overworld because that is the column the level bands describe; other dimensions
 * keep their own sky.
 */
@Mixin(ClientWorld.class)
public class ClientWorldMixin {
	// require = 0 on purpose, and only here. The flight hooks are load-bearing — if one of those ever
	// stops matching, a crash is the right outcome, because silently skipping it reproduces exactly
	// the bug where the ship would not move. This one only tints a colour, so if a future mapping
	// moves the target it should degrade to a vanilla sky rather than take the game down with it.
	@Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true, require = 0)
	private void drmd$levelSky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
		ClientWorld self = (ClientWorld) (Object) this;
		if (self.getRegistryKey() != World.OVERWORLD) return;
		Vec3d vanilla = cir.getReturnValue();
		if (vanilla == null) return;
		Vec3d tinted = LevelSky.tint(vanilla, cameraPos.y);
		if (tinted != vanilla) cir.setReturnValue(tinted);
	}
}
