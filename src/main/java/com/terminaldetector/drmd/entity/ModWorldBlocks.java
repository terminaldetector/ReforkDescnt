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

	public static final Block UNSTABLE_REACTOR = registerBlock("unstable_reactor",
			new TrapBlocks.UnstableReactorBlock(AbstractBlock.Settings.copy(Blocks.RESPAWN_ANCHOR).ticksRandomly()));

	public static final Block GRAVITY_GENERATOR = registerBlock("gravity_generator",
			new com.terminaldetector.drmd.world.gravity.GravityGeneratorBlock(
					AbstractBlock.Settings.copy(Blocks.LODESTONE).luminance(s -> 8).strength(4f)));

	public static final Block DRILL_RIG = registerBlock("drill_rig",
			new com.terminaldetector.drmd.world.engineer.DrillRigBlock(
					AbstractBlock.Settings.copy(Blocks.BLAST_FURNACE).luminance(s -> s.get(com.terminaldetector.drmd.world.engineer.DrillRigBlock.ACTIVE) ? 12 : 5).strength(4.5f).nonOpaque()));

	public static final Block TUNNEL_DRILL_RIG = registerBlock("tunnel_drill_rig",
			new com.terminaldetector.drmd.world.engineer.TunnelDrillRigBlock(
					AbstractBlock.Settings.copy(Blocks.BLAST_FURNACE).luminance(s -> s.get(com.terminaldetector.drmd.world.engineer.TunnelDrillRigBlock.ACTIVE) ? 14 : 6).strength(5f).nonOpaque()));

	public static final Block GRAVITY_TORCH = registerBlock("gravity_torch",
			new com.terminaldetector.drmd.world.gravity.GravityTorchBlock(
					AbstractBlock.Settings.copy(Blocks.TORCH).luminance(s -> 12).ticksRandomly().nonOpaque()));

	public static Item BUILD_TOOL;
	public static Item CONSTRUCTION_LASER;
	public static Item REPAIR_LASER;
	public static Item MINING_LASER;
	public static Item TUNNEL_LASER;
	public static Item GRAVITY_SCANNER;
	public static Item CYCLIC_LASER_KIT;

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
		TUNNEL_LASER = Registry.register(Registries.ITEM,
				Identifier.of(DescentMod.MOD_ID, "tunnel_laser"),
				new com.terminaldetector.drmd.world.engineer.EngineerTools.TunnelLaserItem(new Item.Settings()));
		GRAVITY_SCANNER = Registry.register(Registries.ITEM,
				Identifier.of(DescentMod.MOD_ID, "gravity_scanner"),
				new com.terminaldetector.drmd.world.engineer.EngineerTools.GravityScannerItem(new Item.Settings()));
		CYCLIC_LASER_KIT = Registry.register(Registries.ITEM,
				Identifier.of(DescentMod.MOD_ID, "cyclic_laser_kit"),
				new com.terminaldetector.drmd.world.build.CyclicLaserKitItem(new Item.Settings()));

		ItemGroupEvents.modifyEntriesEvent(ModItems.GROUP_KEY).register(entries -> {
			entries.add(SIX_D_SOIL);
			entries.add(HERMETIC_GATE);
			entries.add(LASER_BARRIER);
			entries.add(VOLUME_TURRET);
			entries.add(LASER_TURRET);
			entries.add(PLASMA_TURRET);
			entries.add(POINT_DEFENSE_TURRET);
			entries.add(MAGNETIC_ANOMALY);
			entries.add(UNSTABLE_REACTOR);
			entries.add(GRAVITY_GENERATOR);
			entries.add(GRAVITY_TORCH);
			entries.add(DRILL_RIG);
			entries.add(TUNNEL_DRILL_RIG);
			entries.add(BUILD_TOOL);
			entries.add(CONSTRUCTION_LASER);
			entries.add(REPAIR_LASER);
			entries.add(MINING_LASER);
			entries.add(TUNNEL_LASER);
			entries.add(GRAVITY_SCANNER);
			entries.add(CYCLIC_LASER_KIT);
		});

		DescentMod.LOGGER.info("Registered Phase3 world blocks & engineer tools");
	}
}
