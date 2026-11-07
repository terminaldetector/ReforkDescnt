package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ProjectileEntity.class)
public class UfoProjectileHitProbe {
    @Inject(method="onEntityHit",at=@At("HEAD"),remap=false)
    private void hit(EntityHitResult hit,CallbackInfo ci){com.terminaldetector.drmd.gate.NativeUfoWeaponsCheck.hit((ProjectileEntity)(Object)this,hit);}
}
