package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeWeaponPresentationCheck;
import com.terminaldetector.drmd.client.render.WeaponViewRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=WeaponViewRenderer.class,remap=false)
public class WeaponPresentationCockpitProbe {
    @Inject(method="drawBox",at=@At("RETURN"),remap=false)
    private static void actual(MatrixStack matrices,VertexConsumerProvider consumers,int colour,int light,CallbackInfo ci){NativeWeaponPresentationCheck.cockpitBox(matrices);}
}
