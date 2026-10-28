package com.terminaldetector.drmd.boardingcheck.mixin;
import com.terminaldetector.drmd.client.flight.BoardingClient;
import com.terminaldetector.drmd.network.BoardingNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BoardingClient.class)
public class StateProbe {
    @Inject(method="apply",at=@At("HEAD"),remap=false)
    private static void state(BoardingNetworking.State p,CallbackInfo ci){if(Boolean.getBoolean("drmd.gate.shipGravity"))com.terminaldetector.drmd.gate.NativeShipGravityCheck.received(p);else com.terminaldetector.drmd.gate.NativeBoardingCheck.received(p);}
}
