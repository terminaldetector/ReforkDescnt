package com.terminaldetector.drmd.boardingcheck.mixin;
import com.terminaldetector.drmd.gate.NativeBodyCollisionCheck;
import com.terminaldetector.drmd.client.render.BlockBodyRenderer;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BlockBodyRenderer.class)
public class HullContactProbe {
    @Redirect(method="render(Lcom/terminaldetector/drmd/world/contraption/BlockBodyEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionf;)V"))
    private void rotation(MatrixStack stack,Quaternionf q,BlockBodyEntity b,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light) {
        stack.multiply(q);if(Boolean.getBoolean("drmd.gate.contacts"))NativeBodyCollisionCheck.drawRotation(b,new Quat(q.x,q.y,q.z,q.w));
    }
    @Inject(method="render(Lcom/terminaldetector/drmd/world/contraption/BlockBodyEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("RETURN"))
    private void drawn(BlockBodyEntity b,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light,CallbackInfo ci) {
        if(Boolean.getBoolean("drmd.gate.contacts"))NativeBodyCollisionCheck.drew(b);
    }
}
