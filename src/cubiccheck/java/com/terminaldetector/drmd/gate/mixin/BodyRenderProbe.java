package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.client.render.BlockBodyRenderer;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BlockBodyRenderer.class)
public class BodyRenderProbe {
    @Inject(method="render(Lcom/terminaldetector/drmd/world/contraption/BlockBodyEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("RETURN"))
    private void drawn(BlockBodyEntity body,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light,CallbackInfo ci) {
        if(Boolean.getBoolean("drmd.gate.contacts"))com.terminaldetector.drmd.gate.NativeBodyCollisionCheck.drew(body);
    }
}
