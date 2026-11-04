package com.terminaldetector.drmd.boardingcheck.mixin;
import com.terminaldetector.drmd.gate.NativeShipGravityCheck;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Measure the local player's actual appended transform; escorts/projectiles are other models. */
@Mixin(value=LivingEntityRenderer.class,priority=500)
public class ShipModelProbe {
 @Unique private Matrix4f drmd$shipBefore;
 @Inject(method="setupTransforms",at=@At("HEAD"))
 private void before(LivingEntity actor,MatrixStack matrices,float age,float yaw,float delta,float scale,CallbackInfo ci) {
  if(Boolean.getBoolean("drmd.gate.shipGravity")&&actor==MinecraftClient.getInstance().player)drmd$shipBefore=new Matrix4f(matrices.peek().getPositionMatrix());
 }
 @Inject(method="render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
  at=@At(value="INVOKE",target="Lnet/minecraft/client/render/entity/LivingEntityRenderer;setupTransforms(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V",shift=At.Shift.AFTER))
 private void after(LivingEntity actor,float yaw,float delta,MatrixStack matrices,net.minecraft.client.render.VertexConsumerProvider consumers,int light,CallbackInfo ci) {
  if(Boolean.getBoolean("drmd.gate.shipGravity"))NativeShipGravityCheck.model(actor,drmd$shipBefore,matrices.peek().getPositionMatrix(),delta);
 }
}
