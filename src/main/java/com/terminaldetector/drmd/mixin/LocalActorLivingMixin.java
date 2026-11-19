package com.terminaldetector.drmd.mixin;
import com.terminaldetector.drmd.world.gravity.LocalActorTargeting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LocalActorLivingMixin {
    @Inject(method="canSee",at=@At("HEAD"),cancellable=true)
    private void localSight(Entity target,CallbackInfoReturnable<Boolean> cir){var actor=(LivingEntity)(Object)this;if(LocalActorTargeting.framed(actor)||LocalActorTargeting.framed(target))cir.setReturnValue(LocalActorTargeting.canSee(actor,target));}
    @Redirect(method="takeKnockback",at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;getVelocity()Lnet/minecraft/util/math/Vec3d;"),require=1,allow=1)
    private Vec3d localImpulseInput(LivingEntity target){return LocalActorTargeting.nativeKnockbackVelocity(target);}
    @Redirect(method="takeKnockback",at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;setVelocity(DDD)V"),require=1,allow=1)
    private void localImpulseOutput(LivingEntity target,double x,double y,double z){LocalActorTargeting.nativeKnockbackResult(target,x,y,z);}
}
