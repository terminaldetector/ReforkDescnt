package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.gravity.GravityFields;
import com.terminaldetector.drmd.world.gravity.TransientGravityFields;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public final class GravityWeaponGameTests implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void projectedGravityFieldPublishesMovesDownAndExpires(TestContext context) {
		var world = context.getWorld();
		Vec3d point = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(7, 5, 7)));
		UUID id = TransientGravityFields.place(world, point, new Vec3d(1, 0, 0),
				10, 1.35f, 3, "game test projector");
		context.assertTrue(id != null, "projector returned an id");
		GravityFields.Sample sample = GravityFields.sample(world, point);
		context.assertTrue(sample != null && sample.downDir().x > .99,
				"projector published its directional field immediately");
		context.assertTrue(TransientGravityFields.remainingTicks(id) == 3,
				"lease started with the requested lifetime");

		TransientGravityFields.tick(world.getServer());
		TransientGravityFields.tick(world.getServer());
		context.assertTrue(TransientGravityFields.remainingTicks(id) == 1,
				"lease decremented once per server tick");
		TransientGravityFields.tick(world.getServer());
		context.assertTrue(TransientGravityFields.remainingTicks(id) == 0,
				"expired lease left the bounded runtime map");
		context.assertTrue(GravityFields.all().stream().noneMatch(field -> field.id().equals(id)),
				"expired lease also removed the sampled field");
		context.complete();
	}
}
