package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.UfoControlClient;
import com.terminaldetector.drmd.client.input.DescentKeybinds;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Active flight owns a conflicting roll key; ordinary inventory/drop bindings remain usable. */
@Mixin(MinecraftClient.class)
public abstract class FlightRollKeyOwnershipMixin {
    @Redirect(method="handleInputEvents",at=@At(value="INVOKE",target="Lnet/minecraft/client/option/KeyBinding;wasPressed()Z"))
    private boolean drmd$vanillaPress(KeyBinding binding){
        var client=(MinecraftClient)(Object)this;
        boolean flight=DescentClientState.enabled||UfoControlClient.active();
        boolean action=binding==client.options.inventoryKey||binding==client.options.dropKey;
        if(flight&&action&&client.player!=null&&!client.player.isSpectator()&&client.world!=null&&client.currentScreen==null
                &&(sameKey(binding,DescentKeybinds.rollLeft)||sameKey(binding,DescentKeybinds.rollRight))){
            // Retire the conflicting vanilla edges, including OS repeats, rather than replaying
            // an inventory/drop action after leaving flight. Held roll state is untouched.
            while(binding.wasPressed()){}
            return false;
        }
        return binding.wasPressed();
    }
    private static boolean sameKey(KeyBinding vanilla,KeyBinding roll){
        return roll!=null&&KeyBindingHelper.getBoundKeyOf(vanilla).equals(KeyBindingHelper.getBoundKeyOf(roll));
    }
}
