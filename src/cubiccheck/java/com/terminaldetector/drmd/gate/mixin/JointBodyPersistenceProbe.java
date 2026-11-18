package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeJointReloadCheck;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BlockBodyEntity.class)
public class JointBodyPersistenceProbe {
    @Inject(method="crossPortal",at=@At("HEAD"),remap=false)
    private void portalBefore(com.terminaldetector.drmd.d6.D6PortalTransform transform,CallbackInfo ci){com.terminaldetector.drmd.gate.NativeConnectedPortalCheck.crossing((BlockBodyEntity)(Object)this,transform,false);}
    @Inject(method="crossPortal",at=@At("RETURN"),remap=false)
    private void portalAfter(com.terminaldetector.drmd.d6.D6PortalTransform transform,CallbackInfo ci){com.terminaldetector.drmd.gate.NativeConnectedPortalCheck.crossing((BlockBodyEntity)(Object)this,transform,true);}
    @Inject(method="writeCustomDataToNbt",at=@At("RETURN"))
    private void saved(NbtCompound n,CallbackInfo ci){com.terminaldetector.drmd.gate.NativeTurretReloadCheck.savedBody((BlockBodyEntity)(Object)this,n);NativeJointReloadCheck.saved((BlockBodyEntity)(Object)this,n);com.terminaldetector.drmd.gate.NativeIslandLoadCheck.saved((BlockBodyEntity)(Object)this,n);}
    @Inject(method="readCustomDataFromNbt",at=@At("RETURN"))
    private void loaded(NbtCompound n,CallbackInfo ci){com.terminaldetector.drmd.gate.NativeTurretReloadCheck.loadedBody((BlockBodyEntity)(Object)this,n);NativeJointReloadCheck.loaded((BlockBodyEntity)(Object)this,n);com.terminaldetector.drmd.gate.NativeIslandLoadCheck.loaded((BlockBodyEntity)(Object)this,n);}
}
