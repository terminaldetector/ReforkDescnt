package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeMobGeometryCheck;
import com.terminaldetector.drmd.client.presentation.Ps1BodyGeometryLayer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=Ps1BodyGeometryLayer.class,remap=false)
public class MobGeometryRouteProbe {
    @Inject(method="selectMob",at=@At("RETURN"),remap=false)
    private static void selected(RenderLayer original,Identifier texture,CallbackInfoReturnable<RenderLayer> ci){if(NativeMobGeometryCheck.ENABLED)NativeMobGeometryCheck.route(original,texture,ci.getReturnValue());}
}
