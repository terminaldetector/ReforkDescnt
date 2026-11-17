package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation;
import com.terminaldetector.drmd.world.gravity.LocalSurfacePath;
import com.terminaldetector.drmd.world.gravity.LocalSurfaceRequestAccess;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Set;

/** Replace only the coordinate-dependent path boundary; retain the native navigator and Path lifecycle. */
@Mixin(EntityNavigation.class)
public abstract class LocalSurfaceNavigationMixin implements LocalSurfaceRequestAccess {
    @Shadow @Final protected MobEntity entity;
    @Shadow protected Path currentPath;
    @Shadow protected double speed;
    @Shadow protected int tickCount;
    @Shadow private BlockPos currentTarget;
    @Shadow private int currentDistance;
    @Shadow protected abstract void adjustPath();

    @Inject(method="findPathToAny(Ljava/util/Set;IZIF)Lnet/minecraft/entity/ai/pathing/Path;",at=@At("HEAD"),cancellable=true)
    private void localRequest(Set<BlockPos> targets,int range,boolean useHeadPos,int distance,float followRange,CallbackInfoReturnable<Path> cir) {
        if(!LocalSurfaceNavigation.framed(entity))return;
        cir.setReturnValue(drmd$surfaceRequest(targets,distance,followRange));
    }
    @Override @Unique public Path drmd$surfaceRequest(Set<BlockPos> targets,int distance,float followRange) {
        Path path=LocalSurfaceNavigation.request((SpiderEntity)entity,targets,followRange);
        if(path!=null){currentTarget=path.getTarget();currentDistance=distance;}
        return path;
    }
    @Redirect(method="startMovingAlong",at=@At(value="INVOKE",target="Lnet/minecraft/entity/ai/pathing/EntityNavigation;adjustPath()V"))
    private void preserveLocalFeet(EntityNavigation navigation) {
        if(!(currentPath instanceof LocalSurfacePath))adjustPath();
    }
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void localFollowing(CallbackInfo ci) {
        boolean framed=LocalSurfaceNavigation.framed(entity);
        if(currentPath instanceof LocalSurfacePath path) {
            ++tickCount;
            if(!framed){currentPath=null;LocalSurfaceNavigation.stop(entity);ci.cancel();return;}
            var spider=(SpiderEntity)entity;LocalSurfaceNavigation.pending(spider);
            if(path.isFinished()){ci.cancel();return;}
            if(!LocalSurfaceNavigation.follow(spider,path,speed))currentPath=null;
            ci.cancel();return;
        }
        if(framed) {
            ++tickCount;currentPath=null;LocalSurfaceNavigation.pending((SpiderEntity)entity);ci.cancel();
        }
    }
    @Inject(method="stop",at=@At("TAIL"))
    private void releaseLocalRequest(CallbackInfo ci){LocalSurfaceNavigation.stop(entity);}
}
