package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeTorchCameraCheck;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemRenderer.class)
public abstract class TorchCameraItemProbe {
    @Inject(method="renderBakedItemModel",at=@At("RETURN"))
    private void drmd$actualHeldMesh(BakedModel model,ItemStack stack,int light,int overlay,MatrixStack matrices,VertexConsumer vertices,CallbackInfo ci){
        if(NativeTorchCameraCheck.ENABLED)NativeTorchCameraCheck.baked(model,stack,matrices);
    }
}
