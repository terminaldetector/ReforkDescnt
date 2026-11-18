package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.geometry.HybridMesh;
import com.terminaldetector.drmd.world.micro.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ThermalBoreTest {
    @Test void invalidFieldsCannotCreateAnUnboundedOrNonfiniteShape(){
        assertThrows(IllegalArgumentException.class,()->new ThermalBore(Double.NaN,0,0,0,0,0,2));
        assertThrows(IllegalArgumentException.class,()->new ThermalBore(0,0,0,0,0,5,2));
        assertThrows(IllegalArgumentException.class,()->new ThermalBore(0,0,0,0,0,1,3.01));
        assertThrows(IllegalArgumentException.class,()->new ThermalBore(17,0,0,17,0,1,2));
    }
    @Test void aRealRimVertexMovesOntoTheRequestedCircleWithinTheExistingCellShell(){
        var bore=new ThermalBore(0,-2,0,0,2,0,2);var p=bore.project(1.9,.25,.25);
        assertEquals(2,bore.distance(p.x(),p.y(),p.z()),1e-12);
        assertTrue(Math.hypot(p.x()-1.9,p.z()-.25)<=ThermalBore.SHELL);
        assertTrue(Math.abs(p.x()-1.9)>1e-4,"actual polygon differs from the unprojected staircase");
        assertEquals(new ThermalBore.Point(4,.25,0),bore.project(4,.25,0),"far engineered geometry is not pulled into a cut");
    }
    private static HybridMesh mesh(int worldX){
        var blocks=new ArrayList<HybridMesh.Block>();
        for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++){
            var bore=new ThermalBore(-.5-worldX-x,-2.6-y,.5-z,1.5-worldX-x,-.6-y,1.5-z,2);
            blocks.add(new HybridMesh.Block(bore.mask(),true,false,true,List.of(bore)));
        }
        return new HybridMesh(new HybridMesh.Neighbourhood(blocks));
    }
    @Test void adjacentActualMeshesChooseExactlyTheSameWorldVerticesAtTheirSeam(){
        var a=mesh(0);var b=mesh(1);
        for(int y=0;y<=4;y++)for(int z=0;z<=4;z++){
            var left=a.vertex(4,y,z);var right=b.vertex(0,y,z);
            assertEquals(left.x(),right.x()+1,1e-12);assertEquals(left.y(),right.y(),1e-12);assertEquals(left.z(),right.z(),1e-12);
        }
    }
    @Test void everyRenderedCavityCornerIsContainedByTheSameConservativeCellBounds(){
        var mesh=mesh(0);var bounds=mesh.collisionBounds();assertFalse(bounds.isEmpty());assertTrue(bounds.size()<=64);
        int nonGrid=0;
        for(var face:mesh.faces())for(var vertex:face.vertices()){
            var p=vertex.position();
            assertTrue(bounds.stream().anyMatch(b->p.x()>=b.minX()-1e-12&&p.x()<=b.maxX()+1e-12&&p.y()>=b.minY()-1e-12&&p.y()<=b.maxY()+1e-12&&p.z()>=b.minZ()-1e-12&&p.z()<=b.maxZ()+1e-12));
            assertTrue(p.x()>=-.5&&p.x()<=1.5&&p.y()>=-.5&&p.y()<=1.5&&p.z()>=-.5&&p.z()<=1.5);
            if(Math.abs(p.x()*4-Math.rint(p.x()*4))>1e-5||Math.abs(p.z()*4-Math.rint(p.z()*4))>1e-5)nonGrid++;
        }
        assertTrue(nonGrid>0,"the accepted primitive is an actual polygonal circular rim, not particles or a tinted cubic hole");
    }
    @Test void materialResponsePreservesARealSofteningIntervalAndFiniteWorstCase(){
        assertTrue(ThermalBore.softenDose(.5f)<ThermalBore.softenDose(5));
        for(float hardness:new float[]{0,.5f,1.5f,5,49}){
            assertTrue(ThermalBore.softenDose(hardness)>=3);assertEquals(ThermalBore.softenDose(hardness)+2,ThermalBore.meltDose(hardness));assertTrue(ThermalBore.meltDose(hardness)<=14);
        }
    }
}
