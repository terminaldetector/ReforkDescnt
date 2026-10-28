package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.client.render.BlockBodyRenderer;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Quaternionf;
@Mixin(BlockBodyRenderer.class)
public class BodyRenderProbe {
    @Redirect(method="render(Lcom/terminaldetector/drmd/world/contraption/BlockBodyEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionf;)V"))
    private void actualRotation(MatrixStack stack,Quaternionf q,BlockBodyEntity body,float yaw,float delta,
            MatrixStack matrices,VertexConsumerProvider consumers,int light) {
        stack.multiply(q);
        if(Boolean.getBoolean("drmd.gate.contacts"))com.terminaldetector.drmd.gate.NativeBodyCollisionCheck.drawRotation(
            body,new com.terminaldetector.drmd.client.portal.PortalTransform.Quat(q.x,q.y,q.z,q.w));
    }
    @Inject(method="render(Lcom/terminaldetector/drmd/world/contraption/BlockBodyEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("RETURN"))
    private void drawn(BlockBodyEntity body,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light,CallbackInfo ci) {
        if(Boolean.getBoolean("drmd.gate.contacts"))com.terminaldetector.drmd.gate.NativeBodyCollisionCheck.drew(body);
    }
}
