package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.build.BuildToolItem;
import com.terminaldetector.drmd.world.soil.SixDSoilBlock;
import com.terminaldetector.drmd.world.trap.DefenseTurrets;
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
			new TrapBlocks.VolumeTurretBlock(AbstractBlock.Settings.copy(Blocks.DISPENSER).ticksRandomly().luminance(s -> 6)));

	public static final Block LASER_TURRET = registerBlock("laser_turret",
			new DefenseTurrets.LaserTurretBlock(AbstractBlock.Settings.copy(Blocks.DISPENSER).ticksRandomly().luminance(s -> 8).strength(4f)));

	public static final Block PLASMA_TURRET = registerBlock("plasma_turret",
			new DefenseTurrets.PlasmaTurretBlock(AbstractBlock.Settings.copy(Blocks.DISPENSER).ticksRandomly().luminance(s -> 9).strength(4f)));

	public static final Block POINT_DEFENSE_TURRET = registerBlock("point_defense_turret",
			new DefenseTurrets.PointDefenseTurretBlock(AbstractBlock.Settings.copy(Blocks.DISPENSER).ticksRandomly().luminance(s -> 7).strength(3.5f)));

	public static final Block MAGNETIC_ANOMALY = registerBlock("magnetic_anomaly",
			new TrapBlocks.MagneticAnomalyBlock(AbstractBlock.Settings.copy(Blocks.LODESTONE).ticksRandomly()));

	// Explicit luminance — do not rely on RESPAWN_ANCHOR CHARGES property.
	public static final Block UNSTABLE_REACTOR = registerBlock("unstable_reactor",
			new TrapBlocks.UnstableReactorBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)
					.ticksRandomly().luminance(s -> 12).strength(3.5f).mapColor(MapColor.RED)));

	public static final Block GRAVITY_GENERATOR = registerBlock("gravity_generator",
			new com.terminaldetector.drmd.world.gravity.GravityGeneratorBlock(
					AbstractBlock.Settings.copy(Blocks.LODESTONE).luminance(s -> 8).strength(4f)));

	public static final Block DRILL_RIG = registerBlock("drill_rig",
			new com.terminaldetector.drmd.world.engineer.DrillRigBlock(
					AbstractBlock.Settings.copy(Blocks.BLAST_FURNACE).luminance(s -> 7).strength(4.5f)));

	public static final Block GRAVITY_TORCH = registerBlock("gravity_torch",
			new com.terminaldetector.drmd.world.gravity.GravityTorchBlock(
					AbstractBlock.Settings.copy(Blocks.TORCH).luminance(s -> 12).ticksRandomly().nonOpaque()));

	public static Item BUILD_TOOL;
	public static Item CONSTRUCTION_LASER;
	public static Item REPAIR_LASER;
	public static Item MINING_LASER;
	public static Item GRAVITY_SCANNER;

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
		CONSTRUCTION_LASER = Registry.register(Registries.ITEM,
				Identifier.of(DescentMod.MOD_ID, "construction_laser"),
				new com.terminaldetector.drmd.world.engineer.EngineerTools.ConstructionLaserItem(new Item.Settings()));
		REPAIR_LASER = Registry.register(Registries.ITEM,
				Identifier.of(DescentMod.MOD_ID, "repair_laser"),
				new com.terminaldetector.drmd.world.engineer.EngineerTools.RepairLaserItem(new Item.Settings()));
		MINING_LASER = Registry.register(Registries.ITEM,
				Identifier.of(DescentMod.MOD_ID, "mining_laser"),
				new com.terminaldetector.drmd.world.engineer.EngineerTools.MiningLaserItem(new Item.Settings()));
		GRAVITY_SCANNER = Registry.register(Registries.ITEM,
				Identifier.of(DescentMod.MOD_ID, "gravity_scanner"),
				new com.terminaldetector.drmd.world.engineer.EngineerTools.GravityScannerItem(new Item.Settings()));

		ItemGroupEvents.modifyEntriesEvent(ModItems.GROUP_KEY).register(entries -> {
			entries.addAfter(ModBlocks.OBJECTIVE, SIX_D_SOIL);
			entries.addAfter(SIX_D_SOIL, HERMETIC_GATE);
			entries.addAfter(HERMETIC_GATE, LASER_BARRIER);
			entries.addAfter(LASER_BARRIER, VOLUME_TURRET);
			entries.addAfter(VOLUME_TURRET, LASER_TURRET);
			entries.addAfter(LASER_TURRET, PLASMA_TURRET);
			entries.addAfter(PLASMA_TURRET, POINT_DEFENSE_TURRET);
			entries.addAfter(POINT_DEFENSE_TURRET, MAGNETIC_ANOMALY);
			entries.addAfter(MAGNETIC_ANOMALY, UNSTABLE_REACTOR);
			entries.addAfter(UNSTABLE_REACTOR, GRAVITY_GENERATOR);
			entries.addAfter(GRAVITY_GENERATOR, GRAVITY_TORCH);
			entries.addAfter(GRAVITY_TORCH, DRILL_RIG);
			entries.addAfter(DRILL_RIG, BUILD_TOOL);
			entries.addAfter(BUILD_TOOL, CONSTRUCTION_LASER);
			entries.addAfter(CONSTRUCTION_LASER, REPAIR_LASER);
			entries.addAfter(REPAIR_LASER, MINING_LASER);
			entries.addAfter(MINING_LASER, GRAVITY_SCANNER);
		});

		DescentMod.LOGGER.info("Registered Phase3 world blocks & engineer tools");
	}
}
