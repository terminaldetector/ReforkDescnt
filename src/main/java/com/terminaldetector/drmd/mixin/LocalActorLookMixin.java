package com.terminaldetector.drmd.mixin;
import com.terminaldetector.drmd.world.gravity.LocalActorTargeting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.control.LookControl;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.Optional;

@Mixin(LookControl.class)
public abstract class LocalActorLookMixin {
    @Shadow @Final protected MobEntity entity;
    @Shadow protected double x,y,z;
    @Shadow public abstract void lookAt(double x,double y,double z,float yaw,float pitch);
    @Inject(method="lookAt(Lnet/minecraft/entity/Entity;)V",at=@At("HEAD"),cancellable=true)
    private void framedTarget(Entity target,CallbackInfo ci){if(LocalActorTargeting.framed(entity)||LocalActorTargeting.framed(target)){var eye=LocalActorTargeting.eye(target);lookAt(eye.x,eye.y,eye.z,entity.getMaxLookYawChange(),entity.getMaxLookPitchChange());ci.cancel();}}
    @Inject(method="lookAt(Lnet/minecraft/entity/Entity;FF)V",at=@At("HEAD"),cancellable=true)
    private void framedTargetLimits(Entity target,float yaw,float pitch,CallbackInfo ci){if(LocalActorTargeting.framed(entity)||LocalActorTargeting.framed(target)){var eye=LocalActorTargeting.eye(target);lookAt(eye.x,eye.y,eye.z,yaw,pitch);ci.cancel();}}
    @Inject(method="getTargetYaw",at=@At("HEAD"),cancellable=true)
    private void localYaw(CallbackInfoReturnable<Optional<Float>> cir){if(LocalActorTargeting.framed(entity)){var d=LocalActorTargeting.lookDelta(entity,x,y,z);cir.setReturnValue(d.x*d.x+d.z*d.z<1e-12?Optional.empty():Optional.of((float)(Math.toDegrees(Math.atan2(d.z,d.x))-90)));}}
    @Inject(method="getTargetPitch",at=@At("HEAD"),cancellable=true)
    private void localPitch(CallbackInfoReturnable<Optional<Float>> cir){if(LocalActorTargeting.framed(entity)){var d=LocalActorTargeting.lookDelta(entity,x,y,z);cir.setReturnValue(d.lengthSquared()<1e-12?Optional.empty():Optional.of((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z)))));}}
}
