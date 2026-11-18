package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.contraption.boarding.LivingBodyContactSystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Defer only one nested dry native move. No cancellation of travel, AI, input, jumping or lifecycle. */
@Mixin(LivingEntity.class)
public abstract class LivingBodyContactMixin {
    @Unique private int drmd$livingTravelDepth;
    @Unique private float drmd$livingSlipperiness;
    @Unique private boolean drmd$livingGrounded, drmd$livingDeferred;
    @Inject(method = "travel", at = @At("HEAD"))
    private void drmd$beginLivingTravel(Vec3d input, CallbackInfo ci) {
        if (++drmd$livingTravelDepth == 1) drmd$livingDeferred = false;
    }
    @Inject(method = "applyMovementInput", at = @At("HEAD"))
    private void drmd$captureNativeMaterial(Vec3d input, float slipperiness, CallbackInfoReturnable<Vec3d> cir) {
        if (drmd$livingTravelDepth == 1) {
            drmd$livingSlipperiness = slipperiness;
            drmd$livingGrounded = ((LivingEntity)(Object)this).isOnGround();
        }
    }
    @Redirect(method = "applyMovementInput", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/LivingEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"))
    private void drmd$deferLivingMove(LivingEntity actor, MovementType type, Vec3d delta) {
        if (drmd$livingTravelDepth == 1 && !drmd$livingDeferred && type == MovementType.SELF
            && LivingBodyContactSystem.deferMove(actor, delta, drmd$livingSlipperiness, drmd$livingGrounded))
            drmd$livingDeferred = true;
        else actor.move(type, delta);
    }
    @Inject(method = "travel", at = @At("RETURN"))
    private void drmd$endLivingTravel(Vec3d input, CallbackInfo ci) {
        if (drmd$livingTravelDepth == 1 && drmd$livingDeferred)
            LivingBodyContactSystem.nativeFinished((LivingEntity)(Object)this);
        --drmd$livingTravelDepth;
    }
}
