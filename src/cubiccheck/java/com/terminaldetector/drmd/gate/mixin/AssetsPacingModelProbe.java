package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.entity.model.PyroShipModel;
import com.terminaldetector.drmd.gate.NativeAssetsPacingCheck;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PyroShipModel.class)
public class AssetsPacingModelProbe {
    @Shadow @Final private ModelPart root;
    @Inject(method="render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V",at=@At("RETURN"))
    private void actual(MatrixStack matrices,VertexConsumer out,int light,int overlay,int color,CallbackInfo ci){if(NativeAssetsPacingCheck.ENABLED)NativeAssetsPacingCheck.model(root);}
}
