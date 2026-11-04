package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.client.render.DroneRenderer;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.gate.NativeSwarmBaselineCheck;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(DroneRenderer.class)
public class DroneRenderProbe {
    @Inject(method="render(Lcom/terminaldetector/drmd/entity/DroneEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("RETURN"))
    private void drawn(DroneEntity d,float yaw,float delta,MatrixStack m,VertexConsumerProvider v,int light,CallbackInfo ci){if(Boolean.getBoolean("drmd.gate.swarm"))NativeSwarmBaselineCheck.drew(d);}
}
