package com.terminaldetector.drmd.mixin.client;
import com.terminaldetector.drmd.client.cloud.AltitudeCloudRenderer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Optional scope tag only: no Sodium classes, fields, geometry or dependency are imported. */
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.immediate.CloudRenderer",remap=false,priority=1500)
public class AltitudeCloudSodiumMixin {
    @Inject(method="render",at=@At("HEAD"),remap=false)
    private void enter(CallbackInfo ci){AltitudeCloudRenderer.enterCloudScope(true);}
    @Inject(method="render",at=@At("RETURN"),remap=false)
    private void leave(CallbackInfo ci){AltitudeCloudRenderer.leaveCloudScope();}
}
