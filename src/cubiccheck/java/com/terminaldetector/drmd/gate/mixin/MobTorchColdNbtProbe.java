package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.NativeMobTorchReloadCheck;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native storage boundaries only; inert unless the exact opt-in profile is armed. */
@Mixin(Entity.class)
public abstract class MobTorchColdNbtProbe {
    @Inject(method="writeNbt",at=@At("RETURN"))
    private void drmd$coldSaved(NbtCompound n,CallbackInfoReturnable<NbtCompound> ci){
        if(NativeMobTorchReloadCheck.WRITE)NativeMobTorchReloadCheck.saved((Entity)(Object)this,ci.getReturnValue());
    }
    @Inject(method="readNbt",at=@At("RETURN"))
    private void drmd$coldLoaded(NbtCompound n,CallbackInfo ci){
        if(NativeMobTorchReloadCheck.READ)NativeMobTorchReloadCheck.loaded((Entity)(Object)this,n);
    }
}
