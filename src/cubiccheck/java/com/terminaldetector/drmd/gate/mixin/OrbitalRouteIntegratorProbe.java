package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeOrbitalRouteCheck;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.DescentPlayerData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=FlightSystem.class,remap=false)
public class OrbitalRouteIntegratorProbe {
    @Inject(target=@Desc(value="tick",args={ServerPlayerEntity.class,DescentPlayerData.class,FlightSystem.Steering.class},ret=void.class),at=@At("HEAD"),remap=false)
    private static void before(ServerPlayerEntity p,DescentPlayerData data,FlightSystem.Steering steering,CallbackInfo ci){NativeOrbitalRouteCheck.observedIntegrator(p,steering,true);}
    @Inject(target=@Desc(value="tick",args={ServerPlayerEntity.class,DescentPlayerData.class,FlightSystem.Steering.class},ret=void.class),at=@At("RETURN"),remap=false)
    private static void after(ServerPlayerEntity p,DescentPlayerData data,FlightSystem.Steering steering,CallbackInfo ci){NativeOrbitalRouteCheck.observedIntegrator(p,steering,false);}
}
