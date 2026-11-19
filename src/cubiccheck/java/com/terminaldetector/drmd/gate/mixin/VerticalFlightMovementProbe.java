package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.NativeVerticalFlightCheck;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client-environment only: observes both integrated server and local-client real movement. */
@Mixin(Entity.class)
public abstract class VerticalFlightMovementProbe {
    @Inject(method="move",at=@At("HEAD"))
    private void before(MovementType type,Vec3d movement,CallbackInfo ci){if(NativeVerticalFlightCheck.ENABLED)NativeVerticalFlightCheck.beforeMove((Entity)(Object)this,type,movement);if(com.terminaldetector.drmd.gate.NativeOrbitalRouteCheck.ENABLED)com.terminaldetector.drmd.gate.NativeOrbitalRouteCheck.beforeMove((Entity)(Object)this,type,movement);}
    @Inject(method="move",at=@At("RETURN"))
    private void after(MovementType type,Vec3d movement,CallbackInfo ci){if(NativeVerticalFlightCheck.ENABLED)NativeVerticalFlightCheck.afterMove((Entity)(Object)this);if(com.terminaldetector.drmd.gate.NativeOrbitalRouteCheck.ENABLED)com.terminaldetector.drmd.gate.NativeOrbitalRouteCheck.afterMove((Entity)(Object)this);}
}
