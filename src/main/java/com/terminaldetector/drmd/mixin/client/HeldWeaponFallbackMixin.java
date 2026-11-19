package com.terminaldetector.drmd.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.DescentCamera;
import com.terminaldetector.drmd.client.render.WeaponViewRenderer;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import com.terminaldetector.drmd.world.bombardment.BombardmentItems;
import com.terminaldetector.drmd.world.portal.gun.PortalProjectorItem;
import com.terminaldetector.drmd.world.portal.mirror.PortalGunItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the mod's hand equipment in the eye frame and omit the hand behind mounted weapons. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldWeaponFallbackMixin {
    @org.spongepowered.asm.mixin.Shadow public abstract void renderItem(net.minecraft.entity.LivingEntity e,ItemStack s,net.minecraft.client.render.model.json.ModelTransformationMode mode,boolean left,MatrixStack matrices,VertexConsumerProvider consumers,int light);

    @Inject(method="renderFirstPersonItem",at=@At("HEAD"),cancellable=true)
    private void drmd$replaceMainHand(AbstractClientPlayerEntity player,float delta,float pitch,Hand hand,
            float swing,ItemStack stack,float equip,MatrixStack matrices,VertexConsumerProvider consumers,int light,CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (player != client.player || !DescentCamera.ownsView() || player.isUsingSpyglass()) return;
        if (hand == Hand.MAIN_HAND && WeaponViewRenderer.replacesVanillaHand(client)) {
            ci.cancel();
            return;
        }
        var item = stack.getItem();
        boolean flight = DescentClientState.enabled;
        boolean centred = item instanceof PortalProjectorItem || item instanceof PortalGunItem;
        boolean engineer = item instanceof com.terminaldetector.drmd.world.engineer.EngineerTools.MiningLaserItem
                || item instanceof com.terminaldetector.drmd.world.engineer.EngineerTools.TunnelLaserItem
                || item instanceof com.terminaldetector.drmd.world.engineer.EngineerTools.RepairLaserItem
                || item instanceof com.terminaldetector.drmd.world.build.ConstructLaserItem;
        if (!flight && !centred && !engineer && !(item instanceof DescentWeaponItem)
                && !(item instanceof BombardmentItems.BombBayItem)
                && !(item instanceof BombardmentItems.LaserDesignatorItem)) return;
        ci.cancel();
        if (stack.isEmpty()) return;

        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean left = arm == Arm.LEFT;
        ModelTransformationMode mode = flight || centred ? ModelTransformationMode.FIXED
                : left ? ModelTransformationMode.FIRST_PERSON_LEFT_HAND : ModelTransformationMode.FIRST_PERSON_RIGHT_HAND;
        matrices.push();
        try {
            // GameRenderer applies the camera matrix globally and its inverse in the hand stack.
            // Retain that cancellation when removing arm animation from the local stack.
            matrices.loadIdentity();
            matrices.multiplyPositionMatrix(new Matrix4f(RenderSystem.getModelViewMatrix()).invert());
            double side = flight ? .45 : .56;
            matrices.translate(centred ? 0 : left ? -side : side, flight || centred ? -.3 : -.52,
                    flight || centred ? -.8 : -.72);
            renderItem(player, stack, mode, left, matrices, consumers, light);
        } finally {
            matrices.pop();
        }
    }
}
