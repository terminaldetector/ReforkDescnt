package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeAssetsPacingCheck;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(MinecraftClient.class)
public class AssetsPacingLimitProbe {
    @Shadow private int getFramerateLimit(){throw new AssertionError();}
    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/MinecraftClient;getFramerateLimit()I"))
    private int actual(MinecraftClient client){int value=getFramerateLimit();if(NativeAssetsPacingCheck.ENABLED)NativeAssetsPacingCheck.limit(value);return value;}
}
