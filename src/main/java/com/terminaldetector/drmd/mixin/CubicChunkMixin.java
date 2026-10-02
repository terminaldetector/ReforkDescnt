package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.cubic.CubicWorldSystem;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public abstract class CubicChunkMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void drmd$cubeChanged(BlockPos pos, BlockState state, boolean moved,
            CallbackInfoReturnable<BlockState> cir) {
        if (cir.getReturnValue() != null && ((WorldChunk) (Object) this).getWorld() instanceof ServerWorld world)
            CubicWorldSystem.changed(world, pos);
    }
}
