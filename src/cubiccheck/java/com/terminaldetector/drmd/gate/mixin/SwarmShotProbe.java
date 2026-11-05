package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.gate.NativeSwarmBaselineCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(WeaponCore.class)
public class SwarmShotProbe {
    @Inject(method="fireProjectile",at=@At("RETURN"),remap=false)
    private static void shot(WeaponCore.FireConfig cfg,CallbackInfoReturnable<ProjectileEntity> ci){if(com.terminaldetector.drmd.gate.NativeFlightEnemyCheck.ENABLED)com.terminaldetector.drmd.gate.NativeFlightEnemyCheck.shot(cfg,ci.getReturnValue());else if(com.terminaldetector.drmd.gate.NativeDroneStateCheck.READ)com.terminaldetector.drmd.gate.NativeDroneStateCheck.shot(cfg,ci.getReturnValue());else if(Boolean.getBoolean("drmd.gate.swarm"))NativeSwarmBaselineCheck.shot(cfg,ci.getReturnValue());}
}
