package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.gravity.FootGravitySystem;
import com.terminaldetector.drmd.world.gravity.GravityMount;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The same local-up frame owns collision, server interaction eyes and the rendered camera eye. */
@Mixin(Entity.class)
public class FootGravityGeometryMixin {
    @Inject(method="calculateBoundingBox",at=@At("HEAD"),cancellable=true)
    private void drmd$localBody(CallbackInfoReturnable<Box> cir) {
        if((Object)this instanceof PlayerEntity player && FootGravitySystem.isActive(player)) {
            cir.setReturnValue(GravityMount.standingBox(player.getPos(),FootGravitySystem.getUp(player),
                    player.getWidth()/2,player.getDimensions(player.getPose()).height()));
        }
    }
    @Inject(method="getEyePos",at=@At("RETURN"),cancellable=true)
    private void drmd$localEye(CallbackInfoReturnable<Vec3d> cir) {
        if((Object)this instanceof PlayerEntity player && FootGravitySystem.isActive(player)) {
            double height=cir.getReturnValue().y-player.getY();
            cir.setReturnValue(player.getPos().add(FootGravitySystem.getUp(player).multiply(height)));
        }
    }
    @Inject(method="getCameraPosVec",at=@At("RETURN"),cancellable=true)
    private void drmd$localCameraEye(float delta,CallbackInfoReturnable<Vec3d> cir) {
        if((Object)this instanceof PlayerEntity player && FootGravitySystem.isActive(player)) {
            Vec3d feet=player.getLerpedPos(delta);
            double height=cir.getReturnValue().y-feet.y;
            cir.setReturnValue(feet.add(FootGravitySystem.getUp(player).multiply(height)));
        }
    }
}
