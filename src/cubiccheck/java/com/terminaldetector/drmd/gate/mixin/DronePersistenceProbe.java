package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.gate.NativeDroneStateCheck;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(DroneEntity.class)
public class DronePersistenceProbe {
    @Inject(method="writeCustomDataToNbt",at=@At("RETURN"))
    private void saved(NbtCompound n,CallbackInfo ci){NativeDroneStateCheck.saved((DroneEntity)(Object)this,n);com.terminaldetector.drmd.gate.NativeFlightEnemyCheck.saved((DroneEntity)(Object)this,n);}
    @Inject(method="readCustomDataFromNbt",at=@At("RETURN"))
    private void loaded(NbtCompound n,CallbackInfo ci){NativeDroneStateCheck.loaded((DroneEntity)(Object)this);com.terminaldetector.drmd.gate.NativeFlightEnemyCheck.loaded((DroneEntity)(Object)this);}
}
