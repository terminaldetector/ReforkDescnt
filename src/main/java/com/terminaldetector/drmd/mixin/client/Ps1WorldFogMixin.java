package com.terminaldetector.drmd.mixin.client;
import com.terminaldetector.drmd.client.presentation.Ps1WorldFog;
import net.minecraft.client.render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Vanilla/Sodium world geometry uses the same PS1 range without replacing either terrain shader. */
@Mixin(BackgroundRenderer.class)
public class Ps1WorldFogMixin {
    @Inject(method="applyFog",at=@At("RETURN"))
    private static void after(Camera camera,BackgroundRenderer.FogType type,float viewDistance,boolean thickFog,float tickDelta,CallbackInfo ci){
        boolean special=com.terminaldetector.drmd.client.sky.Ps1SkyBackground.protectedView(camera,thickFog);
        Ps1WorldFog.apply(camera,special,tickDelta);
    }
}
