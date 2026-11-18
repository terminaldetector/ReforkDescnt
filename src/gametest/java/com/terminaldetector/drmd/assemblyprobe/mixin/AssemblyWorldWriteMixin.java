package com.terminaldetector.drmd.assemblyprobe.mixin;

import com.terminaldetector.drmd.assemblyprobe.AssemblyWriteProbe;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=World.class,remap=false)
public abstract class AssemblyWorldWriteMixin {
    @Inject(method="setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z",at=@At("HEAD"),cancellable=true)
    private void drmd$assemblyBefore(BlockPos pos,BlockState state,int flags,CallbackInfoReturnable<Boolean> ci){
        if(AssemblyWriteProbe.before((World)(Object)this,pos,state,flags))ci.setReturnValue(false);
    }
    @Inject(method="setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z",at=@At("RETURN"))
    private void drmd$assemblyAfter(BlockPos pos,BlockState state,int flags,CallbackInfoReturnable<Boolean> ci){
        AssemblyWriteProbe.after((World)(Object)this,pos,state,flags,ci.getReturnValue());
    }
}
