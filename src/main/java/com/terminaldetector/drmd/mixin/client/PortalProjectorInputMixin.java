package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.world.portal.gun.PortalProjectorItem;
import com.terminaldetector.drmd.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Route both buttons to item fire; neither block breaking nor container use owns a gun click. */
@Mixin(MinecraftClient.class)
public class PortalProjectorInputMixin {
    private void drmd$send(int slot,boolean reset) {
        var client=(MinecraftClient)(Object)this;var player=client.player;
        if(player.getItemCooldownManager().isCoolingDown(player.getMainHandStack().getItem()))return;
        var aim=com.terminaldetector.drmd.client.DescentClientState.enabled
            && com.terminaldetector.drmd.client.flight.ShipAttitudeClient.isPrimed()
            ? com.terminaldetector.drmd.client.flight.ShipAttitudeClient.get().forward():player.getRotationVec(1);
        ClientPlayNetworking.send(new ModNetworking.PortalProjectorPayload(slot,reset,(float)aim.x,(float)aim.y,(float)aim.z));
        player.getItemCooldownManager().set(player.getMainHandStack().getItem(),com.terminaldetector.drmd.world.portal.gun.PortalProjector.COOLDOWN);
        player.swingHand(Hand.MAIN_HAND);
    }
    private boolean drmd$projector() {
        var client=(MinecraftClient)(Object)this;
        return client.player!=null && !client.player.isSpectator()
            && client.player.getMainHandStack().getItem() instanceof PortalProjectorItem;
    }
    @Inject(method="doAttack",at=@At("HEAD"),cancellable=true)
    private void primary(CallbackInfoReturnable<Boolean> ci) {
        if(!drmd$projector())return;
        drmd$send(1,false);
        ci.setReturnValue(false);
    }
    @Inject(method="handleBlockBreaking",at=@At("HEAD"),cancellable=true)
    private void preventBreaking(boolean breaking,CallbackInfo ci){if(drmd$projector())ci.cancel();}
    @Inject(method="doItemUse",at=@At("HEAD"),cancellable=true)
    private void secondary(CallbackInfo ci) {
        if(!drmd$projector())return;
        var client=(MinecraftClient)(Object)this;
        if(client.interactionManager!=null)drmd$send(2,client.player.isSneaking());
        ci.cancel();
    }
}
