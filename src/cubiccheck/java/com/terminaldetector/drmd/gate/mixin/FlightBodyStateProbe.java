package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.client.flight.FlightBodyClient;
import com.terminaldetector.drmd.network.FlightBodyNetworking;
import com.terminaldetector.drmd.gate.NativeFlightBodyCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FlightBodyClient.class)
public class FlightBodyStateProbe {
    @Inject(method="apply",at=@At("HEAD"),remap=false)
    private static void received(FlightBodyNetworking.State state,CallbackInfo ci){if(Boolean.getBoolean("drmd.gate.flightBody"))NativeFlightBodyCheck.received(state);}
}
