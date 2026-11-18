package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.TeleportFlyerGoal;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TeleportCandidateBasisTest {
    @Test void verticalAndRolledFlightRetainsFiveDistinctFiniteBoundedEndpoints(){
        for(var heading:new Vec3d[]{new Vec3d(0,1,0),new Vec3d(0,-1,0),new Vec3d(1,0,0),new Vec3d(.1,.99,.05).normalize()})
            for(float bank:new float[]{0,45,90,179}){
                var points=TeleportFlyerGoal.candidates(heading,bank,8);assertEquals(5,points.size());
                for(int i=0;i<5;i++){assertEquals(8,points.get(i).length(),1e-9);if(i<4)assertEquals(0,points.get(i).dotProduct(heading),1e-9);
                    for(int j=0;j<i;j++)assertTrue(points.get(i).squaredDistanceTo(points.get(j))>1);}
                assertEquals(-8,points.get(4).dotProduct(heading),1e-9);
            }
    }
    @Test void invalidPoseOrRadiusDoesNotInventAnEndpoint(){
        assertTrue(TeleportFlyerGoal.candidates(Vec3d.ZERO,0,8).isEmpty());
        assertTrue(TeleportFlyerGoal.candidates(new Vec3d(Double.NaN,0,1),0,8).isEmpty());
        assertTrue(TeleportFlyerGoal.candidates(new Vec3d(0,0,1),Float.NaN,8).isEmpty());
        assertTrue(TeleportFlyerGoal.candidates(new Vec3d(0,0,1),0,17).isEmpty());
    }
}
