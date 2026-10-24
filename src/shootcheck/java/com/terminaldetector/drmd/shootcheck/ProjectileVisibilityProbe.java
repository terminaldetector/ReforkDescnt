package com.terminaldetector.drmd.shootcheck;

import com.terminaldetector.drmd.WeaponRenderCheck;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityRenderer.class, remap = false)
public class ProjectileVisibilityProbe {
    @Inject(method = "shouldRender", at = @At("RETURN"), remap = false)
    private void visibility(Entity entity, Frustum frustum, double x, double y, double z,
                            CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ProjectileEntity shot) WeaponRenderCheck.recordVisibility(shot.getId(),
                "allowed=" + cir.getReturnValue() + " distanceAllowed=" + shot.shouldRender(x, y, z) +
                " frustum=" + frustum.isVisible(shot.getBoundingBox().expand(.5)) +
                " box=" + shot.getBoundingBox() + " time=" + System.nanoTime());
    }
}
