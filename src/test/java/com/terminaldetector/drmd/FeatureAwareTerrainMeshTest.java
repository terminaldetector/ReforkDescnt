package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.geometry.HybridMesh;
import com.terminaldetector.drmd.world.micro.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FeatureAwareTerrainMeshTest {
    private static HybridMesh mesh(boolean organic,boolean hardNeighbour) {
        var bore=new ThermalBore(-.5,-2.6,.5,1.5,-.6,1.5,2);
        var cells=new ArrayList<HybridMesh.Block>();
        for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++)
            cells.add(x==0&&y==0&&z==0?new HybridMesh.Block(bore.mask(),organic,!organic,true,List.of(bore)):
                hardNeighbour&&x==1&&y==0&&z==0?new HybridMesh.Block(MicroGrid.FULL,false,true,true):HybridMesh.Block.AIR);
        return new HybridMesh(new HybridMesh.Neighbourhood(cells));
    }
    @Test void mergedThermalFacesRetainEveryActualCurvedRimVertexForBothMaterialClasses() {
        for(boolean organic:new boolean[]{false,true}) {
            var mesh=mesh(organic,false);assertFalse(mesh.faces().isEmpty());int curved=0;
            for(int y=0;y<=4;y++)for(int z=0;z<=4;z++)for(int x=0;x<=4;x++) {
                var p=mesh.vertex(x,y,z);
                if(Math.abs(p.x()-x*.25)+Math.abs(p.y()-y*.25)+Math.abs(p.z()-z*.25)>1e-6)curved++;
            }
            assertTrue(curved>0,"fixture contains the actual nonlinear thermal surface");
            for(var face:mesh.faces())assertOriginalLattice(mesh,face);
        }
    }
    @Test void theSameThermalSurfaceKeepsAnUntouchedEngineeredSeamAndItsCollisionEnvelope() {
        var mesh=mesh(true,true);var bounds=mesh.collisionBounds();assertFalse(bounds.isEmpty());
        for(int y=0;y<=4;y++)for(int z=0;z<=4;z++)
            assertEquals(new HybridMesh.Point(1,y*.25,z*.25),mesh.vertex(4,y,z));
        var eastFaces = mesh.faces().stream().filter(face -> face.direction() == 5).toList();
        assertFalse(eastFaces.isEmpty(), "the curved cavity retains its internal east-facing surfaces");
        assertTrue(eastFaces.stream().noneMatch(face -> face.vertices().stream()
            .allMatch(vertex -> vertex.texturePosition().x() == 1)), "opaque hull owns the external shared face");
        for(var face:mesh.faces())for(var vertex:face.vertices()) {
            var p=vertex.position();
            assertTrue(bounds.stream().anyMatch(b->p.x()>=b.minX()-1e-12&&p.x()<=b.maxX()+1e-12
                &&p.y()>=b.minY()-1e-12&&p.y()<=b.maxY()+1e-12&&p.z()>=b.minZ()-1e-12&&p.z()<=b.maxZ()+1e-12));
        }
    }
    private static double u(HybridMesh.Point p,int direction){return direction<4?p.x():p.z();}
    private static double v(HybridMesh.Point p,int direction){return direction<2?p.z():p.y();}
    private static void assertOriginalLattice(HybridMesh mesh,HybridMesh.Face face) {
        int d=face.direction();var vertices=face.vertices();
        double u0=vertices.stream().mapToDouble(p->u(p.texturePosition(),d)).min().orElseThrow(),
            u1=vertices.stream().mapToDouble(p->u(p.texturePosition(),d)).max().orElseThrow(),
            v0=vertices.stream().mapToDouble(p->v(p.texturePosition(),d)).min().orElseThrow(),
            v1=vertices.stream().mapToDouble(p->v(p.texturePosition(),d)).max().orElseThrow();
        double plane=d<2?vertices.get(0).texturePosition().y():d<4?vertices.get(0).texturePosition().z():vertices.get(0).texturePosition().x();
        for(int j=(int)Math.round(v0*4);j<=Math.round(v1*4);j++)for(int i=(int)Math.round(u0*4);i<=Math.round(u1*4);i++) {
            double s=(i*.25-u0)/(u1-u0),t=(j*.25-v0)/(v1-v0),px=0,py=0,pz=0;
            for(var corner:vertices) {
                double weight=(u(corner.texturePosition(),d)==u0?1-s:s)*(v(corner.texturePosition(),d)==v0?1-t:t);
                px+=weight*corner.position().x();py+=weight*corner.position().y();pz+=weight*corner.position().z();
            }
            int p=(int)Math.round(plane*4);
            var actual=d<2?mesh.vertex(i,p,j):d<4?mesh.vertex(i,j,p):mesh.vertex(p,j,i);
            assertEquals(actual.x(),px,1e-10,"merged face lost a projected rim X vertex");
            assertEquals(actual.y(),py,1e-10,"merged face lost a projected rim Y vertex");
            assertEquals(actual.z(),pz,1e-10,"merged face lost a projected rim Z vertex");
        }
    }
}
