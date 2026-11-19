package com.terminaldetector.drmd.world.fixture.city;

import com.terminaldetector.drmd.world.planet.OrbitalObjectGeometry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OrbitalFieldBlueprintTest {
    @TempDir Path directory;
    private static final CityBlueprint.Point ANCHOR=new CityBlueprint.Point(0,96,0);
    private static final CityBlueprint.Limits LIMITS=new CityBlueprint.Limits(-64,512,-1024,1024,-1024,1024);
    @Test void originalFieldUsesExactlyItsRenderedVoxelDefinitionsAndExplicitAir(){
        var plan=OrbitalFieldBlueprint.create(2,ANCHOR,LIMITS);var all=new HashMap<CityBlueprint.Point,String>();for(var c:plan.cells())assertNull(all.put(c.position(),c.state()));
        assertEquals(3,plan.buildings().size());assertTrue(plan.solidCount()>1000&&plan.solidCount()<CityBlueprint.MAX_SOLID);assertTrue(plan.cutCount()>0&&plan.cutCount()<CityBlueprint.MAX_CUT);
        int expectedVolume=0,expectedSolid=0;
        for(var object:OrbitalFieldBlueprint.objects(ANCHOR)){
            expectedVolume+=object.sizeX()*object.sizeY()*object.sizeZ();var shape=new HashMap<CityBlueprint.Point,String>();
            for(var cell:OrbitalObjectGeometry.cells(object.shape(),object.sizeX(),object.sizeY(),object.sizeZ())){var p=new CityBlueprint.Point(object.origin().x()+cell.x(),object.origin().y()+cell.y(),object.origin().z()+cell.z());shape.put(p,cell.state());expectedSolid++;}
            for(int x=0;x<object.sizeX();x++)for(int y=0;y<object.sizeY();y++)for(int z=0;z<object.sizeZ();z++){
                var p=new CityBlueprint.Point(object.origin().x()+x,object.origin().y()+y,object.origin().z()+z);assertEquals(shape.getOrDefault(p,CityBlueprint.AIR),all.get(p));
            }
        }
        assertEquals(expectedVolume,plan.cells().size());assertEquals(expectedSolid,plan.solidCount());assertEquals(plan,OrbitalFieldBlueprint.create(2,ANCHOR,LIMITS));
    }
    @Test void fieldManifestHasExplicitProvenanceAndCannotBeReadAsAnOldPilot()throws Exception{
        var plan=OrbitalFieldBlueprint.create(2,ANCHOR,LIMITS);var id=new CityBatchJournal.Identity(new UUID(1,2),new UUID(3,4),new UUID(5,6),"minecraft:overworld",plan.hash());
        var manifest=CityManifest.fromPlan(id,71,CityManifest.Backend.STOCK,plan,CityManifest.Kind.ORBITAL_FIELD);var file=directory.resolve("field.manifest");manifest.writeNew(file);
        var read=CityManifest.read(file);assertEquals(CityManifest.Kind.ORBITAL_FIELD,read.kind());assertEquals(plan,read.regenerate());
        assertNotEquals(OrbitalRouteBlueprint.create(2,ANCHOR,LIMITS).hash(),plan.hash());
        var wrong=new CityManifest(read.schema(),read.blueprintVersion(),read.journalSchema(),id,71,2,ANCHOR,LIMITS,read.backend(),plan.solidCount(),plan.cutCount(),CityManifest.Kind.ORBITAL_PILOT);assertThrows(IllegalArgumentException.class,wrong::regenerate);
        assertThrows(IllegalArgumentException.class,()->OrbitalFieldBlueprint.create(2,new CityBlueprint.Point(0,500,0),LIMITS));
    }
}
