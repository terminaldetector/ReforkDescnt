package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.entity.ModEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class ModEntityRenderers {
	private ModEntityRenderers() {}

	public static void register() {
		EntityRendererRegistry.register(ModEntities.PROJECTILE, ProjectileRenderer::new);
		EntityRendererRegistry.register(ModEntities.DRONE, DroneRenderer::new);
		EntityRendererRegistry.register(ModEntities.AIR_MINE, AirMineRenderer::new);
	}
}
