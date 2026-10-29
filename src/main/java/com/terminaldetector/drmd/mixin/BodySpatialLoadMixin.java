package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.contraption.*;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.world.entity.EntityLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Loaded physical hulls may span visible sections even while their pivot is not being tracked.
 * Fabric ENTITY_LOAD observes startTracking, which can occur later than successful insertion. */
@Mixin(ServerEntityManager.class)
public class BodySpatialLoadMixin {
    @Inject(method="addEntity(Lnet/minecraft/world/entity/EntityLike;Z)Z",at=@At("RETURN"))
    private void hullLoaded(EntityLike entity,boolean existing,CallbackInfoReturnable<Boolean> ci) {
        if(ci.getReturnValue()&&entity instanceof BlockBodyEntity body)BodySpatialIndex.track(body);
    }
}
