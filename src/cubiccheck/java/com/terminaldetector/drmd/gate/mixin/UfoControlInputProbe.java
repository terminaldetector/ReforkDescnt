package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.NativeUfoEngineCheck;
import com.terminaldetector.drmd.world.mega.UfoControlSystem;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=UfoControlSystem.class,remap=false)
public class UfoControlInputProbe {
    @Inject(method="drive",at=@At("HEAD"),remap=false)
    private static void begin(com.terminaldetector.drmd.world.mega.SkyUfoEntity body,CallbackInfoReturnable<Boolean> result){NativeUfoEngineCheck.beginDrive(body);}
    @Inject(method="drive",at=@At("RETURN"),remap=false)
    private static void end(com.terminaldetector.drmd.world.mega.SkyUfoEntity body,CallbackInfoReturnable<Boolean> result){NativeUfoEngineCheck.endDrive(body,result.getReturnValueZ());}
    @Inject(method="receive",at=@At("RETURN"),remap=false)
    private static void accepted(ServerPlayerEntity player,UfoControlSystem.Input input,CallbackInfoReturnable<Boolean> result){
        if(result.getReturnValueZ())NativeUfoEngineCheck.accepted(player,input);
    }
    @Inject(method="receiveFire",at=@At("RETURN"),remap=false)
    private static void fire(ServerPlayerEntity player,UfoControlSystem.Fire input,CallbackInfoReturnable<Boolean> result){if(result.getReturnValueZ()){com.terminaldetector.drmd.gate.NativeTurretReloadCheck.acceptedFire(player,input);com.terminaldetector.drmd.gate.NativeUfoTurretCheck.trigger(player,input);com.terminaldetector.drmd.gate.NativeUfoWeaponsCheck.accepted(player,input);}}
    @Inject(method="receiveTurret",at=@At("RETURN"),remap=false)
    private static void turret(ServerPlayerEntity player,UfoControlSystem.TurretInput input,CallbackInfoReturnable<Boolean> result){if(result.getReturnValueZ()){com.terminaldetector.drmd.gate.NativeTurretReloadCheck.acceptedAim(player,input);com.terminaldetector.drmd.gate.NativeUfoTurretCheck.aim(player,input);}}
}

