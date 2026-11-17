package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.GravityInputRouteTrace;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerPlayNetworkHandler.class)
public class GravityInputSelectionProbe {
    @Shadow public ServerPlayerEntity player;
    @Inject(method="onUpdateSelectedSlot",at=@At("RETURN"))
    private void hotbar(UpdateSelectedSlotC2SPacket packet,CallbackInfo ci){GravityInputRouteTrace.selection(player,"hotbar");}
    @Inject(method="onClickSlot",at=@At("RETURN"))
    private void click(ClickSlotC2SPacket packet,CallbackInfo ci){GravityInputRouteTrace.selection(player,"screen-click");}
}
