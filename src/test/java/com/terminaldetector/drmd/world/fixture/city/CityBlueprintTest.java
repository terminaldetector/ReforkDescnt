package com.terminaldetector.drmd.world.fixture.city;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure content acceptance: real distinct geometry and routes, not a checksum-only stub. */
class CityBlueprintTest {
    private static final CityBlueprint.Limits LIMITS=new CityBlueprint.Limits(-64,512,-1_024,1_024,-1_024,1_024);
    private static CityBlueprint.Plan plan(long seed){return CityBlueprint.create(seed,new CityBlueprint.Point(0,64,0),LIMITS);}

    @Test void identicalInputsProduceIdenticalImmutableGeometryAndHash(){
        var first=plan(801);var second=plan(801);assertEquals(first.hash(),second.hash());assertEquals(first.cells(),second.cells());
        assertThrows(UnsupportedOperationException.class,()->first.cells().clear());
        assertThrows(UnsupportedOperationException.class,()->first.buildings().clear());
    }
    @Test void eightOriginalArchetypesHaveTwoRealInstancesAndUsableOpenings(){
        var p=plan(812);assertEquals(16,p.buildings().size());var counts=new EnumMap<CityBlueprint.Archetype,Integer>(CityBlueprint.Archetype.class);
        var states=new HashMap<CityBlueprint.Point,String>();for(var cell:p.cells())states.put(cell.position(),cell.state());
        for(var building:p.buildings()){
            counts.merge(building.archetype(),1,Integer::sum);assertTrue(building.solidCount()>0&&building.solidCount()<=2_048);
            assertNotEquals(building.entry(),building.exit());assertEquals("minecraft:air",states.get(building.entry()));assertEquals("minecraft:air",states.get(building.exit()));
        }
        assertEquals(8,counts.size());assertTrue(counts.values().stream().allMatch(n->n==2));
    }
    @Test void sparseFootprintRespectsBothMaterialCapsAndActualWorldBounds(){
        var p=plan(833);assertTrue(p.solidCount()>1_000&&p.solidCount()<=65_536);assertTrue(p.cutCount()>0&&p.cutCount()<=65_536);
        assertEquals(p.solidCount()+p.cutCount(),p.cells().size());assertEquals(p.cells().size(),p.cells().stream().map(CityBlueprint.Cell::position).distinct().count());
        assertTrue(p.cells().stream().allMatch(c->CityBlueprint.PALETTE.contains(c.state())));
        assertTrue(p.cells().stream().allMatch(c->c.position().x()>=-128&&c.position().x()<128&&c.position().z()>=-128&&c.position().z()<128&&c.position().y()>=40&&c.position().y()<=111));
    }
    @Test void seedChangesActualContentWhilePreservingSixteenLotsAndBudgets(){
        var first=plan(100);var second=plan(101);assertNotEquals(first.cells(),second.cells());assertNotEquals(first.hash(),second.hash());
        assertEquals(16,first.buildings().size());assertEquals(16,second.buildings().size());assertTrue(second.solidCount()<=65_536&&second.cutCount()<=65_536);
    }
    @Test void invalidVerticalBorderAndOverflowingAnchorsFailBeforeAnyAuthoring(){
        assertThrows(IllegalArgumentException.class,()->CityBlueprint.create(0,new CityBlueprint.Point(0,-63,0),LIMITS));
        assertThrows(IllegalArgumentException.class,()->CityBlueprint.create(0,new CityBlueprint.Point(1_000,64,0),LIMITS));
        assertThrows(IllegalArgumentException.class,()->CityBlueprint.create(0,new CityBlueprint.Point(Integer.MAX_VALUE,64,0),LIMITS));
        assertThrows(IllegalArgumentException.class,()->new CityBlueprint.Limits(100,99,-1,1,-1,1));
    }
}
