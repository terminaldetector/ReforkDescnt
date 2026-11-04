package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.world.contraption.boarding.FlightBodySystem;
import com.terminaldetector.drmd.network.FlightBodyNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(FlightBodySystem.class)
public class FlightClockReceiveProbe {
    @Inject(method="receive",at=@At("RETURN"),remap=false)
    private static void received(ServerPlayerEntity player,FlightBodyNetworking.Input input,CallbackInfoReturnable<Boolean> ci){if(Boolean.getBoolean("drmd.gate.flightBody")&&input.sequence()<4)System.out.println("FLIGHT_BODY_RECEIVE input="+input+" expectedEpoch="+FlightBodySystem.epoch(player)+" accepted="+ci.getReturnValue()+" enabled="+com.terminaldetector.drmd.DescentPlayerData.get(player).isEnabled());}
}
