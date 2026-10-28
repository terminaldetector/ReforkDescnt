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
public abstract class HeldWeaponFallbackMixin {
    @org.spongepowered.asm.mixin.Shadow public abstract void renderItem(net.minecraft.entity.LivingEntity e,ItemStack s,net.minecraft.client.render.model.json.ModelTransformationMode mode,boolean left,MatrixStack matrices,VertexConsumerProvider consumers,int light);

    @Inject(method="renderFirstPersonItem",at=@At("HEAD"),cancellable=true)
    private void drmd$replaceMainHand(AbstractClientPlayerEntity player,float delta,float pitch,Hand hand,
            float swing,ItemStack stack,float equip,MatrixStack matrices,VertexConsumerProvider consumers,int light,CallbackInfo ci) {
        if(hand==Hand.MAIN_HAND && WeaponViewRenderer.replacesVanillaHand(MinecraftClient.getInstance())){ci.cancel();return;}
        if(!com.terminaldetector.drmd.client.DescentClientState.enabled)return;
        ci.cancel();if(stack.isEmpty())return;
        boolean left=hand==Hand.OFF_HAND;
        boolean centre=stack.getItem() instanceof com.terminaldetector.drmd.world.portal.gun.PortalProjectorItem;
        matrices.push();
        try{
            matrices.translate(centre?0:left?-.45:.45,-.3,-.8);
            renderItem(player,stack,net.minecraft.client.render.model.json.ModelTransformationMode.FIXED,left,matrices,consumers,light);
        }finally{matrices.pop();}
    }
}
