package com.terminaldetector.drmd.client.planet;

import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.planet.OrbitalObjectGeometry;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HorizonOrbitalPixelSnapTest {
    private static HorizonLandmark asteroid(int x){return new HorizonLandmark(x,1444,1176,16,12,12,0x70777d,MacroEntry.Kind.ASTEROID);}
    @Test void measuredFarFootprintCentreUsesAnActualSampleWithoutInflatingItsExtent(){
        var shift=HorizonOrbitalPixelSnap.offset(320.0368,179.9632,1.181,.886,640,360);
        assertEquals(320.5,320.0368+shift[0]*320,1e-5);assertEquals(179.5,179.9632+shift[1]*180,1e-5);
        assertTrue(Math.abs(shift[0]*320)<=.5);assertTrue(Math.abs(shift[1]*180)<=.5);
        // A single image translation preserves both edges, projected span and all relative vertices.
        double left=319.44636,right=320.627459;
        assertEquals(right-left,(right+shift[0]*320)-(left+shift[0]*320),1e-9);
    }
    @Test void largeThinInvalidAndAlreadyCentredObjectsStayNeutral(){
        for(var dimensions:new double[][]{{2,2},{.5,3},{Double.NaN,.5},{0,.5}})
            assertArrayEquals(new float[2],HorizonOrbitalPixelSnap.offset(30.2,40.4,dimensions[0],dimensions[1],640,360));
        assertArrayEquals(new float[2],HorizonOrbitalPixelSnap.offset(30.5,40.5,.8,.8,640,360));
    }
    @Test void cubePieceIndicesStayOnePerObjectAndTheSeventeenthHasNoShaderSlot(){
        var a=asteroid(-1072);var keys=new ArrayList<HorizonOrbitalHandoff.Key>();
        keys.add(new HorizonOrbitalHandoff.Key(a,new HorizonOrbitalHandoff.Cube(0,0,0)));
        keys.add(new HorizonOrbitalHandoff.Key(a,new HorizonOrbitalHandoff.Cube(1,0,0)));
        for(int i=1;i<=16;i++)keys.add(new HorizonOrbitalHandoff.Key(asteroid(i),new HorizonOrbitalHandoff.Cube(0,0,0)));
        var slots=HorizonOrbitalPixelSnap.slots(keys);assertEquals(0,slots[0]);assertEquals(0,slots[1]);
        assertEquals(15,slots[16]);assertEquals(-1,slots[17]);assertEquals(16,HorizonOrbitalPixelSnap.objects(keys).size());
    }
    @Test void currentProjectionSelectsFarShapesAndTheDisabledPhaseClearsEverySlot(){
        var mark=asteroid(-1072);var key=new HorizonOrbitalHandoff.Key(mark,new HorizonOrbitalHandoff.Cube(-68,89,73));
        var keys=List.of(key);float pitch=(float)Math.atan2(1.119999885559,3071.5);
        var view=new Matrix4f().lookAlong(0,-(float)Math.sin(pitch),(float)Math.cos(pitch),0,(float)Math.cos(pitch),(float)Math.sin(pitch));
        var projection=new Matrix4f().perspective((float)Math.toRadians(77),640f/360,.05f,256);
        var far=HorizonOrbitalPixelSnap.shifts(keys,-1071.5,1445.619999885559,-1895.5,view,projection,640,360,0,217.6f,true);
        assertTrue(Math.abs(far[0])+Math.abs(far[1])>0);
        assertArrayEquals(new float[32],HorizonOrbitalPixelSnap.shifts(keys,-1071.5,1445.619999885559,-1895.5,view,projection,640,360,0,217.6f,false));
        assertArrayEquals(new float[32],HorizonOrbitalPixelSnap.shifts(keys,-1071.5,1445.619999885559,664.5,view,projection,640,360,0,217.6f,true));
        var station=new HorizonLandmark(-1072,1444,1176,24,16,20,0xaaaaaa,MacroEntry.Kind.ORBITAL_STATION);
        var stationKeys=List.of(new HorizonOrbitalHandoff.Key(station,key.cube()));
        var stationShift=HorizonOrbitalPixelSnap.shifts(stationKeys,-1071.5,1445.619999885559,-1895.5,view,projection,640,360,0,217.6f,true);
        assertTrue(Math.abs(stationShift[0])+Math.abs(stationShift[1])>0);
        assertTrue(Math.abs(stationShift[0]*320)<=.500001);
        assertTrue(Math.abs(stationShift[1]*180)<=.500001);
        assertArrayEquals(new float[32],HorizonOrbitalPixelSnap.shifts(stationKeys,-1071.5,1445.619999885559,664.5,view,projection,640,360,0,217.6f,true));
        var sample=OrbitalObjectGeometry.stationDeckSample(24,16,20);
        assertTrue(OrbitalObjectGeometry.cells(OrbitalObjectGeometry.Shape.STATION,24,16,20).contains(sample));
    }
    @Test void bothPassesUndoTheImageTranslationBeforeComputingTrueRayDepth(){
        var inverse=new Matrix4f().perspective((float)Math.toRadians(77),640f/360,.05f,256).invert();
        float originalX=.0002f,originalY=-.0003f,shiftX=.0014f,shiftY=-.0025f;
        var original=new Vector4f(originalX,originalY,1,1).mul(inverse);
        var corrected=new Vector4f((originalX+shiftX)-shiftX,(originalY+shiftY)-shiftY,1,1).mul(inverse);
        assertEquals(original.x/original.w,corrected.x/corrected.w,1e-5);
        assertEquals(original.y/original.w,corrected.y/corrected.w,1e-5);
        assertEquals(original.z/original.w,corrected.z/corrected.w,1e-5);
        var wrong=new Vector4f(originalX+shiftX,originalY+shiftY,1,1).mul(inverse);
        assertTrue(Math.abs(wrong.y/wrong.w-original.y/original.w)>1e-3);
    }
}
