package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.gravity.LocalSurfaceMotion;
import com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation;
import com.terminaldetector.drmd.world.gravity.ActorFrameMath;
import com.terminaldetector.drmd.world.gravity.FootGravitySystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only native jump's new normal and sprint additions change basis for the supported field policy. */
@Mixin(LivingEntity.class)
public abstract class LocalSurfaceJumpMixin {
    @Unique private int drmd$jumpDepth;
    @Unique private Vec3d drmd$jumpUp;

    @Inject(method = "jump", at = @At("HEAD"))
    private void drmd$beginJump(CallbackInfo callback) {
        drmd$jumpUp = ++drmd$jumpDepth == 1
            ? drmd$jumpFrame((LivingEntity) (Object) this) : null;
    }

    @Inject(method = "jump", at = @At("RETURN"))
    private void drmd$endJump(CallbackInfo callback) {
        if (drmd$jumpDepth > 0) drmd$jumpDepth--;
        if (drmd$jumpDepth == 0) drmd$jumpUp = null;
    }

    @Unique private boolean drmd$validJumpFrame(LivingEntity actor) {
        return drmd$jumpDepth == 1 && drmd$jumpUp != null
            && drmd$jumpUp.equals(drmd$jumpFrame(actor));
    }

    @Unique private static Vec3d drmd$jumpFrame(LivingEntity actor) {
        if(actor instanceof PlayerEntity player && player.getWorld().isClient && FootGravitySystem.isActive(player))
            return FootGravitySystem.getUp(player);
        return LocalSurfaceNavigation.cardinalPhysicsUp(actor);
    }

    @Redirect(method = "jump", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/LivingEntity;setVelocity(DDD)V"))
    private void drmd$localJumpNormal(LivingEntity actor, double x, double y, double z) {
        if (!drmd$validJumpFrame(actor)) {
            actor.setVelocity(x, y, z);
            return;
        }
        var previous = actor.getVelocity();
        Vec3d result = actor instanceof PlayerEntity
            ? previous.subtract(drmd$jumpUp.multiply(previous.dotProduct(drmd$jumpUp))).add(drmd$jumpUp.multiply(y))
            : LocalSurfaceMotion.ordinaryJump(previous, new Vec3d(x, y, z), drmd$jumpUp);
        actor.setVelocity(result.x, result.y, result.z);
    }

    @Redirect(method = "jump", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/LivingEntity;addVelocityInternal(Lnet/minecraft/util/math/Vec3d;)V"))
    private void drmd$localJumpSprint(LivingEntity actor, Vec3d delta) {
        if (!drmd$validJumpFrame(actor)) {
            actor.addVelocityInternal(delta);
            return;
        }
        actor.addVelocityInternal(actor instanceof PlayerEntity ? ActorFrameMath.toWorld(delta, drmd$jumpUp)
            : LocalSurfaceMotion.jumpSprint(delta, drmd$jumpUp));
    }
}
