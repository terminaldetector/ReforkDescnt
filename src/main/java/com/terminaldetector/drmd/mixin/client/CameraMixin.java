package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.DescentCamera;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After vanilla Camera.update, apply Descent CalcView placement
 * (CamLag / micro-motion / vib / ship-relative third person).
 */
@Mixin(Camera.class)
public class CameraMixin {
	@Inject(method = "update", at = @At("RETURN"))
	private void drmd$descentCalcView(BlockView area, Entity focusedEntity, boolean thirdPerson,
									  boolean inverseView, float tickDelta, CallbackInfo ci) {
		if (!DescentClientState.enabled || !DescentClientState.attitudeValid) return;
		Camera self = (Camera) (Object) this;
		CameraAccessor acc = (CameraAccessor) self;
		DescentCamera.apply(self, new DescentCamera.CameraAccess() {
			@Override
			public void drmd$setPos(Vec3d pos) {
				acc.drmd$invokeSetPos(pos);
			}

			@Override
			public void drmd$setRotation(float yaw, float pitch) {
				acc.drmd$invokeSetRotation(yaw, pitch);
			}
		}, thirdPerson, inverseView, tickDelta);
	}
}
