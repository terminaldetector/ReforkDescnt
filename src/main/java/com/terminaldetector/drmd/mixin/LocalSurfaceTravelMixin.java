package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.gravity.LocalSurfaceMotion;
import com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only dry final dissipation changes basis. Fluid/gliding/no-drag branches and actual movement stay native. */
@Mixin(LivingEntity.class)
public abstract class LocalSurfaceTravelMixin {
    @Unique private int drmd$travelDepth;
    @Unique private Vec3d drmd$travelUp;
    @Unique private Vec3d drmd$moved;
    @Unique private float drmd$tangentDrag;
    @Inject(method="travel",at=@At("HEAD"))
    private void begin(Vec3d input,CallbackInfo ci) {
        drmd$moved=null;
        if(++drmd$travelDepth!=1){drmd$travelUp=null;return;}
        drmd$travelUp=LocalSurfaceNavigation.cardinalPhysicsUp((LivingEntity)(Object)this);
    }
    @Inject(method="travel",at=@At("RETURN"))
    private void end(Vec3d input,CallbackInfo ci) {
        if(--drmd$travelDepth==0){drmd$travelUp=null;drmd$moved=null;}
    }
    @Unique private boolean valid(Vec3d up,LivingEntity actor) {
        return up!=null&&drmd$travelDepth==1&&up.equals(LocalSurfaceNavigation.cardinalPhysicsUp(actor));
    }
    @Redirect(method="travel",at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;applyMovementInput(Lnet/minecraft/util/math/Vec3d;F)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d nativeMovement(LivingEntity actor,Vec3d input,float slipperiness) {
        var up=drmd$travelUp;
        boolean capture=valid(up,actor);
        if(capture)drmd$tangentDrag=actor.isOnGround()?slipperiness*.91f:.91f;
        var result=actor.applyMovementInput(input,slipperiness);
        if(capture&&drmd$travelUp==up)drmd$moved=result;
        return result;
    }
    @Redirect(method="travel",slice=@Slice(from=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;hasNoDrag()Z")),
        at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;setVelocity(DDD)V",ordinal=1))
    private void dryDamping(LivingEntity actor,double x,double y,double z) {
        var up=drmd$travelUp;var result=new Vec3d(x,y,z);
        if(valid(up,actor)&&drmd$moved!=null)
            result=LocalSurfaceMotion.dryResult(drmd$moved,result,up,drmd$tangentDrag,actor.hasNoDrag());
        actor.setVelocity(result.x,result.y,result.z);
    }
}
