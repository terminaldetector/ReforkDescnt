package com.terminaldetector.drmd.weapon.items;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Creative-tab entry that opens the DRMD settings screen (client).
 * Screen open is hooked from DescentClient — no client class import here.
 */
public class ModSettingsItem extends Item {
	/** Set from client init to open {@code ModSettingsScreen}. */
	public static Runnable OPEN_SETTINGS = () -> {};

	public ModSettingsItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) {
			OPEN_SETTINGS.run();
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.drmd.mod_settings.desc"));
	}
}
