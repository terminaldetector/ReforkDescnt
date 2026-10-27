package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.gravity.EntityGravitySystem;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runtime acceleration override. Entity flags and serialized NBT retain their original owner. */
@Mixin(Entity.class)
public class EntityFieldGravityMixin {
    @Inject(method="getFinalGravity",at=@At("HEAD"),cancellable=true)
    private void drmd$fieldAcceleration(CallbackInfoReturnable<Double> cir) {
        if(EntityGravitySystem.suppressesVanillaGravity((Entity)(Object)this))cir.setReturnValue(0.0);
    }
}
