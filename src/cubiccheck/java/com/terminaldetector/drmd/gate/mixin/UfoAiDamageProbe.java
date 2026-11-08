package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.weapon.core.*;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(WeaponCore.class)
public class UfoAiDamageProbe {
    @Inject(method="directDamage",at=@At("HEAD"),remap=false)
    private static void before(Entity source,Entity target,float amount,DamageClass kind,CallbackInfo ci){com.terminaldetector.drmd.gate.NativeUfoAiCheck.beforeDamage(source,target);com.terminaldetector.drmd.gate.NativeTurretAiCheck.beforeDamage(source,target);}
    @Inject(method="directDamage",at=@At("RETURN"),remap=false)
    private static void after(Entity source,Entity target,float amount,DamageClass kind,CallbackInfo ci){com.terminaldetector.drmd.gate.NativeUfoAiCheck.afterDamage(source,target);com.terminaldetector.drmd.gate.NativeTurretAiCheck.afterDamage(source,target);}
}
