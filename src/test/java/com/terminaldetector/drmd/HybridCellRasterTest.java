package com.terminaldetector.drmd;
import com.terminaldetector.drmd.world.geometry.*;
import com.terminaldetector.drmd.world.micro.MicroGrid;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HybridCellRasterTest {
    private static HybridMesh.Cell shear(double shift){
        var p=new ArrayList<HybridMesh.Point>();
        for(int y=0;y<2;y++)for(int z=0;z<2;z++)for(int x=0;x<2;x++)p.add(new HybridMesh.Point(shift+(x+y)*.25,y*.25,z*.25));
        return new HybridMesh.Cell(p);
    }
    @Test void unchangedEngineeredCubeKeepsExactlyItsExistingSixteenthVolume(){
        var blocks=new ArrayList<HybridMesh.Block>(Collections.nCopies(27,HybridMesh.Block.AIR));blocks.set(13,new HybridMesh.Block(MicroGrid.FULL,false,true,true));
        var mesh=new HybridMesh(new HybridMesh.Neighbourhood(blocks));var bits=HybridCellRaster.raster(mesh.collisionCells());assertEquals(4096,bits.cardinality());
        for(int y=0;y<32;y++)for(int z=0;z<32;z++)for(int x=0;x<32;x++)assertEquals(x>=8&&x<24&&y>=8&&y<24&&z>=8&&z<24,bits.get(HybridCellRaster.index(x,y,z)));
    }
    @Test void trueDiagonalClearanceIsNotFilledByTheOldEnclosingQuarterCellBox(){
        var bits=HybridCellRaster.raster(List.of(shear(0)));int expected=0;
        for(int y=0;y<32;y++)for(int z=0;z<32;z++)for(int x=0;x<32;x++){
            double x0=-.5+x/16d,x1=x0+1d/16,y0=-.5+y/16d,y1=y0+1d/16,z0=-.5+z/16d,z1=z0+1d/16;
            // Independent analytic convex strip x in[y,y+1/4], y/z in[0,1/4].
            boolean intersects=y1>0&&y0<.25&&z1>0&&z0<.25&&x1>Math.max(0,y0)&&x0<Math.min(.25,y1)+.25;
            if(intersects)expected++;assertEquals(intersects,bits.get(HybridCellRaster.index(x,y,z)),"voxel "+x+","+y+","+z);
        }
        assertEquals(expected,bits.cardinality());assertTrue(bits.cardinality()<128,"old AABB filled128 voxels");
        assertFalse(bits.get(HybridCellRaster.index(15,8,8)),"visible outside corner stays traversable");
    }
    @Test void identicalCellGeometryTranslatedOneBlockRetainsTheSameRaster(){
        var left=HybridCellRaster.raster(List.of(shear(0)));var right=HybridCellRaster.raster(List.of(shear(1)));
        assertEquals(left.cardinality(),right.cardinality());
        for(int y=0;y<32;y++)for(int z=0;z<32;z++)for(int x=0;x<16;x++)assertEquals(left.get(HybridCellRaster.index(x,y,z)),right.get(HybridCellRaster.index(x+16,y,z)));
    }
    @Test void invalidOrUnboundedCellSourcesNeverAllocateAWorldSizedCollider(){
        assertTrue(HybridCellRaster.raster(List.of()).isEmpty());assertThrows(IllegalArgumentException.class,()->HybridCellRaster.raster(Collections.nCopies(65,shear(0))));
        assertThrows(IllegalArgumentException.class,()->HybridCellRaster.raster(List.of(shear(2))));
        assertThrows(IllegalArgumentException.class,()->new HybridMesh.Cell(List.of()));
    }
}
