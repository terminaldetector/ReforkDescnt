package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.LocalOrientation;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LocalOrientationTest {
    @Test void finiteMagnitudeOverflowStillProducesTheCorrectUnitFrame() {
        var value=LocalOrientation.normalizeUp(new Vec3d(1e308,-1e308,5e307)).orElseThrow();
        assertEquals(1,value.length(),1e-15);assertTrue(value.distanceTo(new Vec3d(2,-2,1).normalize())<1e-15);
        assertEquals(new Vec3d(0,1,0),LocalOrientation.normalizeUp(new Vec3d(1e-310,-1e-310,0)).orElseThrow());
    }
    @Test void malformedLaterInputRetainsTheLastActualFrameRatherThanPoisoningIt() {
        var id=UUID.randomUUID();var first=new Vec3d(1,2,-3).normalize();
        try {
            LocalOrientation.setUp(id,first);
            for(var bad:new Vec3d[]{null,new Vec3d(Double.NaN,0,1),new Vec3d(0,Double.POSITIVE_INFINITY,1),new Vec3d(0,1,Double.NEGATIVE_INFINITY)}) {
                LocalOrientation.setUp(id,bad);assertTrue(LocalOrientation.getUp(id).distanceTo(first)<1e-15);
            }
        } finally {LocalOrientation.clear(id);}
    }
    @Test void explicitNeutralInputAndLifecycleClearStillRestoreWorldUp() {
        var id=UUID.randomUUID();
        try {
            LocalOrientation.setUp(id,new Vec3d(0,-1,0));LocalOrientation.setUp(id,Vec3d.ZERO);
            assertEquals(new Vec3d(0,1,0),LocalOrientation.getUp(id));
            LocalOrientation.setUp(id,new Vec3d(1,0,0));LocalOrientation.clear(id);
            assertEquals(new Vec3d(0,1,0),LocalOrientation.getUp(id));
        } finally {LocalOrientation.clear(id);}
    }
}
