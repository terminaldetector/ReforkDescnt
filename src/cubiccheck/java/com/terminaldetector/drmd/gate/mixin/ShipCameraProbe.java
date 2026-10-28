package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.client.gravity.FootGravityCamera;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FootGravityCamera.class)
public class ShipCameraProbe {
 @Inject(method="apply",at=@At("RETURN"),remap=false)
 private static void camera(MatrixStack m,float delta,CallbackInfo ci){if(Boolean.getBoolean("drmd.gate.shipGravity"))com.terminaldetector.drmd.gate.NativeShipGravityCheck.camera();}
}
