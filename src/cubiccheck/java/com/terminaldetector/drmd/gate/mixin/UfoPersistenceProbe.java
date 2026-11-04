package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeUfoReloadCheck;
import com.terminaldetector.drmd.world.mega.SkyUfoEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(SkyUfoEntity.class)
public class UfoPersistenceProbe {
    @Inject(method="writeCustomDataToNbt",at=@At("RETURN"))
    private void saved(NbtCompound n,CallbackInfo ci){NativeUfoReloadCheck.saved((SkyUfoEntity)(Object)this,n);}
    @Inject(method="readCustomDataFromNbt",at=@At("RETURN"))
    private void loaded(NbtCompound n,CallbackInfo ci){NativeUfoReloadCheck.loaded((SkyUfoEntity)(Object)this,n);}
}
