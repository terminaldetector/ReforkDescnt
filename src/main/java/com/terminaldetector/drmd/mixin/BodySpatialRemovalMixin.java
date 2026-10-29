package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.contraption.*;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** True entity removal/unload, rather than just client visibility of the pivot's section. */
@Mixin(Entity.class)
public class BodySpatialRemovalMixin {
    @Inject(method="setRemoved",at=@At("RETURN"))
    private void hullRemoved(Entity.RemovalReason reason,CallbackInfo ci) {
        if((Object)this instanceof BlockBodyEntity body)BodySpatialIndex.untrack(body);
    }
}
