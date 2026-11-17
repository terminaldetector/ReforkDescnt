package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation;
import com.terminaldetector.drmd.world.gravity.LocalSurfaceRequestAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.SpiderNavigation;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Set;

/** An unavailable local route cannot fall through to SpiderNavigation's untested world-floor chase. */
@Mixin(SpiderNavigation.class)
public abstract class LocalSpiderNavigationMixin extends MobNavigation {
    @Shadow private BlockPos targetPos;
    protected LocalSpiderNavigationMixin(MobEntity actor,World world){super(actor,world);}
    @Inject(method="findPathTo(Lnet/minecraft/entity/Entity;I)Lnet/minecraft/entity/ai/pathing/Path;",at=@At("HEAD"),cancellable=true)
    private void preserveEntityDestination(Entity target,int distance,CallbackInfoReturnable<Path> cir) {
        if(!LocalSurfaceNavigation.framed(entity))return;
        targetPos=target.getBlockPos();cir.setReturnValue(originalDestination(targetPos,distance));
    }
    @Inject(method="findPathTo(Lnet/minecraft/util/math/BlockPos;I)Lnet/minecraft/entity/ai/pathing/Path;",at=@At("HEAD"),cancellable=true)
    private void preserveBlockDestination(BlockPos target,int distance,CallbackInfoReturnable<Path> cir) {
        if(!LocalSurfaceNavigation.framed(entity))return;
        targetPos=target.toImmutable();cir.setReturnValue(originalDestination(targetPos,distance));
    }
    @Unique private Path originalDestination(BlockPos target,int distance) {
        return ((LocalSurfaceRequestAccess)(Object)this).drmd$surfaceRequest(Set.of(target),distance,
            (float)entity.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE));
    }
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void localPathLifecycle(CallbackInfo ci) {
        if(!LocalSurfaceNavigation.framed(entity))return;
        super.tick();ci.cancel();
    }
}
