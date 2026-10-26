package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.*;
import com.terminaldetector.drmd.world.portal.mirror.MirrorEdges;
import net.minecraft.util.math.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MirrorSurfaceTest {
    private MirrorScanner.MirrorFace face(BlockPos pos,Direction facing) {
        var normal=Vec3d.of(facing.getVector());
        return new MirrorScanner.MirrorFace(pos,facing,Vec3d.ofCenter(pos).add(normal.multiply(.5)),normal,null);
    }
    @Test void rectangularWallsJoinOnAllSixFacesAcrossChunkBoundaries() {
        for(var facing:Direction.values()) {
            var dirs=MirrorEdges.directions(facing);
            var list=new ArrayList<MirrorScanner.MirrorFace>();
            for(int u=0;u<4;u++)for(int v=0;v<3;v++)list.add(face(new BlockPos(15,15,15).offset(dirs[1],u).offset(dirs[3],v),facing));
            var surfaces=MirrorSurface.join(list);
            assertEquals(1,surfaces.size());assertEquals(12,surfaces.getFirst().panes().size());
            assertEquals(12,surfaces.getFirst().apertures().size());
            assertEquals((4-.25)*(3-.25),area(surfaces.getFirst()),1e-9);
        }
    }
    @Test void diagonalDifferentFacingAndParallelPlanesStaySeparate() {
        var a=face(BlockPos.ORIGIN,Direction.SOUTH);
        assertEquals(2,MirrorSurface.join(List.of(a,face(new BlockPos(1,1,0),Direction.SOUTH))).size());
        assertEquals(2,MirrorSurface.join(List.of(a,face(new BlockPos(1,0,0),Direction.NORTH))).size());
        assertEquals(2,MirrorSurface.join(List.of(a,face(new BlockPos(0,0,1),Direction.SOUTH))).size());
    }
    @Test void holesAreNotFilledAndRemovingABridgeSplitsTheSurface() {
        var ring=new ArrayList<MirrorScanner.MirrorFace>();
        for(int x=0;x<3;x++)for(int y=0;y<3;y++)if(x!=1 || y!=1)ring.add(face(new BlockPos(x,y,0),Direction.SOUTH));
        var surface=MirrorSurface.join(ring).getFirst();
        assertEquals(8,surface.panes().size());
        for(var quad:surface.apertures())assertFalse(quad[0].x()<1.5 && quad[1].x()>1.5 && quad[0].y()<1.5 && quad[2].y()>1.5);
        assertEquals(2,MirrorSurface.join(List.of(face(BlockPos.ORIGIN,Direction.SOUTH),face(new BlockPos(2,0,0),Direction.SOUTH))).size());
    }
    private double area(MirrorSurface surface) {
        double total=0;
        for(var q:surface.apertures())total+=q[1].minus(q[0]).cross(q[3].minus(q[0])).length();
        return total;
    }
}
