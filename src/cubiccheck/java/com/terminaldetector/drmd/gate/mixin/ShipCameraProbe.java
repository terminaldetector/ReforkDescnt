package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.client.flight.BoardingCamera;
import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BoardingCamera.class)
public class ShipCameraProbe {
 @Inject(method="apply",at=@At("RETURN"),remap=false)
 private static void camera(Camera camera,CameraAccessor access,PlayerEntity player,boolean third,boolean front,float delta,CallbackInfo ci){if(Boolean.getBoolean("drmd.gate.shipGravity"))com.terminaldetector.drmd.gate.NativeShipGravityCheck.camera(camera,delta);}
}
