package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.gravity.*;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LocalTargetVolumeTest {
    @Test void ordinaryNativeSearchBandIsBitExact(){
        var body=new Box(1,2,3,2,4,4);
        assertEquals(body.expand(16,4,16),LocalTargetVolume.expand(body,new Vec3d(-0.0,1,-0.0),16));
    }
    @Test void wallTangentTargetIsIncludedWithoutIncreasingTheNormalRange(){
        var body=new Box(9.02,2203.7,15.7,10.97,2204.3,16.3);
        var target=new Box(9.6,2212,15.7,10.2,2214,16.3);
        var old=body.expand(16,4,16);var actual=LocalTargetVolume.expand(body,new Vec3d(1,0,0),16);
        assertFalse(old.intersects(target));assertTrue(actual.intersects(target));
        assertEquals(body.minX-4,actual.minX,1e-10);assertEquals(body.maxX+4,actual.maxX,1e-10);
        assertFalse(actual.intersects(new Box(actual.maxX+.01,2204,16,actual.maxX+1,2205,17)));
    }
    @Test void sixAxesAndObliqueAabbContainAllRotatedNativeExpansionCorners(){
        var body=new Box(-.3,0,-.3,.3,1.95,.3);
        for(var up:List.of(new Vec3d(1,0,0),new Vec3d(-1,0,0),new Vec3d(0,1,0),new Vec3d(0,-1,0),new Vec3d(0,0,1),new Vec3d(0,0,-1),new Vec3d(1,2,-3).normalize())){
            var box=LocalTargetVolume.expand(body,up,16);
            for(int i=0;i<8;i++)for(int j=0;j<8;j++){
                var base=new Vec3d((i&1)==0?body.minX:body.maxX,(i&2)==0?body.minY:body.maxY,(i&4)==0?body.minZ:body.maxZ);
                var p=base.add(ActorFrameMath.toWorld(new Vec3d((j&1)==0?-16:16,(j&2)==0?-4:4,(j&4)==0?-16:16),up));
                assertTrue(p.x>=box.minX-1e-9&&p.x<=box.maxX+1e-9&&p.y>=box.minY-1e-9&&p.y<=box.maxY+1e-9&&p.z>=box.minZ-1e-9&&p.z<=box.maxZ+1e-9);
            }
        }
    }
    @Test void physicalEyeAndLegacyWorldEyeHaveDistinctNearestPlayerOrdering(){
        var feet=new Vec3d(9.02,2208,16);var physicalEye=feet.add(1.74,0,0);var legacyEye=feet.add(0,1.74,0);
        var nearPhysical=feet.add(0,0,3);var nearLegacy=feet.add(0,1.5,3);
        assertTrue(physicalEye.squaredDistanceTo(nearPhysical)<physicalEye.squaredDistanceTo(nearLegacy));
        assertTrue(legacyEye.squaredDistanceTo(nearPhysical)>legacyEye.squaredDistanceTo(nearLegacy));
    }
}
