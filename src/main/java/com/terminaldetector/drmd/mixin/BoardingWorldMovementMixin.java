package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.contraption.boarding.BoardingPose;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class BoardingWorldMovementMixin {
    @Inject(method="onTrackedDataSet",at=@At("TAIL"))
    private void boardingFrameChanged(net.minecraft.entity.data.TrackedData<?> data,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if((Object)this instanceof PlayerEntity p&&p instanceof BoardingPose.Access a&&a.drmd$boardingFrameData(data))p.calculateDimensions();
    }
    @Inject(method="adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",at=@At("HEAD"),cancellable=true)
    private void orientedWorld(Vec3d delta,CallbackInfoReturnable<Vec3d> cir) {
        // Only the movement already checked against actual oriented world geometry bypasses the
        // vanilla AABB narrowphase. All other moves, including ordinary players, remain vanilla.
        if((Object)this instanceof PlayerEntity player&&BoardingPose.checkedMove(player))cir.setReturnValue(delta);
    }
}
