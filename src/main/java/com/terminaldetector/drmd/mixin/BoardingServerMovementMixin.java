package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.contraption.boarding.*;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.*;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class BoardingServerMovementMixin implements BoardingConnection {
    @Shadow public ServerPlayerEntity player;
    @Shadow private boolean floating;
    @Shadow private int floatingTicks;
    @Shadow public abstract void syncWithPlayerPosition();
    @Override public void drmd$bodyMovementOwned(){floating=false;floatingTicks=0;syncWithPlayerPosition();}
    @Inject(method="onPlayerMove",at=@At("HEAD"),cancellable=true)
    private void bodyMove(PlayerMoveC2SPacket packet,CallbackInfo ci) {
        var handler=(ServerPlayNetworkHandler)(Object)this;
        NetworkThreadUtils.forceMainThread(packet,handler,player.getServerWorld());
        if(!BoardingSystem.drives(player))return;
        double x=packet.getX(player.getX()),y=packet.getY(player.getY()),z=packet.getZ(player.getZ());
        float yaw=packet.getYaw(player.getYaw()),pitch=packet.getPitch(player.getPitch());
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch))
            handler.disconnect(Text.translatable("multiplayer.disconnect.invalid_player_movement"));
        else {player.setYaw(yaw);player.setPitch(net.minecraft.util.math.MathHelper.clamp(pitch,-90,90));}
        drmd$bodyMovementOwned();ci.cancel();
    }
}
