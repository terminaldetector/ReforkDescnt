package com.terminaldetector.drmd.world.portal.mirror;

import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

/** Legacy registry IDs remain readable in saves; keys no longer activate portals. */
public class MirrorLinkerItem extends Item {
    private final MirrorLinkerTier tier;
    public MirrorLinkerItem(Settings settings, MirrorLinkerTier tier) {
        super(settings.maxCount(1)); this.tier=tier;
    }
    public MirrorLinkerTier tier() {return tier;}
    @Override public ActionResult useOnBlock(ItemUsageContext context) {
        if (!context.getWorld().isClient && context.getPlayer()!=null)
            context.getPlayer().sendMessage(Text.translatable("message.drmd.portal.fusion_key_retired"),true);
        return ActionResult.SUCCESS;
    }
}
