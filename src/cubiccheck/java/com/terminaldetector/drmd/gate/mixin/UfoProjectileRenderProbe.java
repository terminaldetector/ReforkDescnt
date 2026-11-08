package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.client.render.ProjectileRenderer;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ProjectileRenderer.class)
public class UfoProjectileRenderProbe {
    @Inject(method="render(Lcom/terminaldetector/drmd/entity/ProjectileEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("RETURN"))
    private void drew(ProjectileEntity p,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light,CallbackInfo ci){com.terminaldetector.drmd.gate.NativeUfoAiCheck.drewShot(p,matrices);com.terminaldetector.drmd.gate.NativeUfoWeaponsCheck.drew(p);}
}
