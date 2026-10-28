package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.flight.BoardingClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public class BoardingClientTravelMixin {
    @Inject(method="travel",at=@At("HEAD"),cancellable=true)
    private void bodyTravel(Vec3d input,CallbackInfo ci) {
        if((Object)this instanceof ClientPlayerEntity p&&BoardingClient.drives(p)){BoardingClient.travel(p,input);ci.cancel();}
    }
}
