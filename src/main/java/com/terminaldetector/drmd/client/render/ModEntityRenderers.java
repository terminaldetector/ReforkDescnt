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
		EntityModelLayerRegistry.registerModelLayer(PyroShipRenderer.LAYER, PyroShipModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(DroneRenderer.LAYER, DescentDroneModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(ReactorDisplayRenderer.LAYER, ReactorCoreModel::getTexturedModelData);

		EntityRendererRegistry.register(ModEntities.PROJECTILE, ProjectileRenderer::new);
		EntityRendererRegistry.register(ModEntities.DRONE, DroneRenderer::new);
		EntityRendererRegistry.register(ModEntities.AIR_MINE, AirMineRenderer::new);
		EntityRendererRegistry.register(ModEntities.PYRO_SHIP, PyroShipRenderer::new);
		EntityRendererRegistry.register(ModEntities.REACTOR_DISPLAY, ReactorDisplayRenderer::new);
		EntityRendererRegistry.register(ModEntities.MEGA_WORM, MegaWormRenderer::new);
		EntityRendererRegistry.register(ModEntities.DRONE_SWARM, DroneSwarmRenderer::new);
		EntityRendererRegistry.register(ModEntities.REACTOR_KEEPER, ReactorKeeperRenderer::new);
	}
}
