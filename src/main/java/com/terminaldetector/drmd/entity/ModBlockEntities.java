package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.world.soil.SixDSoilBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlockEntities {
	public static BlockEntityType<SixDSoilBlockEntity> SIX_D_SOIL;

	private ModBlockEntities() {}

	public static void register() {
		SIX_D_SOIL = Registry.register(Registries.BLOCK_ENTITY_TYPE,
				Identifier.of(DescentMod.MOD_ID, "six_d_soil"),
				BlockEntityType.Builder.create(SixDSoilBlockEntity::new, ModWorldBlocks.SIX_D_SOIL).build());
		DescentMod.LOGGER.info("Registered DRMD block entities");
	}
}
