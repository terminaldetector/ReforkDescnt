package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.geometry.*;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BoundedHybridShapeTest {
    @Test void irregularNeighbourhoodsCannotMultiplyCoordinatePlanes() {
        var random=new Random(261026);
        for(int run=0;run<128;run++) {
            var neighbours=new ArrayList<HybridMesh.Block>();
            for(int i=0;i<27;i++)neighbours.add(new HybridMesh.Block(random.nextBoolean()?-1L:0L,true,false,true));
            neighbours.set(13,new HybridMesh.Block(random.nextLong(),true,false,true));
            var bounds=new HybridMesh(new HybridMesh.Neighbourhood(neighbours)).collisionBounds();
            var shape=BoundedHybridShape.from(bounds);
            for(var axis:Direction.Axis.values())assertEquals(33,shape.getPointPositions(axis).size());
            var enclosing=shape.getBoundingBox();
            for(var b:bounds) {
                assertTrue(enclosing.minX<=b.minX() && enclosing.maxX>=b.maxX());
                assertTrue(enclosing.minY<=b.minY() && enclosing.maxY>=b.maxY());
                assertTrue(enclosing.minZ<=b.minZ() && enclosing.maxZ>=b.maxZ());
            }
        }
    }
    @Test void quarterCellHoleAndExactFlatBoundsSurviveRasterization() {
        var boxes=new ArrayList<HybridMesh.Bounds>();
        for(int x=0;x<4;x++)for(int y=0;y<4;y++)for(int z=0;z<4;z++)
            if(y!=1 || z!=1)boxes.add(new HybridMesh.Bounds(x*.25,y*.25,z*.25,(x+1)*.25,(y+1)*.25,(z+1)*.25));
        var shape=BoundedHybridShape.from(boxes);
        assertEquals(0,shape.getBoundingBox().minX);assertEquals(1,shape.getBoundingBox().maxX);
        assertFalse(shape.getBoundingBoxes().stream().anyMatch(b->b.contains(.5,.375,.375)),"do not fill the open tunnel");
        assertTrue(BoundedHybridShape.from(List.of()).isEmpty());
    }
}
