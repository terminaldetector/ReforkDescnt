package com.terminaldetector.drmd;
import com.terminaldetector.drmd.world.geometry.HybridMesh;
import com.terminaldetector.drmd.world.micro.MicroGrid;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HybridCavityTest {
    private static HybridMesh sphere(int ox,int oy,int oz){
        var blocks=new ArrayList<HybridMesh.Block>();
        for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++){
            final int bx=ox+x,by=oy+y,bz=oz+z;
            long mask=MicroGrid.build((a,b,c)->Math.pow(bx+(a+.5)*.25+.125,2)+Math.pow(by+(b+.5)*.25-.25,2)+Math.pow(bz+(c+.5)*.25-.375,2)>.75*.75);
            blocks.add(mask==0?HybridMesh.Block.AIR:new HybridMesh.Block(mask,false,true,true));
        }return new HybridMesh(new HybridMesh.Neighbourhood(blocks));
    }
    private static boolean quarter(double v){return Math.abs(v*4-Math.rint(v*4))<1e-10;}
    @Test void coldCavityFacesKeepTheirMaskButGainActualSubQuarterCurvature(){
        var mesh=sphere(0,0,0);var faces=mesh.faces();
        assertTrue(faces.stream().flatMap(f->f.vertices().stream()).anyMatch(v->!quarter(v.position().x())||!quarter(v.position().y())||!quarter(v.position().z())),"actual emitted cut faces are rounded");
        var cells=mesh.collisionCells();assertFalse(cells.isEmpty());
        assertTrue(cells.stream().flatMap(c->c.vertices().stream()).anyMatch(v->!quarter(v.x())||!quarter(v.y())||!quarter(v.z())),"collision receives the very same curved vertices");
        for(var face:faces)for(var v:face.vertices()){assertTrue(Double.isFinite(v.position().x()+v.position().y()+v.position().z()));assertTrue(quarter(v.texturePosition().x())&&quarter(v.texturePosition().y())&&quarter(v.texturePosition().z()),"source UVs remain anchored to the original damage lattice");}
    }
    @Test void theSameSphereHasExactlySharedVerticesAcrossAllThreeBlockSectionDirections(){
        var centre=sphere(0,0,0);var west=sphere(-1,0,0);var below=sphere(0,-1,0);var north=sphere(0,0,-1);
        for(int a=0;a<=4;a++)for(int b=0;b<=4;b++) {
            equal(centre.vertex(0,a,b),west.vertex(4,a,b),-1,0,0);
            equal(centre.vertex(a,0,b),below.vertex(a,4,b),0,-1,0);
            equal(centre.vertex(a,b,0),north.vertex(a,b,4),0,0,-1);
        }
    }
    private static void equal(HybridMesh.Point a,HybridMesh.Point b,int x,int y,int z){assertEquals(a.x(),b.x()+x,1e-12);assertEquals(a.y(),b.y()+y,1e-12);assertEquals(a.z(),b.z()+z,1e-12);}
    @Test void intactEngineeredNeighboursAndPlanarCutsKeepTheirExactOriginalPlanes(){
        var blocks=new ArrayList<HybridMesh.Block>(Collections.nCopies(27,new HybridMesh.Block(MicroGrid.FULL,false,true,true)));
        blocks.set(13,new HybridMesh.Block(MicroGrid.build((x,y,z)->x>=2),false,true,true));var mesh=new HybridMesh(new HybridMesh.Neighbourhood(blocks));
        for(int y=0;y<=4;y++)for(int z=0;z<=4;z++)for(int x=0;x<=4;x++)assertEquals(new HybridMesh.Point(x*.25,y*.25,z*.25),mesh.vertex(x,y,z));
        assertEquals(32,mesh.collisionCells().size(),"rounding never adds or removes an occupied quarter cell");
    }
}
