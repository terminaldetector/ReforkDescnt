package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.ai.DroneSwarmAi;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class DroneSwarmGameTests implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
	public void denseDroneCloudUsesOneBoundedSnapshotPerTick(TestContext context) {
		var world = context.getWorld();
		Vec3d origin = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(7, 6, 7)));
		List<DroneEntity> drones = new ArrayList<>();
		try {
			for (int index = 0; index < 192; index++) {
				DroneEntity drone = ModEntities.DRONE.create(world);
				context.assertTrue(drone != null, "dense swarm drone created");
				double x = ((index & 7) - 3.5) * .08;
				double y = (((index >> 3) % 6) - 2.5) * .08;
				double z = (((index / 48) & 3) - 1.5) * .08;
				drone.setPosition(origin.add(x, y, z));
				drone.applyRole(AiRole.ASSAULT);
				world.spawnEntity(drone);
				drones.add(drone);
			}
			DroneSwarmAi.clear();
			var first = DroneSwarmAi.queryStats(drones.getFirst());
			context.assertTrue(first.indexedDrones() >= drones.size(),
					"world snapshot indexed the dense cloud");
			context.assertTrue(first.neighbours() == DroneSwarmAi.MAX_BOID_NEIGHBOURS,
					"dense cloud returns the bounded boid neighbourhood");
			context.assertTrue(first.examinedCandidates() <= DroneSwarmAi.MAX_BOID_CANDIDATES,
					"dense cloud never falls back to a full pair scan");
			long examined = 0;
			for (DroneEntity drone : drones) {
				var stats = DroneSwarmAi.queryStats(drone);
				context.assertTrue(stats.snapshotTick() == first.snapshotTick(),
						"the dense pass stays on one world snapshot");
				examined += stats.examinedCandidates();
			}
			context.assertTrue(examined <= (long) drones.size() * DroneSwarmAi.MAX_BOID_CANDIDATES,
					"the complete cloud pass is bounded by drones times candidate budget");

			DroneEntity late = ModEntities.DRONE.create(world);
			context.assertTrue(late != null, "late drone created");
			late.setPosition(origin);
			late.applyRole(AiRole.ASSAULT);
			world.spawnEntity(late);
			drones.add(late);
			var sameTick = DroneSwarmAi.queryStats(drones.get(1));
			context.assertTrue(sameTick.snapshotTick() == first.snapshotTick(),
					"both queries share one server-tick snapshot");
			context.assertTrue(sameTick.indexedDrones() == first.indexedDrones(),
					"a snapshot is not rebuilt once per individual drone");
		} finally {
			drones.forEach(DroneEntity::discard);
			DroneSwarmAi.clear();
		}
		context.complete();
	}
}
