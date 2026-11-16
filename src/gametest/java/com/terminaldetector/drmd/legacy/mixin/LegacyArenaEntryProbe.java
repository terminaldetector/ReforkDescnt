package com.terminaldetector.drmd.legacy.mixin;

import com.terminaldetector.drmd.legacy.LegacyRetirementObservation;
import com.terminaldetector.drmd.world.end.EndReactorSession;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes the forbidden generator boundary; does not edit the content generator. */
@Mixin(value=EndReactorSession.class,remap=false)
public abstract class LegacyArenaEntryProbe {
    @Inject(method="generateBase",at=@At("HEAD"),remap=false)
    private static void observed(ServerWorld world,BlockPos center,CallbackInfo ci){
        if(LegacyRetirementObservation.ENABLED)LegacyRetirementObservation.forbiddenArena();
    }
}
