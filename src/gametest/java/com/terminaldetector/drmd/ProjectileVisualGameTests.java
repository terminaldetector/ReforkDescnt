package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Vec3d;

/** Server-side metadata/range regressions; client motion is checked separately in shootcheck. */
public final class ProjectileVisualGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void visualVelocityAndRangeDoNotUseVanillaSmallProjectileLimits(TestContext context) {
        var shot = ModEntities.PROJECTILE.create(context.getWorld());
        context.assertTrue(shot != null, "projectile created");
        Vec3d velocity = new Vec3d(4.875, -8, 12);
        shot.setVelocity(velocity);
        shot.synchronizeVisualVelocity();
        context.assertTrue(shot.getVisualVelocity().squaredDistanceTo(velocity) < 1e-10,
                "tracked vector retains components beyond the vanilla packet clamp");
        shot.setVelocity(new Vec3d(-12, 8, -4.875));
        shot.synchronizeVisualVelocity();
        context.assertTrue(shot.getVisualVelocity().squaredDistanceTo(shot.getVelocity()) < 1e-10,
                "homing/bounce updates replace the tracked vector");
        context.assertTrue(shot.shouldRender(100 * 100), "a tiny shot stays visible at 100 blocks");
        context.assertTrue(!shot.shouldRender(1024 * 1024), "visual range remains bounded");
        context.complete();
    }
}
