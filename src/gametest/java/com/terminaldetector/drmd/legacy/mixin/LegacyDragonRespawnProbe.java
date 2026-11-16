package com.terminaldetector.drmd.legacy.mixin;

import com.terminaldetector.drmd.legacy.LegacyRetirementObservation;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reaches a real vanilla field read, rather than a synthetic cancelled RETURN. */
@Mixin(value=EnderDragonFight.class,priority=900)
public abstract class LegacyDragonRespawnProbe {
    @Inject(method="respawnDragon()V",at=@At(value="FIELD",target="Lnet/minecraft/entity/boss/dragon/EnderDragonFight;dragonKilled:Z",opcode=Opcodes.GETFIELD))
    private void vanillaBody(CallbackInfo ci){
        if(LegacyRetirementObservation.ENABLED)LegacyRetirementObservation.vanillaRespawnEntries++;
    }
}
