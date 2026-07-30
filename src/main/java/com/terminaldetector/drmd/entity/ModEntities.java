package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.DescentMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
	public static final EntityType<ProjectileEntity> PROJECTILE = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(DescentMod.MOD_ID, "projectile"),
			EntityType.Builder.<ProjectileEntity>create(ProjectileEntity::new, SpawnGroup.MISC)
					.dimensions(0.35f, 0.35f)
					.maxTrackingRange(128)
					.trackingTickInterval(1)
					.build()
	);

	public static final EntityType<DroneEntity> DRONE = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(DescentMod.MOD_ID, "drone"),
			EntityType.Builder.<DroneEntity>create(DroneEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.9f, 0.9f)
					.maxTrackingRange(96)
					.build()
	);

	public static final EntityType<AirMineEntity> AIR_MINE = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(DescentMod.MOD_ID, "air_mine"),
			EntityType.Builder.<AirMineEntity>create(AirMineEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6f, 0.6f)
					.maxTrackingRange(64)
					.build()
	);

	public static final EntityType<PyroShipEntity> PYRO_SHIP = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(DescentMod.MOD_ID, "pyro_ship"),
			EntityType.Builder.<PyroShipEntity>create(PyroShipEntity::new, SpawnGroup.MISC)
					.dimensions(2.2f, 1.2f)
					.maxTrackingRange(96)
					.trackingTickInterval(1)
					.build()
	);

	public static final EntityType<ReactorDisplayEntity> REACTOR_DISPLAY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(DescentMod.MOD_ID, "reactor_display"),
			EntityType.Builder.<ReactorDisplayEntity>create(ReactorDisplayEntity::new, SpawnGroup.MISC)
					.dimensions(1.5f, 1.5f)
					.maxTrackingRange(64)
					.build()
	);

	private ModEntities() {}

	public static void register() {
		FabricDefaultAttributeRegistry.register(DRONE, DroneEntity.createDroneAttributes());
		FabricDefaultAttributeRegistry.register(AIR_MINE, AirMineEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(PYRO_SHIP, PyroShipEntity.createAttributes());
		DescentMod.LOGGER.info("Registered DRMD entities (drones, mines, pyro ship, reactor)");
	}
}
