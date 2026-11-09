package com.terminaldetector.drmd.world.contraption.mechanism;

import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import java.util.List;

/** World selections and moving-grid selections share one server-owned assembly tool. */
public final class MechanismWrenchItem extends Item {
    public MechanismWrenchItem(Settings settings){super(settings);}
    @Override public ActionResult useOnBlock(ItemUsageContext context){
        if(context.getWorld().isClient)return ActionResult.SUCCESS;
        if(!(context.getPlayer() instanceof ServerPlayerEntity player))return ActionResult.PASS;
        MechanicalBearingSystem.useWorld(player,context.getBlockPos());return ActionResult.CONSUME;
    }
    @Override public void appendTooltip(ItemStack stack,TooltipContext context,List<Text> tooltip,TooltipType type){
        tooltip.add(Text.translatable("tooltip.drmd.mechanism.world"));
        tooltip.add(Text.translatable("tooltip.drmd.mechanism.bearings"));
        tooltip.add(Text.translatable("tooltip.drmd.mechanism.drive"));
    }
}
