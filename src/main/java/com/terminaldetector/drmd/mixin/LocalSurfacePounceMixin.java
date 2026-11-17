package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.gravity.LocalSurfaceMotion;
import com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.PounceAtTargetGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** Keep the native goal/RNG/target/lifecycle; only its final impulse uses the actor's cardinal basis. */
@Mixin(PounceAtTargetGoal.class)
public abstract class LocalSurfacePounceMixin {
    @Shadow @Final private MobEntity mob;
    @Shadow private LivingEntity target;
    @Shadow @Final private float velocity;

    @Redirect(method="start",at=@At(value="INVOKE",target="Lnet/minecraft/entity/mob/MobEntity;setVelocity(DDD)V"))
    private void localImpulse(MobEntity actor,double x,double y,double z) {
        var result=new Vec3d(x,y,z);var up=LocalSurfaceNavigation.cardinalPhysicsUp(actor);
        if(up!=null&&target!=null)
            result=LocalSurfaceMotion.pounce(target.getPos().subtract(actor.getPos()),actor.getVelocity(),up,velocity,result);
        actor.setVelocity(result.x,result.y,result.z);
    }
}
