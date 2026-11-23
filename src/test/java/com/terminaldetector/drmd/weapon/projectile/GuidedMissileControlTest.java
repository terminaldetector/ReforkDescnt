package com.terminaldetector.drmd.weapon.projectile;

import com.terminaldetector.drmd.flight.ShipAttitude;
import com.terminaldetector.drmd.weapon.projectile.GuidedMissileControl.Commands;
import com.terminaldetector.drmd.weapon.projectile.GuidedMissileControl.Input;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidedMissileControlTest {
    @Test
    void turnsThroughVerticalWithoutChangingLocalUpConvention() {
        var attitude = new ShipAttitude();
        Vec3d initialRight = attitude.right();
        for (int tick = 0; tick < 18; tick++) GuidedMissileControl.turn(attitude, 0, 1, 200);

        assertEquals(-1, attitude.forward().z, 1e-8);
        assertEquals(-1, attitude.up().y, 1e-8);
        assertEquals(1, initialRight.dotProduct(attitude.right()), 1e-8);
    }

    @Test
    void combinedAxesSpendOneTurnBudget() {
        var attitude = new ShipAttitude();
        GuidedMissileControl.turn(attitude, -1, 1, 200);

        assertEquals(10, Math.toDegrees(Math.acos(attitude.forward().z)), 1e-6);
        assertTrue(attitude.forward().x < 0);
        assertTrue(attitude.forward().y > 0);
    }

    @Test
    void admitsOnlyFreshBoundedCommandsAndConsumesEachTurnOnce() {
        UUID token = UUID.randomUUID();
        var commands = new Commands(token, 10);
        assertFalse(commands.accept(new Input(UUID.randomUUID(), 0, false, 1, 0), 11));
        assertFalse(commands.accept(new Input(token, 0, false, Float.NaN, 0), 11));
        assertFalse(commands.accept(new Input(token, 0, false, 2, 0), 11));

        var first = new Input(token, 0, false, 0.5f, -0.75f);
        assertTrue(commands.accept(first, 11));
        assertFalse(commands.accept(new Input(token, 1, false, 1, 0), 11));
        assertSame(first, commands.take());
        assertNull(commands.take());
        assertFalse(commands.accept(first, 12));

        assertTrue(commands.accept(new Input(token, 1, false, 0, 1), 12));
        assertTrue(commands.accept(new Input(token, 2, true, 0, 0), 12));
        assertNull(commands.take());
    }
}
