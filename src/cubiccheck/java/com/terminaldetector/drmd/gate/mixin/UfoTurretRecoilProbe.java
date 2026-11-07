package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeUfoTurretCheck;
import com.terminaldetector.drmd.world.mega.*;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=SkyUfoTurret.class,remap=false)
public class UfoTurretRecoilProbe {
    @Inject(method="fire",at=@At("HEAD"),remap=false)
    private void begin(SkyUfoEntity root,ServerPlayerEntity pilot,CallbackInfo ci){NativeUfoTurretCheck.begin((SkyUfoTurret)(Object)this,root);}
    @Inject(method="fire",at=@At("RETURN"),remap=false)
    private void end(SkyUfoEntity root,ServerPlayerEntity pilot,CallbackInfo ci){NativeUfoTurretCheck.end((SkyUfoTurret)(Object)this,root);}
}
