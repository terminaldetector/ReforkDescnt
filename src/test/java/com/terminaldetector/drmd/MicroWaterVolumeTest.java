package com.terminaldetector.drmd;
import com.terminaldetector.drmd.world.geometry.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MicroWaterVolumeTest {
    private static int at(int x,int y,int z){return(y*16+z)*16+x;}
    private static MicroWaterVolume floor(){var solid=new BitSet(4096);for(int y=0;y<4;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)solid.set(at(x,y,z));return MicroWaterVolume.fromOccupied(solid);}
    @Test void clippedWaterNeverFillsRetainedSolidAndKeepsActualFluidHeight(){
        var volume=floor();assertTrue(volume.fillable());assertEquals(3072,volume.emptyCells());var faces=volume.faces(8/9d,(x,y,z)->false);assertEquals(6,faces.size());
        for(var face:faces)for(var p:face.points()){assertTrue(p.y()>=.25&&p.y()<=8/9d);assertTrue(p.x()>=0&&p.x()<=1&&p.z()>=0&&p.z()<=1);}
        assertEquals(8/9d,faces.stream().flatMap(q->q.points().stream()).mapToDouble(HybridMesh.Point::y).max().orElseThrow());
    }
    @Test void separateOrSealedPocketsCannotInventWaterFromASingleNewFill(){
        var solid=new BitSet(4096);solid.set(0,4096);solid.clear(at(8,8,8));assertFalse(MicroWaterVolume.fromOccupied(solid).fillable());
        solid.clear(at(0,0,0));assertFalse(MicroWaterVolume.fromOccupied(solid).fillable());
        assertFalse(MicroWaterVolume.fromOccupied(new BitSet(){{set(0,4096);}}).fillable());
        assertThrows(IllegalArgumentException.class,()->MicroWaterVolume.fromOccupied(new BitSet(){{set(4096);}}));
    }
    @Test void neighbouringRealWaterRemovesTheJoinWithoutSuppressingTheActualTopSurface(){
        var volume=floor();assertEquals(5,volume.faces(1,(x,y,z)->x==16).size());
        assertTrue(volume.faces(1,(x,y,z)->x==16).stream().anyMatch(q->q.direction()==1));
        assertTrue(volume.faces(1,(x,y,z)->x==16).stream().noneMatch(q->q.direction()==5));
    }
}
