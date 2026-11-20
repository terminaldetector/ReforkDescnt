package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.contraption.boarding.*;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.server.network.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class BoardingServerMovementMixin implements BoardingConnection {
    @Shadow public ServerPlayerEntity player;
    @Shadow private boolean floating;
    @Shadow private int floatingTicks;
    @Shadow private int requestedTeleportId;
    @Shadow private Vec3d requestedTeleportPos;
    @Shadow public abstract void syncWithPlayerPosition();
    @Unique private int drmd$boardingPlacementEpoch=-1;
    @Unique private int drmd$boardingPlacementId;

    @Override public void drmd$bodyMovementOwned(){floating=false;floatingTicks=0;syncWithPlayerPosition();}
    @Override public void drmd$preserveBoardingPlacementOnConfirm(int epoch){
        drmd$boardingPlacementEpoch=epoch;
        drmd$boardingPlacementId=requestedTeleportId;
    }

    @Inject(method="requestTeleport(DDDFFLjava/util/Set;)V",at=@At("HEAD"))
    private void externalPosition(CallbackInfo ci) {
        drmd$boardingPlacementEpoch=-1;
        BoardingSystem.externalTeleport(player);
        FlightBodySystem.discontinuity(player);
    }

    @Inject(method="onTeleportConfirm",at=@At(value="INVOKE",target="Lnet/minecraft/network/packet/c2s/play/TeleportConfirmC2SPacket;getTeleportId()I"))
    private void confirmBoardingPlacement(TeleportConfirmC2SPacket packet,CallbackInfo ci) {
        if(drmd$boardingPlacementEpoch<0||packet.getTeleportId()!=drmd$boardingPlacementId
                ||requestedTeleportId!=drmd$boardingPlacementId||requestedTeleportPos==null)return;
        // The contact motor has already advanced on the moving deck while this confirmation
        // was in flight. Vanilla still validates and clears the request using the current feet.
        if(BoardingSystem.drives(player)&&BoardingSystem.epoch(player)==drmd$boardingPlacementEpoch)
            requestedTeleportPos=player.getPos();
        drmd$boardingPlacementEpoch=-1;
    }

    @Inject(method="onPlayerMove",at=@At("HEAD"),cancellable=true)
    private void bodyMove(PlayerMoveC2SPacket packet,CallbackInfo ci) {
        var handler=(ServerPlayNetworkHandler)(Object)this;
        NetworkThreadUtils.forceMainThread(packet,handler,player.getServerWorld());
        boolean flight=FlightBodySystem.owns(player);
        boolean restoring=BoardingRestore.holds(player);
        if(!flight&&!BoardingSystem.drives(player)&&!restoring)return;
        double x=packet.getX(player.getX()),y=packet.getY(player.getY()),z=packet.getZ(player.getZ());
        float yaw=packet.getYaw(player.getYaw()),pitch=packet.getPitch(player.getPitch());
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch))
            handler.disconnect(Text.translatable("multiplayer.disconnect.invalid_player_movement"));
        else if(!restoring&&(flight||BoardingSystem.state(player).gravityOwner()<0)) {player.setYaw(yaw);player.setPitch(net.minecraft.util.math.MathHelper.clamp(pitch,-90,90));}
        drmd$bodyMovementOwned();ci.cancel();
    }
}
