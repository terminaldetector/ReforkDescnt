package com.terminaldetector.drmd.world.enclave;

import com.terminaldetector.drmd.world.fixture.city.CityBlueprint;
import com.terminaldetector.drmd.world.fixture.city.OrbitalFieldBlueprint;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class OrbitalFieldMissionDataTest {
    private static EnclaveMissionContract definition()throws IOException{
        try(var in=OrbitalFieldMissionDataTest.class.getResourceAsStream("/data/drmd/missions/orbital_field_salvage.json")){
            assertNotNull(in,"actual authored server-data resource is present");return EnclaveMissionContract.parse(in.readAllBytes());
        }
    }
    @Test void existingMissionAdapterTargetsActualAccessibleAuthoredAsteroidStone()throws IOException{
        var definition=definition();assertEquals("EXPLORE_SHAFT",definition.offeredKind());assertEquals(5,definition.objectives().size());
        var anchor=new CityBlueprint.Point(0,96,0);var plan=OrbitalFieldBlueprint.create(2,anchor,new CityBlueprint.Limits(-64,512,-1024,1024,-1024,1024));var cells=new HashMap<CityBlueprint.Point,String>();for(var c:plan.cells())cells.put(c.position(),c.state());
        int targets=0;for(var step:definition.at(new EnclaveMissionContract.Point(0,96,0)))if(step.type()==EnclaveMissionContract.Type.BREAK_BLOCK){
            targets++;var p=step.block();assertEquals(step.expectedBlock(),cells.get(new CityBlueprint.Point(p.x(),p.y(),p.z())));
            assertEquals("minecraft:stone",step.expectedBlock());var outside=cells.get(new CityBlueprint.Point(p.x(),p.y(),p.z()-1));assertTrue(outside==null||outside.equals(CityBlueprint.AIR),"mineable front shell, not a buried artificial objective");
        }
        assertEquals(2,targets);
    }
    @Test void allGridAlignmentsStayWithinExistingCompact27CubeAdmission()throws IOException{
        var definition=definition();
        for(int ax=0;ax<16;ax++)for(int ay=0;ay<16;ay++)for(int az=0;az<16;az++){
            var cubes=new HashSet<String>();cubes.add(cube(ax,ay,az));
            for(var step:definition.at(new EnclaveMissionContract.Point(ax,ay,az))){
                if(step.type()==EnclaveMissionContract.Type.BREAK_BLOCK){var p=step.block();cubes.add(cube(p.x(),p.y(),p.z()));continue;}
                var b=step.volume();for(int x=Math.floorDiv(b.min().x(),16);x<=Math.floorDiv(b.max().x()-1,16);x++)for(int y=Math.floorDiv(b.min().y(),16);y<=Math.floorDiv(b.max().y()-1,16);y++)for(int z=Math.floorDiv(b.min().z(),16);z<=Math.floorDiv(b.max().z()-1,16);z++)cubes.add(x+":"+y+":"+z);
            }
            assertTrue(cubes.size()+1<=27,"bound all objective cubes plus an independently placed real Herald cube");
        }
    }
    private static String cube(int x,int y,int z){return Math.floorDiv(x,16)+":"+Math.floorDiv(y,16)+":"+Math.floorDiv(z,16);}
}
