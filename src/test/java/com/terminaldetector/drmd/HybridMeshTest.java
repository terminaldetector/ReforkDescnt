package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.geometry.HybridMesh;
import com.terminaldetector.drmd.world.micro.MicroGrid;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class HybridMeshTest {
    private static final HybridMesh.Block SOFT=new HybridMesh.Block(-1,true,false,true);
    private static final HybridMesh.Block HARD=new HybridMesh.Block(-1,false,true,true);
    interface Field { HybridMesh.Block at(int x,int y,int z); }
    private static HybridMesh mesh(Field f,int x,int y,int z) {
        var blocks=new ArrayList<HybridMesh.Block>();
        for(int dy=-1;dy<=1;dy++) for(int dz=-1;dz<=1;dz++) for(int dx=-1;dx<=1;dx++)
            blocks.add(f.at(x+dx,y+dy,z+dz));
        return new HybridMesh(new HybridMesh.Neighbourhood(blocks));
    }
    @Test void rigidCubeHasSixMergedFacesWithOutwardWinding() {
        var m=mesh((x,y,z)-> x==0&&y==0&&z==0?HARD:HybridMesh.Block.AIR,0,0,0);
        assertEquals(6,m.faces().size());
        for(var f:m.faces()) assertOutward(f);
        assertEquals(new HybridMesh.Point(0,0,0),m.vertex(0,0,0));
        assertEquals(new HybridMesh.Point(1,1,1),m.vertex(4,4,4));
    }
    @Test void organicCubeActuallyMovesGeometryAndCollider() {
        var m=mesh((x,y,z)-> x==0&&y==0&&z==0?SOFT:HybridMesh.Block.AIR,0,0,0);
        assertEquals(new HybridMesh.Point(.25,.25,.25),m.vertex(0,0,0));
        assertEquals(new HybridMesh.Point(.75,.75,.75),m.vertex(4,4,4));
        assertEquals(.25,m.collisionBounds().stream().mapToDouble(HybridMesh.Bounds::minX).min().orElseThrow(),1e-12);
        for(var f:m.faces()) assertOutward(f);
    }
    @Test void flatGroundRemainsFlatAndHasNoInternalFaces() {
        var m=mesh((x,y,z)->y<=0?SOFT:HybridMesh.Block.AIR,0,0,0);
        assertEquals(16,m.faces().size());
        for(var f:m.faces()) {
            assertEquals(1,f.direction());
            for(var v:f.vertices()) assertEquals(1,v.position().y(),1e-12);
        }
    }
    @Test void rigidBuildingPinsTheWholeSharedBoundary() {
        var m=mesh((x,y,z)->x==0&&y==0&&z==0?SOFT:x==1&&y==0&&z==0?HARD:HybridMesh.Block.AIR,0,0,0);
        for(int y=0;y<=4;y++) for(int z=0;z<=4;z++)
            assertEquals(new HybridMesh.Point(1,y*.25,z*.25),m.vertex(4,y,z));
        assertTrue(m.faces().stream().noneMatch(f->f.direction()==5));
    }
    @Test void quarterCellTunnelSurvivesInMeshAndCollision() {
        long mask=MicroGrid.FULL;
        for(int x=0;x<4;x++) mask=MicroGrid.clear(mask,x,1,1);
        final long damaged=mask;
        var m=mesh((x,y,z)->x==0&&y==0&&z==0?new HybridMesh.Block(damaged,true,false,true):HybridMesh.Block.AIR,0,0,0);
        assertEquals(60,m.collisionBounds().size());
        var centre=m.vertex(2,1,1);
        double tx=.5,ty=centre.y()+.0625,tz=centre.z()+.0625;
        assertFalse(m.collisionBounds().stream().anyMatch(b -> tx>b.minX()&&tx<b.maxX()&&ty>b.minY()&&ty<b.maxY()&&tz>b.minZ()&&tz<b.maxZ()));
        assertTrue(m.faces().size()>96,"the cut has real interior faces");
    }
    @Test void sharedVerticesMatchAcrossAllAxesAndNegativeSectionBoundaries() {
        Random random=new Random(71329);
        for(int run=0;run<80;run++) {
            long seed=random.nextLong();
            Field field=(x,y,z)-> {
                if(x==-16&&y==-16&&z==-16 || x==-15&&y==-16&&z==-16 || x==-16&&y==-15&&z==-16 || x==-16&&y==-16&&z==-15) return SOFT;
                long h=(x*73856093L ^ y*19349663L ^ z*83492791L)^seed;
                return (h&3)==0?HybridMesh.Block.AIR:(h&3)==1?HARD:SOFT;
            };
            var a=mesh(field,-16,-16,-16);
            for(int axis=0;axis<3;axis++) {
                var b=mesh(field,-16+(axis==0?1:0),-16+(axis==1?1:0),-16+(axis==2?1:0));
                for(int u=0;u<=4;u++) for(int v=0;v<=4;v++) {
                    var p=axis==0?a.vertex(4,u,v):axis==1?a.vertex(u,4,v):a.vertex(u,v,4);
                    var q=axis==0?b.vertex(0,u,v):axis==1?b.vertex(u,0,v):b.vertex(u,v,0);
                    assertEquals(p.x(),q.x()+(axis==0?1:0),1e-12);
                    assertEquals(p.y(),q.y()+(axis==1?1:0),1e-12);
                    assertEquals(p.z(),q.z()+(axis==2?1:0),1e-12);
                }
            }
        }
    }
    @Test void everyCornerPatternProducesFiniteNonInvertedCells() {
        for(int pattern=0;pattern<256;pattern++) {
            final int bits=pattern;
            var m=mesh((x,y,z)-> x==0&&y==0&&z==0?SOFT:
                x>=0&&y>=0&&z>=0&&(bits&(1<<((y*2+z)*2+x)))!=0?SOFT:HybridMesh.Block.AIR,0,0,0);
            for(var b:m.collisionBounds()) {
                assertTrue(Double.isFinite(b.minX()) && Double.isFinite(b.maxX()));
                assertTrue(b.maxX()>b.minX() && b.maxY()>b.minY() && b.maxZ()>b.minZ());
            }
            for(var f:m.faces()) assertOutward(f);
        }
    }
    private static void assertOutward(HybridMesh.Face f) {
        var a=f.vertices().get(0).position();var b=f.vertices().get(1).position();var c=f.vertices().get(2).position();
        double ux=b.x()-a.x(),uy=b.y()-a.y(),uz=b.z()-a.z(),vx=c.x()-a.x(),vy=c.y()-a.y(),vz=c.z()-a.z();
        double axis=f.direction()<2?uz*vx-ux*vz:f.direction()<4?ux*vy-uy*vx:uy*vz-uz*vy;
        assertTrue(axis*(f.direction()%2==0?-1:1)>0,"outward non-degenerate face "+f.direction());
    }
}
