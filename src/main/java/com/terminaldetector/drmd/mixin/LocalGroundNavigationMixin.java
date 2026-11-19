package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation;
import com.terminaldetector.drmd.world.gravity.LocalSurfaceRequestAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Set;

/** Preserve genuine native destinations before MobNavigation coerces their world-Y floor.
 * Native goals still choose requests and decide whether a pending (null) path can start. */
@Mixin(MobNavigation.class)
public abstract class LocalGroundNavigationMixin extends EntityNavigation {
    protected LocalGroundNavigationMixin(MobEntity actor,World world){super(actor,world);}
    @Unique private boolean localGround(){return !(entity instanceof SpiderEntity)&&LocalSurfaceNavigation.framed(entity);}
    @Unique private Path localTarget(BlockPos target,int distance){
        return ((LocalSurfaceRequestAccess)(Object)this).drmd$surfaceRequest(Set.of(target),distance,
            (float)entity.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE));
    }
    @Inject(method="findPathTo(Lnet/minecraft/util/math/BlockPos;I)Lnet/minecraft/entity/ai/pathing/Path;",at=@At("HEAD"),cancellable=true)
    private void originalBlock(BlockPos target,int distance,CallbackInfoReturnable<Path> cir){if(localGround())cir.setReturnValue(localTarget(target,distance));}
    @Inject(method="findPathTo(Lnet/minecraft/entity/Entity;I)Lnet/minecraft/entity/ai/pathing/Path;",at=@At("HEAD"),cancellable=true)
    private void originalEntity(Entity target,int distance,CallbackInfoReturnable<Path> cir){if(localGround())cir.setReturnValue(localTarget(target.getBlockPos(),distance));}
}
