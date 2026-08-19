package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.world.gravity.GravityGeneratorBlockEntity;
import com.terminaldetector.drmd.world.locator.LocatorCoreBlockEntity;
import com.terminaldetector.drmd.world.soil.SixDSoilBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlockEntities {
	public static BlockEntityType<SixDSoilBlockEntity> SIX_D_SOIL;
	public static BlockEntityType<GravityGeneratorBlockEntity> GRAVITY_GENERATOR;
	public static BlockEntityType<LocatorCoreBlockEntity> LOCATOR_CORE;
	public static BlockEntityType<com.terminaldetector.drmd.world.micro.CarvedBlockEntity> CARVED;
	public static BlockEntityType<com.terminaldetector.drmd.world.portal.mirror.MirrorBlockEntity> MIRROR;
	public static BlockEntityType<com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlockEntity> CHARGED_MIRROR;

	private ModBlockEntities() {}

	public static void register() {
		SIX_D_SOIL = Registry.register(Registries.BLOCK_ENTITY_TYPE,
				Identifier.of(DescentMod.MOD_ID, "six_d_soil"),
				BlockEntityType.Builder.create(SixDSoilBlockEntity::new, ModWorldBlocks.SIX_D_SOIL).build());
		GRAVITY_GENERATOR = Registry.register(Registries.BLOCK_ENTITY_TYPE,
				Identifier.of(DescentMod.MOD_ID, "gravity_generator"),
				BlockEntityType.Builder.create(GravityGeneratorBlockEntity::new, ModWorldBlocks.GRAVITY_GENERATOR).build());
		LOCATOR_CORE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
				Identifier.of(DescentMod.MOD_ID, "locator_core"),
				BlockEntityType.Builder.create(LocatorCoreBlockEntity::new, ModWorldBlocks.LOCATOR_CORE).build());
		CARVED = Registry.register(Registries.BLOCK_ENTITY_TYPE,
				Identifier.of(DescentMod.MOD_ID, "carved"),
				BlockEntityType.Builder.create(
						com.terminaldetector.drmd.world.micro.CarvedBlockEntity::new,
						ModWorldBlocks.CARVED).build());
		MIRROR = Registry.register(Registries.BLOCK_ENTITY_TYPE,
				Identifier.of(DescentMod.MOD_ID, "mirror"),
				BlockEntityType.Builder.create(
						com.terminaldetector.drmd.world.portal.mirror.MirrorBlockEntity::new,
						ModWorldBlocks.MIRROR).build());
		CHARGED_MIRROR = Registry.register(Registries.BLOCK_ENTITY_TYPE,
				Identifier.of(DescentMod.MOD_ID, "charged_mirror"),
				BlockEntityType.Builder.create(
						com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlockEntity::new,
						ModWorldBlocks.CHARGED_MIRROR).build());
		DescentMod.LOGGER.info("Registered DRMD block entities");
	}
}
