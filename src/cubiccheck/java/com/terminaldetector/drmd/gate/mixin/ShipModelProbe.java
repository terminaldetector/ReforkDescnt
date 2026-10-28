package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.client.render.ModelOrientation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ModelOrientation.class)
public class ShipModelProbe {
 @Inject(method="applyBasis",at=@At("HEAD"),remap=false)
 private static void model(MatrixStack m,float yaw,Vec3d forward,Vec3d up,CallbackInfo ci){if(Boolean.getBoolean("drmd.gate.shipGravity"))com.terminaldetector.drmd.gate.NativeShipGravityCheck.model(up);}
}
