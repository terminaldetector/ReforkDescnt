package com.terminaldetector.drmd.world.portal.gun;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import net.minecraft.item.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** RMB orange, LMB blue (client input adapter); sneaking with RMB removes the owned pair. */
public final class PortalProjectorItem extends Item {
    public PortalProjectorItem(Settings settings){super(settings.maxCount(1));}
    @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand) {
        ItemStack stack=player.getStackInHand(hand);
        if(hand!=Hand.MAIN_HAND)return TypedActionResult.pass(stack);
        if(player.getItemCooldownManager().isCoolingDown(this))return TypedActionResult.fail(stack);
        if(world.isClient){player.getItemCooldownManager().set(this,PortalProjector.COOLDOWN);return TypedActionResult.success(stack);}
        if(player instanceof ServerPlayerEntity server)trigger(server,2,player.isSneaking());
        return TypedActionResult.consume(stack);
    }
    public static void trigger(ServerPlayerEntity player,int slot,boolean reset) {
        trigger(player,slot,reset,com.terminaldetector.drmd.weapon.core.WeaponCore.aimDir(player));
    }
    public static void trigger(ServerPlayerEntity player,int slot,boolean reset,net.minecraft.util.math.Vec3d aim) {
        double length=aim.lengthSquared();
        if(slot<1 || slot>2 || !Double.isFinite(length) || length<.9 || length>1.1)return;
        if(!(player.getMainHandStack().getItem() instanceof PortalProjectorItem) || player.isSpectator()
            || player.getItemCooldownManager().isCoolingDown(ModWorldBlocks.PORTAL_PROJECTOR))return;
        player.getItemCooldownManager().set(ModWorldBlocks.PORTAL_PROJECTOR,PortalProjector.COOLDOWN);
        var result=reset?PortalProjector.clear(player):PortalProjector.fire(player,slot,aim.normalize());
        player.sendMessage(Text.translatable("message.drmd.projector."+result.name().toLowerCase(java.util.Locale.ROOT)),true);
        if(result==PortalProjector.Result.PLACED) {
            var world=player.getServerWorld();
            world.playSound(null,player.getBlockPos(),SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,SoundCategory.PLAYERS,.7f,slot==1?1.5f:.9f);
            com.terminaldetector.drmd.network.ModNetworking.shotFx(world,player.getEyePos(),slot==1?0x35adff:0xff9b26,.3f,false);
        }
    }
    @Override public void appendTooltip(ItemStack stack,TooltipContext context,java.util.List<Text> tooltip,net.minecraft.item.tooltip.TooltipType type) {
        tooltip.add(Text.translatable("tooltip.drmd.portal_projector.controls"));
        tooltip.add(Text.translatable("tooltip.drmd.portal_projector.reset"));
    }
}
