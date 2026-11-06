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
    private static final java.util.Map<java.util.UUID,Integer> drmd$poses=new java.util.HashMap<>();
    @Inject(method="render(Lcom/terminaldetector/drmd/entity/RagdollEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("HEAD"))
    private void drmd$pose(RagdollEntity e,float yaw,float delta,MatrixStack m,VertexConsumerProvider v,int light,CallbackInfo ci){
        if(!NativeDroneWreckCheck.READ)return;net.minecraft.entity.Entity actor=e;int n=drmd$poses.merge(actor.getUuid(),1,Integer::sum);if(n>8)return;
        var c=net.minecraft.client.MinecraftClient.getInstance();
        com.terminaldetector.drmd.DescentMod.LOGGER.info("[wreck draw pose] kind={} pos={} lerped={} delta={} matrix={} firstPart={} camera={} flight={}",e.flightHullKind(),actor.getPos(),actor.getLerpedPos(delta),delta,m.peek().getPositionMatrix().getTranslation(new org.joml.Vector3f()),e.currentRenderParts().getFirst(),c.gameRenderer.getCamera().getPos(),com.terminaldetector.drmd.client.DescentClientState.enabled);
    }
    @Inject(method="render(Lcom/terminaldetector/drmd/entity/RagdollEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("RETURN"))
    private void drmd$actualWreck(RagdollEntity e,float yaw,float delta,MatrixStack m,VertexConsumerProvider v,int light,CallbackInfo ci){NativeDroneWreckCheck.drewWreck(e);}
}
