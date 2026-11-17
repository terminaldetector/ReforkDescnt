package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeSurfaceArrivalCheck;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(WorldChunk.class)
public abstract class SurfaceArrivalMutationProbe {
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void changed(BlockPos pos,BlockState state,boolean moved,CallbackInfoReturnable<BlockState> cir){if(NativeSurfaceArrivalCheck.ENABLED)NativeSurfaceArrivalCheck.blockChanged((WorldChunk)(Object)this,pos,cir.getReturnValue(),state);}
}
