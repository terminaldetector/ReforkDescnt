package com.terminaldetector.drmd.mixin.client;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
	@Invoker("setPos")
	void drmd$invokeSetPos(Vec3d pos);

	@Invoker("setRotation")
	void drmd$invokeSetRotation(float yaw, float pitch);
}
