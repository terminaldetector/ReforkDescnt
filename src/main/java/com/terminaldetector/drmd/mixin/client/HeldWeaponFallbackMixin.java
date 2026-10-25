package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.render.WeaponViewRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only the cockpit weapon's hand, preserving tools, offhand and disabled-view fallback. */
@Mixin(HeldItemRenderer.class)
public class HeldWeaponFallbackMixin {
    @Inject(method="renderFirstPersonItem",at=@At("HEAD"),cancellable=true)
    private void drmd$replaceMainHand(AbstractClientPlayerEntity player,float delta,float pitch,Hand hand,
            float swing,ItemStack stack,float equip,MatrixStack matrices,VertexConsumerProvider consumers,int light,CallbackInfo ci) {
        if(hand==Hand.MAIN_HAND && WeaponViewRenderer.replacesVanillaHand(MinecraftClient.getInstance()))ci.cancel();
    }
}
