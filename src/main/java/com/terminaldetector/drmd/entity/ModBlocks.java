package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.weapon.items.SessionControlItems;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlocks {
	public static final Block CHECKPOINT = register("checkpoint",
			new CheckpointBlock(AbstractBlock.Settings.copy(Blocks.BEACON).mapColor(MapColor.EMERALD_GREEN)));
	public static final Block DOCK = register("dock",
			new DockBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).mapColor(MapColor.DIAMOND_BLUE)));
	public static final Block COMBAT_ZONE = register("combat_zone",
			new MarkerBlock(AbstractBlock.Settings.copy(Blocks.GLASS).nonOpaque().mapColor(MapColor.RED)));
	// Do NOT Settings.copy(Blocks.LIGHT): LightBlock luminance reads LEVEL property and crashes.
	public static final Block NAV_NODE = register("nav_node",
			new MarkerBlock(AbstractBlock.Settings.copy(Blocks.GLASS).nonOpaque()
					.luminance(s -> 12).mapColor(MapColor.LIGHT_BLUE)));
	public static final Block OBJECTIVE = register("objective",
			new MarkerBlock(AbstractBlock.Settings.copy(Blocks.GOLD_BLOCK).mapColor(MapColor.GOLD)));

	private ModBlocks() {}

	private static Block register(String id, Block block) {
		Identifier ident = Identifier.of(DescentMod.MOD_ID, id);
		Registry.register(Registries.BLOCK, ident, block);
		Registry.register(Registries.ITEM, ident, new BlockItem(block, new Item.Settings()));
		return block;
	}

	public static void register() {
		// Right after session tools — before weapons/eggs — so installables are easy to find.
		ItemGroupEvents.modifyEntriesEvent(ModItems.GROUP_KEY).register(entries -> {
			entries.addAfter(SessionControlItems.ENERGY_DIAL, CHECKPOINT);
			entries.addAfter(CHECKPOINT, DOCK);
			entries.addAfter(DOCK, COMBAT_ZONE);
			entries.addAfter(COMBAT_ZONE, NAV_NODE);
			entries.addAfter(NAV_NODE, OBJECTIVE);
		});
		DescentMod.LOGGER.info("Registered DRMD map blocks");
	}
}
