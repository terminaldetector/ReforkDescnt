package com.terminaldetector.drmd.mixin.client;
import com.terminaldetector.drmd.client.flight.BoardingClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class BoardingClientTeleportMixin {
    @Inject(method="onPlayerPositionLook",at=@At("HEAD"))
    private void externalPosition(PlayerPositionLookS2CPacket packet,CallbackInfo ci){
        // Vanilla reschedules this handler onto the client thread before applying the packet.
        if(MinecraftClient.getInstance().isOnThread())BoardingClient.externalTeleport();
    }
}
