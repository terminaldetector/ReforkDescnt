package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeJointReloadCheck;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BlockBodyEntity.class)
public class JointBodyPersistenceProbe {
    @Inject(method="writeCustomDataToNbt",at=@At("RETURN"))
    private void saved(NbtCompound n,CallbackInfo ci){NativeJointReloadCheck.saved((BlockBodyEntity)(Object)this,n);}
    @Inject(method="readCustomDataFromNbt",at=@At("RETURN"))
    private void loaded(NbtCompound n,CallbackInfo ci){NativeJointReloadCheck.loaded((BlockBodyEntity)(Object)this,n);}
}
