package com.terminaldetector.drmd.mixin;
import com.terminaldetector.drmd.world.gravity.LocalActorTargeting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(MobEntity.class)
public class LocalActorAttackMixin {
    @Shadow @Final private static double ATTACK_RANGE;
    @Inject(method="isInAttackRange",at=@At("HEAD"),cancellable=true)
    private void physicalMeleeRange(LivingEntity target,CallbackInfoReturnable<Boolean> cir){var actor=(MobEntity)(Object)this;if(LocalActorTargeting.framed(actor)&&!actor.hasVehicle())cir.setReturnValue(LocalActorTargeting.inAttackRange(actor,target,ATTACK_RANGE));}
    @Redirect(method="tryAttack",at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;takeKnockback(DDD)V"),require=1,allow=1)
    private void physicalAttackImpulse(LivingEntity target,double strength,double x,double z){LocalActorTargeting.knockback(target,strength,x,z,(MobEntity)(Object)this);}
}
