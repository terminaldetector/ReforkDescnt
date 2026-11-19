package com.terminaldetector.drmd;

import com.terminaldetector.drmd.weapon.projectile.ProjectileTracerBudget;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectileTracerBudgetTest {
    @Test
    void distantVolleyLeavesRoomForLocalTracers() {
        var budget = new ProjectileTracerBudget();
        budget.begin(Vec3d.ZERO);
        var distant = new Vec3d(32, 0, 0);
        for (int i = 0; i < 64; i++) assertEquals(8, budget.take(distant, Vec3d.ZERO, 48));
        assertEquals(0, budget.take(distant, Vec3d.ZERO, 1));
        for (int i = 0; i < 16; i++) assertEquals(16, budget.take(Vec3d.ZERO, Vec3d.ZERO, 16));
        assertEquals(0, budget.take(Vec3d.ZERO, Vec3d.ZERO, 1));
        assertEquals(ProjectileTracerBudget.MAX_PARTICLES_PER_TICK, budget.sample().admitted());

        budget.begin(Vec3d.ZERO);
        assertEquals(0, budget.sample().admitted());
        assertEquals(3, budget.take(new Vec3d(80, 0, 0), Vec3d.ZERO, 30));
    }

    @Test
    void fastSegmentKeepsItsNearBudgetWhenTheHeadIsOutsideTheViewRange() {
        var budget = new ProjectileTracerBudget();
        budget.begin(Vec3d.ZERO);
        var head = new Vec3d(160, 0, 0);
        assertEquals(0, budget.take(head, Vec3d.ZERO, 48));
        assertEquals(48, budget.take(head, new Vec3d(320, 0, 0), 48));
        budget.begin(null);
        assertEquals(0, budget.take(Vec3d.ZERO, Vec3d.ZERO, 48));
    }
}
