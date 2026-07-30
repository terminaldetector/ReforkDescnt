package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.build.BuildToolItem;
import com.terminaldetector.drmd.world.soil.SixDSoilBlock;
import com.terminaldetector.drmd.world.trap.TrapBlocks;
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

/**
 * World-design blocks & tools — 6D Soil, traps, build tool.
 */
public final class ModWorldBlocks {
	public static final Block SIX_D_SOIL = registerBlock("six_d_soil",
			new SixDSoilBlock(AbstractBlock.Settings.copy(Blocks.GRASS_BLOCK).mapColor(MapColor.GREEN)));

	public static final Block HERMETIC_GATE = registerBlock("hermetic_gate",
			new TrapBlocks.HermeticGateBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).ticksRandomly()));

	public static final Block LASER_BARRIER = registerBlock("laser_barrier",
			new TrapBlocks.LaserBarrierBlock(AbstractBlock.Settings.copy(Blocks.GLASS).ticksRandomly().luminance(s -> 10)));

	public static final Block VOLUME_TURRET = registerBlock("volume_turret",
			new TrapBlocks.VolumeTurretBlock(AbstractBlock.Settings.copy(Blocks.DISPENSER).ticksRandomly()));

	public static final Block MAGNETIC_ANOMALY = registerBlock("magnetic_anomaly",
			new TrapBlocks.MagneticAnomalyBlock(AbstractBlock.Settings.copy(Blocks.LODESTONE).ticksRandomly()));

	public static final Block UNSTABLE_REACTOR = registerBlock("unstable_reactor",
			new TrapBlocks.UnstableReactorBlock(AbstractBlock.Settings.copy(Blocks.RESPAWN_ANCHOR).ticksRandomly()));

	public static Item BUILD_TOOL;

	private ModWorldBlocks() {}

	private static Block registerBlock(String id, Block block) {
		Identifier ident = Identifier.of(DescentMod.MOD_ID, id);
		Registry.register(Registries.BLOCK, ident, block);
		Registry.register(Registries.ITEM, ident, new BlockItem(block, new Item.Settings()));
		return block;
	}

	public static void register() {
		BUILD_TOOL = Registry.register(Registries.ITEM,
				Identifier.of(DescentMod.MOD_ID, "build_tool"),
				new BuildToolItem(new Item.Settings()));

		ItemGroupEvents.modifyEntriesEvent(ModItems.GROUP_KEY).register(entries -> {
			entries.add(SIX_D_SOIL);
			entries.add(HERMETIC_GATE);
			entries.add(LASER_BARRIER);
			entries.add(VOLUME_TURRET);
			entries.add(MAGNETIC_ANOMALY);
			entries.add(UNSTABLE_REACTOR);
			entries.add(BUILD_TOOL);
		});

		DescentMod.LOGGER.info("Registered 6DoF world-design blocks & build tool");
	}
}
