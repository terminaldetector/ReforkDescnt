package com.terminaldetector.drmd.flight;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/**
 * Craftable propulsion module — socket it into a Pyro GX's single propulsion slot to grant that
 * tier's afterburner (mirrors {@code world/build/ConstructLaserItem}'s one-item-per-tier shape).
 * No numeric stats of its own: {@link AfterburnerTiers}'s existing pure functions of the tier int
 * are the only source of truth, unchanged by this item's existence — only how a tier is obtained
 * and equipped changes, from a free button click to crafting and socketing this.
 */
public class AcceleratorModuleItem extends Item {
	private final int tier;

	public AcceleratorModuleItem(Settings settings, int tier) {
		super(settings.maxCount(1));
		this.tier = AfterburnerTiers.clamp(tier);
	}

	public int tier() { return tier; }

	/** Chat color code matching {@link AfterburnerTiers#colorArgb}'s own HUD colors — that method
	 *  returns an ARGB int for drawing, not a §-code, so this is a small local mirror of the same
	 *  four-tier mapping rather than a method AfterburnerTiers doesn't have. */
	private static String colorCode(int tier) {
		return switch (AfterburnerTiers.clamp(tier)) {
			case 1 -> "§a";
			case 2 -> "§e";
			case 3 -> "§6";
			default -> "§c";
		};
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal(colorCode(tier) + AfterburnerTiers.colorName(tier).toUpperCase()
				+ " §7propulsion module"));
		tooltip.add(Text.literal(String.format(Locale.ROOT,
				"§7Burn: §f%.1f/s §7· Accel §f×%.2f-%.2f §7· Speed §f×%.2f-%.2f",
				AfterburnerTiers.costPerSec(tier),
				AfterburnerTiers.accelMult(tier, 0f), AfterburnerTiers.accelMult(tier, 1f),
				AfterburnerTiers.speedMult(tier, 0f), AfterburnerTiers.speedMult(tier, 1f))));
		tooltip.add(Text.literal("§8Socket into the ship's propulsion slot"));
	}
}
