package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.NativeMobTorchReloadCheck;
import com.terminaldetector.drmd.world.gravity.EntityGravitySystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityGravitySystem.class)
public abstract class MobTorchColdForceProbe {
    @Inject(method="applyMobPhysics",at=@At("HEAD"),remap=false)
    private static void drmd$coldForceStart(LivingEntity a,Vec3d up,float strength,boolean forceOnly,CallbackInfo ci){
        if(NativeMobTorchReloadCheck.ENABLED)NativeMobTorchReloadCheck.beforeForce(a,up,strength,forceOnly);
    }
    @Inject(method="applyMobPhysics",at=@At("RETURN"),remap=false)
    private static void drmd$coldForceEnd(LivingEntity a,Vec3d up,float strength,boolean forceOnly,CallbackInfo ci){
        if(NativeMobTorchReloadCheck.ENABLED)NativeMobTorchReloadCheck.afterForce(a,up,strength,forceOnly);
    }
}
