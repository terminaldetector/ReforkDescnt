package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.pickup.BlastBlockDrops;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlastBlockDropsMixin {
    @Inject(method = "dropStack(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/item/ItemStack;)V",
        at = @At("HEAD"), cancellable = true)
    private static void drmd$collectBlastDrop(World world, BlockPos pos, ItemStack stack, CallbackInfo callback) {
        if (BlastBlockDrops.capture(world, pos, stack)) callback.cancel();
    }
}
