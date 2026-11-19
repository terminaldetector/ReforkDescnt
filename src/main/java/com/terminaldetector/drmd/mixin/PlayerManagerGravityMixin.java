package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.gravity.FootGravitySystem;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The native session owner retires after its last ordinary save. */
@Mixin(PlayerManager.class)
public abstract class PlayerManagerGravityMixin {
    @Inject(method = "remove", at = @At("TAIL"))
    private void drmd$retireRemovedPlayer(ServerPlayerEntity player, CallbackInfo callback) {
        FootGravitySystem.retireRemovedPlayer(player);
    }
}
