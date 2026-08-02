package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hide vanilla flat hand-item when DRMD cockpit / item-3D view draws the gun instead.
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
	private void drmd$hideVanillaWeapon(AbstractClientPlayerEntity player, float tickDelta, float pitch,
										Hand hand, float swingProgress, ItemStack item,
										float equipProgress, MatrixStack matrices,
										VertexConsumerProvider vertexConsumers, int light,
										CallbackInfo ci) {
		if (hand != Hand.MAIN_HAND) return;
		if (!(item.getItem() instanceof DescentWeaponItem)) return;
		if (!DescentClientState.weaponViewMode.hidesVanillaHandItem()) return;
		if (!DescentClientState.enabled && DescentClientState.weaponViewMode
				!= com.terminaldetector.drmd.client.render.WeaponViewMode.ITEM_3D) return;
		ci.cancel();
	}
}
