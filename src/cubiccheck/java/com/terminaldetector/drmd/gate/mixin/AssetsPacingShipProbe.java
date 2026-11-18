package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.client.render.PyroShipRenderer;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.gate.NativeAssetsPacingCheck;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PyroShipRenderer.class)
public class AssetsPacingShipProbe {
    @Inject(method="render(Lcom/terminaldetector/drmd/entity/PyroShipEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("HEAD"))
    private void before(PyroShipEntity ship,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider out,int light,CallbackInfo ci){if(NativeAssetsPacingCheck.ENABLED)NativeAssetsPacingCheck.actor(ship,true);}
    @Inject(method="render(Lcom/terminaldetector/drmd/entity/PyroShipEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("RETURN"))
    private void after(PyroShipEntity ship,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider out,int light,CallbackInfo ci){if(NativeAssetsPacingCheck.ENABLED)NativeAssetsPacingCheck.actor(ship,false);}
}
