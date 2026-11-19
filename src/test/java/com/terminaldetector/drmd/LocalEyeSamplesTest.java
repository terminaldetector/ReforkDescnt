package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.gravity.LocalEyeSamples;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LocalEyeSamplesTest {
    private static List<Vec3d> nativeSamples(Vec3d eye,double width,double scale){
        var samples=new ArrayList<Vec3d>();
        for(int i=0;i<8;i++)samples.add(eye.add(((i>>0)%2-.5)*width*.8,((i>>1)%2-.5)*.1*scale,((i>>2)%2-.5)*width*.8));
        return samples;
    }
    @Test void allEightOrdinaryEyeSamplesRemainBitExact(){
        var eye=new Vec3d(16,2202.6400000047684,16);var up=new Vec3d(-0.0,1,-0.0);
        for(var sample:nativeSamples(eye,.6,1))assertSame(sample,LocalEyeSamples.transform(sample,eye,eye,up));
    }
    @Test void actualWallCoordinatesNoLongerSampleBehindTheClearPhysicalEye(){
        var oldEye=new Vec3d(9.02,2209.6200000047684,16);var actualEye=new Vec3d(10.640000004768371,2208,16);var up=new Vec3d(1,0,0);
        var wall=new Box(8,2200,8,9,2216,24);int oldHits=0,newHits=0;
        for(var sample:nativeSamples(oldEye,.6,1)){
            if(wall.contains(sample))oldHits++;
            if(wall.contains(LocalEyeSamples.transform(sample,oldEye,actualEye,up)))newHits++;
        }
        assertEquals(4,oldHits,"the exact retained native eye generates false in-wall samples");
        assertEquals(0,newHits,"all eight actual eye samples are clear of the real support wall");
    }
    @Test void aGenuinelyEmbeddedEyeRetainsAllEightOpaqueMaterialSamples(){
        var oldEye=new Vec3d(9.02,2209.6200000047684,16);var actualEye=new Vec3d(8.5,2208.5,16.5);var up=new Vec3d(1,0,0);
        var opaque=new Box(8,2208,16,9,2209,17);int hits=0;
        for(var sample:nativeSamples(oldEye,.6,1))if(opaque.contains(LocalEyeSamples.transform(sample,oldEye,actualEye,up)))hits++;
        assertEquals(8,hits,"an actual opaque eye intersection still reaches native shouldBlockVision and fullscreen overlay");
    }
    @Test void allSixAxesAndAnObliqueFramePreserveTheNormalAndTheActualEyeCentre(){
        var oldEye=new Vec3d(13,14,15);var actualEye=new Vec3d(34,56,78);
        for(var up:List.of(new Vec3d(1,0,0),new Vec3d(-1,0,0),new Vec3d(0,1,0),new Vec3d(0,-1,0),new Vec3d(0,0,1),new Vec3d(0,0,-1),new Vec3d(1,2,-3).normalize())){
            Vec3d sum=Vec3d.ZERO;
            for(var sample:nativeSamples(oldEye,.6,1)){
                var actual=LocalEyeSamples.transform(sample,oldEye,actualEye,up);var offset=actual.subtract(actualEye);sum=sum.add(actual);
                assertEquals(sample.y-oldEye.y,offset.dotProduct(up),1e-10,"the short native eye-normal offset follows physical UP");
                assertEquals(sample.squaredDistanceTo(oldEye),offset.lengthSquared(),1e-10,"a rotation preserves native probe size");
            }
            assertTrue(sum.multiply(1.0/8).distanceTo(actualEye)<1e-10,"all eight corners are centred on the actual physical eye");
        }
    }
    @Test void nativeWidthAndScaleRemainOwnedByTheCaller(){
        var oldEye=new Vec3d(2,3,4);var actualEye=new Vec3d(8,9,10);var up=new Vec3d(0,0,-1);
        for(var sample:nativeSamples(oldEye,1.2,3)){
            var offset=LocalEyeSamples.transform(sample,oldEye,actualEye,up).subtract(actualEye);
            assertEquals(.15,Math.abs(offset.dotProduct(up)),1e-10);
            assertEquals(.48*.48*2+.15*.15,offset.lengthSquared(),1e-10);
        }
    }
    @Test void invalidFrameOrEyeNeverManufacturesAnotherOverlayQuery(){
        var sample=new Vec3d(1,2,3);var eye=new Vec3d(4,5,6);
        for(var up:List.of(Vec3d.ZERO,new Vec3d(2,0,0),new Vec3d(Double.NaN,0,0)))assertSame(sample,LocalEyeSamples.transform(sample,eye,eye,up));
        assertSame(sample,LocalEyeSamples.transform(sample,eye,new Vec3d(Double.NaN,0,0),new Vec3d(1,0,0)));
    }
}
