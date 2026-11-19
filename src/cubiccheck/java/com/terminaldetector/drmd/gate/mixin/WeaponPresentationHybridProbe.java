package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeWeaponPresentationCheck;
import com.terminaldetector.drmd.client.render.HybridWeaponRenderer;
import com.terminaldetector.drmd.workshop.ClusterModule;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=HybridWeaponRenderer.class,remap=false)
public class WeaponPresentationHybridProbe {
    @Inject(method="draw",at=@At("RETURN"),remap=false)
    private static void actual(MatrixStack matrices,VertexConsumerProvider consumers,ClusterModule module,float recoil,float load,int light,CallbackInfo ci){NativeWeaponPresentationCheck.cockpitMesh(matrices,module,recoil);}
}
