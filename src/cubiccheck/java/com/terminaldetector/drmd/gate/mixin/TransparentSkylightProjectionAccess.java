package com.terminaldetector.drmd.gate.mixin;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
/** Reads the actual render FOV; does not change camera, matrices or the world renderer. */
@Mixin(GameRenderer.class)
public interface TransparentSkylightProjectionAccess {
    @Invoker("getFov") double drmd$skylightFov(Camera camera,float tickDelta,boolean changingFov);
}
