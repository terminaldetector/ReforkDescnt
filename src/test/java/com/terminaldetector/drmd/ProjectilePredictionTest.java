package com.terminaldetector.drmd;

import com.terminaldetector.drmd.weapon.projectile.ProjectilePrediction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectilePredictionTest {
    @Test void fullSpeedIsPreservedBetweenServerPositions() {
        var prediction = new ProjectilePrediction();
        Vec3d start = new Vec3d(3, 4, 5), velocity = new Vec3d(0, 0, -8);
        prediction.accept(start);
        assertEquals(start.add(velocity), prediction.advance(start, velocity));
    }

    @Test void missingServerUpdatesCannotProduceUnlimitedFlight() {
        var prediction = new ProjectilePrediction();
        Vec3d start = new Vec3d(17, 206, -60), velocity = new Vec3d(0, 0, -.06);
        Vec3d current = start;
        prediction.accept(start);
        for (int tick = 0; tick < 200; tick++) current = prediction.advance(current, velocity);
        assertEquals(start.add(velocity.multiply(ProjectilePrediction.MAX_LEAD_TICKS)), current);
    }

    @Test void newServerPositionResetsThePredictionWindow() {
        var prediction = new ProjectilePrediction();
        Vec3d velocity = new Vec3d(8, 0, 0);
        prediction.accept(Vec3d.ZERO);
        Vec3d current = Vec3d.ZERO;
        for (int i = 0; i < 20; i++) current = prediction.advance(current, velocity);
        Vec3d authoritative = new Vec3d(80, 2, 4);
        prediction.accept(authoritative);
        assertEquals(authoritative.add(velocity), prediction.advance(current, velocity));
    }

    @Test void zeroVelocityAndVerticalTravelAreStable() {
        var prediction = new ProjectilePrediction();
        Vec3d start = new Vec3d(1, 2, 3);
        assertEquals(start, prediction.advance(start, Vec3d.ZERO));
        prediction.accept(start);
        assertEquals(new Vec3d(1, 10, 3), prediction.advance(start, new Vec3d(0, 8, 0)));
    }
}
