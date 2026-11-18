package com.terminaldetector.drmd.mixin.client;
import com.terminaldetector.drmd.client.cloud.AltitudeCloudRenderer;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=WorldRenderer.class,priority=1500)
public class AltitudeCloudVanillaMixin {
    @Inject(method="renderClouds",at=@At("HEAD"))
    private void enter(CallbackInfo ci){AltitudeCloudRenderer.enterCloudScope(false);}
    @Inject(method="renderClouds",at=@At("RETURN"))
    private void leave(CallbackInfo ci){AltitudeCloudRenderer.leaveCloudScope();}
}
