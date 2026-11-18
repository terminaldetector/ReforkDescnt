package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.geometry.HybridMesh;
import com.terminaldetector.drmd.world.geometry.HybridMesh.LogAxis;
import com.terminaldetector.drmd.world.micro.MicroGrid;
import com.terminaldetector.drmd.world.micro.ThermalBore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RoundedLogsTest {
    interface Field {HybridMesh.Block at(int x,int y,int z);}
    private static HybridMesh.Block log(LogAxis axis) {return new HybridMesh.Block(-1,true,false,true,List.of(),axis);}
    private static HybridMesh mesh(Field field,int x,int y,int z) {
        var blocks=new ArrayList<HybridMesh.Block>();
        for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)blocks.add(field.at(x+dx,y+dy,z+dz));
        return new HybridMesh(new HybridMesh.Neighbourhood(blocks));
    }
    private static HybridMesh isolated(HybridMesh.Block block) {return mesh((x,y,z)->x==0&&y==0&&z==0?block:HybridMesh.Block.AIR,0,0,0);}
    @Test void allThreeSourceAxesKeepFullLengthAndHaveRealPolygonalCrossSections() {
        for(var axis:List.of(LogAxis.X,LogAxis.Y,LogAxis.Z)) {
            var m=isolated(log(axis));
            var end=axis==LogAxis.X?m.vertex(0,2,2):axis==LogAxis.Y?m.vertex(2,0,2):m.vertex(2,2,0);
            var side=axis==LogAxis.X?m.vertex(2,0,2):m.vertex(0,2,2);
            assertEquals(0,axis==LogAxis.X?end.x():axis==LogAxis.Y?end.y():end.z(),1e-12);
            assertEquals(.125,axis==LogAxis.X?side.y():side.x(),1e-12);
            assertTrue(m.faces().size()>6&&m.faces().size()<=96,"bounded polygonal surface, not an isolated rounded cube");
            assertTrue(m.collisionBounds().size()<=64);
        }
    }
    @Test void perpendicularBranchesAndThickTrunksShareEveryActualSeamVertex() {
        for(boolean perpendicular:new boolean[]{true,false}) {
            Field field=(x,y,z)-> {
                if(x==-16&&z==-16&&(y==-16||y==-15))return log(LogAxis.Y);
                if(x==-15&&y==-16&&z==-16)return log(perpendicular?LogAxis.X:LogAxis.Y);
                return HybridMesh.Block.AIR;
            };
            var a=mesh(field,-16,-16,-16);var b=mesh(field,-15,-16,-16);var upper=mesh(field,-16,-15,-16);
            for(int u=0;u<=4;u++)for(int v=0;v<=4;v++) {
                var p=a.vertex(4,u,v);var q=b.vertex(0,u,v);
                assertEquals(p.x(),q.x()+1,1e-12);assertEquals(p.y(),q.y(),1e-12);assertEquals(p.z(),q.z(),1e-12);
                p=a.vertex(u,4,v);q=upper.vertex(u,0,v);
                assertEquals(p.x(),q.x(),1e-12);assertEquals(p.y(),q.y()+1,1e-12);assertEquals(p.z(),q.z(),1e-12);
            }
        }
    }
    @Test void roundEndCapsAndTheCollisionProxyRetainTheSameOuterArcAndEmptyCorner() {
        var m=isolated(log(LogAxis.Y));var bounds=m.collisionBounds();
        assertTrue(m.faces().stream().filter(f->f.direction()==0).flatMap(f->f.vertices().stream())
            .anyMatch(v->Math.abs(v.position().x()-.125)<1e-12&&Math.abs(v.position().z()-.5)<1e-12),
            "planar greedy merging must not erase curved cap edges");
        assertFalse(bounds.stream().anyMatch(b->inside(b,.13,.5,.13)),"the old cube corner is empty");
        assertTrue(bounds.stream().anyMatch(b->inside(b,.13,.5,.5)),"face-midpoint radius is not lost by corner-only bounds");
        for(var f:m.faces())for(var v:f.vertices())assertTrue(bounds.stream().anyMatch(b->inside(b,v.position().x(),v.position().y(),v.position().z())));
    }
    @Test void oldConstructorsThermalBoresEngineeredAnchorsAndDamageMasksRemainOwned() {
        var old=new HybridMesh.Block(-1,true,false,true);
        assertEquals(new HybridMesh.Point(.25,.25,.25),isolated(old).vertex(0,0,0));
        var bore=new ThermalBore(0,-2,0,0,2,0,.5);
        var legacy=isolated(new HybridMesh.Block(bore.mask(),true,false,true,List.of(bore)));
        var tagged=isolated(new HybridMesh.Block(bore.mask(),true,false,true,List.of(bore),LogAxis.Y));
        for(int y=0;y<=4;y++)for(int z=0;z<=4;z++)for(int x=0;x<=4;x++)assertEquals(legacy.vertex(x,y,z),tagged.vertex(x,y,z));
        var anchored=mesh((x,y,z)->x==0&&y==0&&z==0?log(LogAxis.Y):x==1&&y==0&&z==0?new HybridMesh.Block(-1,false,true,true):HybridMesh.Block.AIR,0,0,0);
        for(int y=0;y<=4;y++)for(int z=0;z<=4;z++)assertEquals(new HybridMesh.Point(1,y*.25,z*.25),anchored.vertex(4,y,z));
        long cut=MicroGrid.clear(-1L,1,1,1);var damaged=new HybridMesh.Block(cut,true,false,true,List.of(),LogAxis.Y);
        assertEquals(63,isolated(damaged).collisionBounds().size());assertEquals(cut,damaged.mask());
    }
    private static boolean inside(HybridMesh.Bounds b,double x,double y,double z) {
        return x>=b.minX()-1e-12&&x<=b.maxX()+1e-12&&y>=b.minY()-1e-12&&y<=b.maxY()+1e-12&&z>=b.minZ()-1e-12&&z<=b.maxZ()+1e-12;
    }
}
