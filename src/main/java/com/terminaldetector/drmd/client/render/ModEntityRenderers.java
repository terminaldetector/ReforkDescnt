package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.model.DescentDroneModel;
import com.terminaldetector.drmd.entity.model.PyroShipModel;
import com.terminaldetector.drmd.entity.model.ReactorCoreModel;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class ModEntityRenderers {
	private ModEntityRenderers() {}

	public static void register() {
		// The carved block has no baked model — its renderer draws the block it used to be, once per
		// box of the shape the damage left.
		net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererFactories.register(
				com.terminaldetector.drmd.entity.ModBlockEntities.CARVED, CarvedBlockRenderer::new);

		EntityModelLayerRegistry.registerModelLayer(PyroShipRenderer.LAYER, PyroShipModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(DroneRenderer.LAYER, DescentDroneModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(ReactorDisplayRenderer.LAYER, ReactorCoreModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(TripodRenderer.LAYER,
				com.terminaldetector.drmd.entity.model.TripodModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(ScannerRenderer.LAYER,
				com.terminaldetector.drmd.entity.model.ScannerModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(SpiderTurretRenderer.LAYER,
				com.terminaldetector.drmd.entity.model.SpiderTurretModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(OblivionSeekerRenderer.LAYER,
				com.terminaldetector.drmd.entity.model.OblivionSeekerModel::getTexturedModelData);

		EntityRendererRegistry.register(ModEntities.PROJECTILE, ProjectileRenderer::new);
		EntityRendererRegistry.register(ModEntities.DRONE, DroneRenderer::new);
		EntityRendererRegistry.register(ModEntities.AIR_MINE, AirMineRenderer::new);
		EntityRendererRegistry.register(ModEntities.PYRO_SHIP, PyroShipRenderer::new);
		EntityRendererRegistry.register(ModEntities.REACTOR_DISPLAY, ReactorDisplayRenderer::new);
		EntityRendererRegistry.register(ModEntities.MEGA_WORM, MegaWormRenderer::new);
		EntityRendererRegistry.register(ModEntities.DRONE_SWARM, DroneSwarmRenderer::new);
		EntityRendererRegistry.register(ModEntities.REACTOR_KEEPER, ReactorKeeperRenderer::new);
		EntityRendererRegistry.register(ModEntities.AERIAL_BOMB, AerialBombRenderer::new);
		EntityRendererRegistry.register(ModEntities.END_REACTOR_BOSS, EndReactorBossRenderer::new);
		EntityRendererRegistry.register(ModEntities.SKY_UFO, SkyUfoRenderer::new);
		EntityRendererRegistry.register(ModEntities.TRIPOD, TripodRenderer::new);
		EntityRendererRegistry.register(ModEntities.SCANNER, ScannerRenderer::new);
		EntityRendererRegistry.register(ModEntities.SPIDER_TURRET, SpiderTurretRenderer::new);
		EntityRendererRegistry.register(ModEntities.OBLIVION_SEEKER, OblivionSeekerRenderer::new);
		EntityRendererRegistry.register(ModEntities.LASER_BARRIER_CART, ctx ->
				new net.minecraft.client.render.entity.MinecartEntityRenderer<>(
						ctx, net.minecraft.client.render.entity.model.EntityModelLayers.MINECART));
	}
}
