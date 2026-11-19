package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeWeaponPresentationCheck;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemRenderer.class)
public class WeaponPresentationItemProbe {
    @Inject(method="renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",at=@At("HEAD"))
    private void begin(ItemStack stack,ModelTransformationMode mode,boolean left,MatrixStack matrices,VertexConsumerProvider consumers,int light,int overlay,BakedModel model,CallbackInfo ci){NativeWeaponPresentationCheck.beginModel(mode);}
    @Inject(method="renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",at=@At("RETURN"))
    private void actual(ItemStack stack,ModelTransformationMode mode,boolean left,MatrixStack matrices,VertexConsumerProvider consumers,int light,int overlay,BakedModel model,CallbackInfo ci){
        NativeWeaponPresentationCheck.model(stack,mode,model==MinecraftClient.getInstance().getBakedModelManager().getMissingModel());
    }
    @Inject(method="renderBakedItemModel",at=@At("RETURN"))
    private void mesh(BakedModel model,ItemStack stack,int light,int overlay,MatrixStack matrices,VertexConsumer vertices,CallbackInfo ci){NativeWeaponPresentationCheck.baked(model,stack,matrices);}
}
