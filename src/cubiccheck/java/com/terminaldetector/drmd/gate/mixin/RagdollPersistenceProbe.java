package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.entity.RagdollEntity;
import com.terminaldetector.drmd.gate.NativeDroneWreckCheck;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(RagdollEntity.class)
public class RagdollPersistenceProbe {
    @Inject(method="writeCustomDataToNbt",at=@At("RETURN"))
    private void drmd$actualSave(NbtCompound tag,CallbackInfo ci){NativeDroneWreckCheck.saved((RagdollEntity)(Object)this,tag);}
    @Inject(method="readCustomDataFromNbt",at=@At("RETURN"))
    private void drmd$actualRead(NbtCompound tag,CallbackInfo ci){NativeDroneWreckCheck.loaded((RagdollEntity)(Object)this);}
}
