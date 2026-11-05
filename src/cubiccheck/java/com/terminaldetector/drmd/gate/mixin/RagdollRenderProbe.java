package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.client.render.RagdollRenderer;
import com.terminaldetector.drmd.entity.RagdollEntity;
import com.terminaldetector.drmd.gate.NativeDroneWreckCheck;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(RagdollRenderer.class)
public class RagdollRenderProbe {
    @Inject(method="render(Lcom/terminaldetector/drmd/entity/RagdollEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("RETURN"))
    private void drmd$actualWreck(RagdollEntity e,float yaw,float delta,MatrixStack m,VertexConsumerProvider v,int light,CallbackInfo ci){NativeDroneWreckCheck.drewWreck(e);}
}
