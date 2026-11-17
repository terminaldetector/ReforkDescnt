package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeSurfaceArrivalCheck;
import com.terminaldetector.drmd.world.store.SurfaceIngest;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=SurfaceIngest.class,remap=false)
public abstract class SurfaceArrivalServerProbe {
    @Inject(method="onChunkLoad",at=@At("HEAD"),remap=false)
    private static void entering(ServerWorld world,WorldChunk chunk,CallbackInfo ci){if(NativeSurfaceArrivalCheck.ENABLED)NativeSurfaceArrivalCheck.entering(world,chunk);}
    @Inject(method="onChunkLoad",at=@At("RETURN"),remap=false)
    private static void returned(ServerWorld world,WorldChunk chunk,CallbackInfo ci){if(NativeSurfaceArrivalCheck.ENABLED)NativeSurfaceArrivalCheck.returned(world,chunk);}
    @Inject(method="colourAt",at=@At("HEAD"),remap=false)
    private static void selected(ServerWorld world,WorldChunk chunk,BlockPos pos,CallbackInfoReturnable<Integer> cir){if(NativeSurfaceArrivalCheck.ENABLED)NativeSurfaceArrivalCheck.selected(world,chunk,pos);}
    @Inject(method="colourAt",at=@At("RETURN"),remap=false)
    private static void colour(ServerWorld world,WorldChunk chunk,BlockPos pos,CallbackInfoReturnable<Integer> cir){if(NativeSurfaceArrivalCheck.ENABLED)NativeSurfaceArrivalCheck.returnedColour(world,chunk,cir.getReturnValue());}
}
