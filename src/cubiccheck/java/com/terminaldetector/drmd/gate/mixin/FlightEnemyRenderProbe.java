package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.client.render.FlightEnemyRenderer;
import com.terminaldetector.drmd.entity.FlightEnemyEntity;
import com.terminaldetector.drmd.gate.NativeFlightEnemyCheck;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FlightEnemyRenderer.class)
public class FlightEnemyRenderProbe {
    @Inject(method="render(Lcom/terminaldetector/drmd/entity/FlightEnemyEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("RETURN"))
    private void drew(FlightEnemyEntity e,float yaw,float delta,MatrixStack m,VertexConsumerProvider v,int light,CallbackInfo ci){
        if(com.terminaldetector.drmd.gate.NativeDroneWreckCheck.ENABLED)com.terminaldetector.drmd.gate.NativeDroneWreckCheck.drewLive(e);
        else if(NativeFlightEnemyCheck.ENABLED)NativeFlightEnemyCheck.drew(e);
        else if(com.terminaldetector.drmd.gate.NativeDroneStateCheck.READ)com.terminaldetector.drmd.gate.NativeDroneStateCheck.drew(e);
        else if(Boolean.getBoolean("drmd.gate.swarm"))com.terminaldetector.drmd.gate.NativeSwarmBaselineCheck.drew(e);
    }
}
