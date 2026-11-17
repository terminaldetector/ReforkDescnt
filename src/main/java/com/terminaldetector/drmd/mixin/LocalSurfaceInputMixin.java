package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;

/** Rotate the newly computed locomotor delta, never the actor's existing world momentum. */
@Mixin(Entity.class)
public abstract class LocalSurfaceInputMixin {
    @Shadow private static Vec3d movementInputToVelocity(Vec3d input,float speed,float yaw){throw new AssertionError();}
    @Redirect(method="updateVelocity",at=@At(value="INVOKE",target="Lnet/minecraft/entity/Entity;movementInputToVelocity(Lnet/minecraft/util/math/Vec3d;FF)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d localAcceleration(Vec3d input,float speed,float yaw) {
        return LocalSurfaceNavigation.input((Entity)(Object)this,movementInputToVelocity(input,speed,yaw));
    }
}
