package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.contraption.boarding.FlightVolumeInterest;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlightVolumeInterestTest {
    @Test void realVelocityRequestsAheadAcrossBothSignedHeightBoundaries(){
        var up=FlightVolumeInterest.plan(new Vec3d(40.5,254.35,40.5),new Vec3d(0,41.5,0),.6,1.8,-16384,16384);
        assertFalse(up.isEmpty());assertTrue(up.stream().anyMatch(c->c.maxY()>=17));
        var down=FlightVolumeInterest.plan(new Vec3d(40.5,-510,40.5),new Vec3d(0,-41.5,0),.6,1.8,-16384,16384);
        assertTrue(down.stream().anyMatch(c->c.minY()<=-34));
    }
    @Test void administrativeVelocityCannotAllocateAnUnboundedLeaseOrLeaveRegistryHeight(){
        for(int x=0;x<16;x++)for(int y=0;y<16;y++){
            var plan=FlightVolumeInterest.plan(new Vec3d(x,16380-y,15-x),new Vec3d(1e9,1e9,-1e9),.6,1.8,-16384,16384);
            assertTrue(plan.size()<=FlightVolumeInterest.MAX_COLUMNS);
            assertTrue(plan.stream().mapToLong(c->(long)c.maxY()-c.minY()+1).sum()<=FlightVolumeInterest.MAX_CUBES);
            assertTrue(plan.stream().allMatch(c->c.maxY()<1024&&c.minY()>=-1024));
        }
        assertTrue(FlightVolumeInterest.plan(Vec3d.ZERO,new Vec3d(Double.NaN,0,0),.6,1.8,-16384,16384).isEmpty());
    }
}
