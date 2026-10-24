package com.terminaldetector.drmd.shootcheck;

import com.terminaldetector.drmd.WeaponRenderCheck;
import com.terminaldetector.drmd.client.render.ProjectileRenderer;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes actual render calls only in the isolated weapon-check client. */
@Mixin(value = ProjectileRenderer.class, remap = false)
public class ProjectileRendererProbe {
    @Inject(method = "render(Lcom/terminaldetector/drmd/entity/ProjectileEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), remap = false)
    private void record(ProjectileEntity entity, float yaw, float delta, MatrixStack matrices,
                        VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        WeaponRenderCheck.recordRender(entity, matrices);
    }
}
