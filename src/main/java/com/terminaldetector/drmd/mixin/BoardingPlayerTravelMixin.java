package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public class BoardingPlayerTravelMixin {
    @Inject(method="travel",at=@At("HEAD"),cancellable=true)
    private void bodyTravel(Vec3d input,CallbackInfo ci) {
        if((Object)this instanceof ServerPlayerEntity p&&BoardingSystem.drives(p))ci.cancel();
    }
}
