package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeTurretReloadCheck;
import com.terminaldetector.drmd.world.contraption.BodyJointRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BodyJointRegistry.class)
public class TurretRegistryPersistenceProbe {
    @Inject(method="writeNbt",at=@At("RETURN"))
    private void saved(NbtCompound n,RegistryWrapper.WrapperLookup lookup,CallbackInfoReturnable<NbtCompound> ci){NativeTurretReloadCheck.savedRegistry(ci.getReturnValue());}
}
