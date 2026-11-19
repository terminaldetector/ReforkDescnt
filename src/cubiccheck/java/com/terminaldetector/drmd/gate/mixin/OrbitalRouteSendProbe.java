package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeOrbitalRouteCheck;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ClientPlayNetworking.class,remap=false)
public class OrbitalRouteSendProbe {
    @Inject(method="send",at=@At("HEAD"),remap=false)
    private static void actualSend(CustomPayload packet,CallbackInfo ci){NativeOrbitalRouteCheck.observedSend(packet);}
}
