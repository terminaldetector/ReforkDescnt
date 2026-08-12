package com.terminaldetector.drmd.world.structure;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Carries riders/occupants along with a moving structure — promoted near-verbatim from
 * {@code SkyUfoHull.collectInterior}/{@code shiftEntities} under generic names, since "carry entities
 * along on a move" isn't a Sky UFO-specific concern. Unlike the original, this doesn't exclude any
 * particular entity type by class — only {@code exclude} itself (typically the structure's own owning
 * entity, if it has one) and liveness are filtered here; a specific consumer that needs to exclude a
 * whole category can filter the returned list further.
 */
public final class StructureOccupants {
	private StructureOccupants() {}

	public static List<Entity> collect(ServerWorld world, StructureInstance instance, Entity exclude) {
		return world.getOtherEntities(exclude, instance.interior(), Entity::isAlive);
	}

	public static void shiftBy(List<Entity> entities, Vec3d delta) {
		for (Entity e : entities) {
			e.setPosition(e.getPos().add(delta));
			e.velocityModified = true;
		}
	}
}
