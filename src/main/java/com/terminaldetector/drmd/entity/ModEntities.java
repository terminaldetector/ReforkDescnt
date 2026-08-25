package com.terminaldetector.drmd.entity;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.world.bombardment.AerialBombEntity;
import com.terminaldetector.drmd.world.mega.DroneSwarmEntity;
import com.terminaldetector.drmd.world.mega.ReactorKeeperEntity;
import com.terminaldetector.drmd.world.mega.SkyUfoEntity;
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

	public static final EntityType<DroneSwarmEntity> DRONE_SWARM = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(DescentMod.MOD_ID, "drone_swarm"),
			EntityType.Builder.<DroneSwarmEntity>create(DroneSwarmEntity::new, SpawnGroup.MISC)
					.dimensions(4.0f, 4.0f)
					.maxTrackingRange(256)
					.trackingTickInterval(5)
					.build()
	);

	public static final EntityType<ReactorKeeperEntity> REACTOR_KEEPER = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(DescentMod.MOD_ID, "reactor_keeper"),
			EntityType.Builder.<ReactorKeeperEntity>create(ReactorKeeperEntity::new, SpawnGroup.MONSTER)
					.dimensions(4.5f, 4.5f)
					.maxTrackingRange(160)
					.trackingTickInterval(2)
					.build()
	);

	public static final EntityType<AerialBombEntity> AERIAL_BOMB = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(DescentMod.MOD_ID, "aerial_bomb"),
			EntityType.Builder.<AerialBombEntity>create(AerialBombEntity::new, SpawnGroup.MISC)
					.dimensions(0.55f, 0.55f)
					.maxTrackingRange(256)
					.trackingTickInterval(1)
					.build()
	);

	public static final EntityType<com.terminaldetector.drmd.world.end.EndReactorBossEntity> END_REACTOR_BOSS = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(DescentMod.MOD_ID, "end_reactor_boss"),
			EntityType.Builder.<com.terminaldetector.drmd.world.end.EndReactorBossEntity>create(
							com.terminaldetector.drmd.world.end.EndReactorBossEntity::new, SpawnGroup.MONSTER)
					.dimensions(3.5f, 3.5f)
					.maxTrackingRange(160)
					.trackingTickInterval(2)
					.build()
	);

	public static final EntityType<SkyUfoEntity> SKY_UFO = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(DescentMod.MOD_ID, "sky_ufo"),
			// Covers the real hull footprint (SkyUfoHull.MAJOR=11 -> 23 wide, MINOR=4 -> 10 tall) now
			// that the entity itself provides collision while virtual (see SkyUfoEntity.getBoundingBox,
			// which overrides this declarative box with the hull's actual non-symmetric vertical profile
			// — this call still matters for the horizontal extent and for systems that read type-level
			// dimensions directly rather than a live instance's box).
			EntityType.Builder.<SkyUfoEntity>create(SkyUfoEntity::new, SpawnGroup.MISC)
					.dimensions(23.0f, 10.0f)
					.maxTrackingRange(256)
					.trackingTickInterval(2)
					.build()
	);

	// --- hybrid-cyberpunk ground & sentry roster ---

	public static final EntityType<com.terminaldetector.drmd.entity.mob.TripodEntity> TRIPOD = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(DescentMod.MOD_ID, "tripod"),
			EntityType.Builder.<com.terminaldetector.drmd.entity.mob.TripodEntity>create(
							com.terminaldetector.drmd.entity.mob.TripodEntity::new, SpawnGroup.MONSTER)
					.dimensions(3.2f, 6.0f)
					.maxTrackingRange(96)
					.build()
	);

	public static final EntityType<com.terminaldetector.drmd.entity.mob.ScannerEntity> SCANNER = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(DescentMod.MOD_ID, "scanner"),
			EntityType.Builder.<com.terminaldetector.drmd.entity.mob.ScannerEntity>create(
							com.terminaldetector.drmd.entity.mob.ScannerEntity::new, SpawnGroup.MONSTER)
					.dimensions(1.0f, 1.0f)
					.maxTrackingRange(96)
					.trackingTickInterval(2)
					.build()
	);

	public static final EntityType<com.terminaldetector.drmd.entity.mob.SpiderTurretEntity> SPIDER_TURRET = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(DescentMod.MOD_ID, "spider_turret"),
			EntityType.Builder.<com.terminaldetector.drmd.entity.mob.SpiderTurretEntity>create(
							com.terminaldetector.drmd.entity.mob.SpiderTurretEntity::new, SpawnGroup.MONSTER)
					.dimensions(1.6f, 1.4f)
					.maxTrackingRange(96)
					.build()
	);

	/** Oblivion Seeker (рыскатель) — dark spherical End-faction pre-boss. */
	public static final EntityType<com.terminaldetector.drmd.entity.mob.OblivionSeekerEntity> OBLIVION_SEEKER = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(DescentMod.MOD_ID, "oblivion_seeker"),
			EntityType.Builder.<com.terminaldetector.drmd.entity.mob.OblivionSeekerEntity>create(
							com.terminaldetector.drmd.entity.mob.OblivionSeekerEntity::new, SpawnGroup.MONSTER)
					.dimensions(1.35f, 1.35f)
					.maxTrackingRange(128)
					.trackingTickInterval(2)
					.build()
	);

	public static final EntityType<LaserBarrierCartEntity> LASER_BARRIER_CART = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(DescentMod.MOD_ID, "laser_barrier_cart"),
			EntityType.Builder.<LaserBarrierCartEntity>create(LaserBarrierCartEntity::new, SpawnGroup.MISC)
					.dimensions(0.98f, 0.7f)
					.maxTrackingRange(80)
					.trackingTickInterval(2)
					.build()
	);

	private ModEntities() {}

	public static void register() {
		FabricDefaultAttributeRegistry.register(DRONE, DroneEntity.createDroneAttributes());
		FabricDefaultAttributeRegistry.register(TRIPOD,
				com.terminaldetector.drmd.entity.mob.TripodEntity.createTripodAttributes());
		FabricDefaultAttributeRegistry.register(SCANNER,
				com.terminaldetector.drmd.entity.mob.ScannerEntity.createScannerAttributes());
		FabricDefaultAttributeRegistry.register(SPIDER_TURRET,
				com.terminaldetector.drmd.entity.mob.SpiderTurretEntity.createSpiderTurretAttributes());
		FabricDefaultAttributeRegistry.register(OBLIVION_SEEKER,
				com.terminaldetector.drmd.entity.mob.OblivionSeekerEntity.createSeekerAttributes());
		FabricDefaultAttributeRegistry.register(AIR_MINE, AirMineEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(REACTOR_KEEPER, ReactorKeeperEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(END_REACTOR_BOSS,
				com.terminaldetector.drmd.world.end.EndReactorBossEntity.createAttributes());
		DescentMod.LOGGER.info("Registered DRMD entities (End boss + Sky UFO + laser cart)");
	}
}
