package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.world.contraption.BodyBlockInteraction;
import com.terminaldetector.drmd.gate.NativeMountedWeaponCheck;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;
@Mixin(BodyBlockInteraction.class)
public class BodyUseValidationProbe {
    @Inject(method="validated",at=@At("RETURN"),remap=false)
    private static void validated(ServerPlayerEntity p,BodyBlockInteraction.Edit edit,CallbackInfoReturnable<Optional<BodyBlockInteraction.Target>> ci){
        if(NativeMountedWeaponCheck.ENABLED)NativeMountedWeaponCheck.validated(p,edit,ci.getReturnValue().isPresent());
    }
}
