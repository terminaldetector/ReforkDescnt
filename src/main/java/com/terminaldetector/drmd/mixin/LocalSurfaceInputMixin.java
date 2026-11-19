package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation;
import com.terminaldetector.drmd.world.gravity.LocalSurfaceMotion;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** New locomotor input and actual cardinal support material; world momentum stays resolved. */
@Mixin(Entity.class)
public abstract class LocalSurfaceInputMixin {
    @Unique private int drmd$materialSampleDepth;
    @Unique private Vec3d drmd$materialUp,drmd$materialPosition;
    @Shadow private static Vec3d movementInputToVelocity(Vec3d input,float speed,float yaw){throw new AssertionError();}
    @Shadow protected abstract float getVelocityMultiplier();
    @Redirect(method="updateVelocity",at=@At(value="INVOKE",target="Lnet/minecraft/entity/Entity;movementInputToVelocity(Lnet/minecraft/util/math/Vec3d;FF)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d localAcceleration(Vec3d input,float speed,float yaw) {
        return LocalSurfaceNavigation.input((Entity)(Object)this,movementInputToVelocity(input,speed,yaw));
    }
    @Inject(method="getVelocityAffectingPos",at=@At("HEAD"),cancellable=true)
    private void actualSupportMaterial(CallbackInfoReturnable<BlockPos> ci){
        var actor=(Entity)(Object)this;var up=LocalSurfaceNavigation.cardinalPhysicsUp(actor);
        if(up==null||!(actor instanceof LivingEntity living))return;
        // A world-Y onGround flag is overwritten by the native move on a wall. The
        // existing FULL/body-clear/normal/gap support query is the physical admission.
        BlockPos actual=LocalSurfaceNavigation.supportMaterial(living,up);if(actual==null)return;
        if(drmd$materialSampleDepth==1){drmd$materialUp=up;drmd$materialPosition=actor.getPos();}
        ci.setReturnValue(actual);
    }
    @Redirect(method="move",at=@At(value="INVOKE",target="Lnet/minecraft/entity/Entity;getVelocityMultiplier()F"),require=1,allow=1)
    private float sampleActualMultiplier(Entity actor){
        // Scope only the real native getter invocation. A different HEAD hook may cancel
        // Entity.move, so HEAD/RETURN depth bookkeeping there could leak across calls.
        drmd$materialUp=null;drmd$materialPosition=null;drmd$materialSampleDepth++;
        try{return getVelocityMultiplier();}finally{drmd$materialSampleDepth--;}
    }
    @Redirect(method="move",at=@At(value="INVOKE",target="Lnet/minecraft/util/math/Vec3d;multiply(DDD)Lnet/minecraft/util/math/Vec3d;"),require=1,allow=1)
    private Vec3d localMaterialMultiplier(Vec3d resolved,double x,double y,double z){
        Vec3d nativeResult=resolved.multiply(x,y,z);var actor=(Entity)(Object)this;var up=drmd$materialUp;var position=drmd$materialPosition;
        drmd$materialUp=null;drmd$materialPosition=null;
        if(drmd$materialSampleDepth!=0||up==null||!up.equals(LocalSurfaceNavigation.cardinalPhysicsUp(actor))
            ||position==null||!actor.getPos().equals(position)||y!=1||x!=z)return nativeResult;
        return LocalSurfaceMotion.materialMultiplier(resolved,nativeResult,up,x);
    }
}
