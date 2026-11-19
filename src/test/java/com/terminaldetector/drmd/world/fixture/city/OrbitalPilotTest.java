package com.terminaldetector.drmd.world.fixture.city;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrbitalPilotTest {
    @TempDir Path directory;
    private static final CityBlueprint.Point ANCHOR=new CityBlueprint.Point(0,96,0);
    private static final CityBlueprint.Limits LIMITS=new CityBlueprint.Limits(-64,512,-1024,1024,-1024,1024);
    private static CityBlueprint.Plan orbital(){return OrbitalRouteBlueprint.create(2,ANCHOR,LIMITS);}
    private static CityBatchJournal.Identity identity(CityBlueprint.Plan plan){return new CityBatchJournal.Identity(new UUID(1,2),new UUID(3,4),new UUID(5,6),"minecraft:overworld",plan.hash());}
    private static OrbitalRouteProgress.Position at(OrbitalRouteBlueprint.Gate gate){var p=gate.position();return new OrbitalRouteProgress.Position(p.x()+.5,p.y(),p.z()+.5);}

    @Test void originalRingAndCitadelStayBoundedAndHaveRealClearGateCells(){
        var plan=orbital();assertEquals(10,plan.buildings().size());
        assertTrue(plan.solidCount()>1000&&plan.solidCount()<=CityBlueprint.MAX_SOLID);assertTrue(plan.cutCount()>0&&plan.cutCount()<=CityBlueprint.MAX_CUT);
        var cells=new HashMap<CityBlueprint.Point,String>();for(var cell:plan.cells())assertNull(cells.put(cell.position(),cell.state()));
        for(var gate:OrbitalRouteBlueprint.gates(ANCHOR))for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<3;y++){
            var p=gate.position();assertEquals(CityBlueprint.AIR,cells.get(new CityBlueprint.Point(p.x()+x,p.y()+y,p.z()+z)),"authored gate passage "+gate.id());
        }
        assertThrows(IllegalArgumentException.class,()->OrbitalRouteBlueprint.create(2,new CityBlueprint.Point(0,-60,0),LIMITS));
    }
    @Test void authoredGeometryIsDeterministicWithoutChangingTheOriginalCityFactory(){
        var a=orbital();assertEquals(a,OrbitalRouteBlueprint.create(2,ANCHOR,LIMITS));
        assertNotEquals(a.cells(),OrbitalRouteBlueprint.create(3,ANCHOR,LIMITS).cells());
        assertEquals(CityBlueprint.create(2,ANCHOR,LIMITS),CityManifest.Kind.CITY.create(2,ANCHOR,LIMITS));
        assertThrows(UnsupportedOperationException.class,()->a.cells().clear());
    }
    @Test void legacyCitySchemaOneReadsExactGeometryAndPreservesItsFileBytes()throws Exception{
        var plan=CityBlueprint.create(2,ANCHOR,LIMITS);
        var legacy=new CityManifest(1,CityBlueprint.VERSION,CityBatchJournal.VERSION,identity(plan),71,2,ANCHOR,LIMITS,CityManifest.Backend.STOCK,plan.solidCount(),plan.cutCount());
        var file=directory.resolve("legacy.manifest");legacy.writeNew(file);byte[] bytes=Files.readAllBytes(file);
        var restored=CityManifest.read(file);assertEquals(1,restored.schema());assertEquals(CityManifest.Kind.CITY,restored.kind());assertEquals(plan,restored.regenerate());
        assertArrayEquals(bytes,Files.readAllBytes(file));var rewritten=directory.resolve("legacy-copy.manifest");restored.writeNew(rewritten);assertArrayEquals(bytes,Files.readAllBytes(rewritten));
    }
    @Test void orbitalManifestRestoresItsOwnTemplateInsteadOfRegeneratingCity()throws Exception{
        var plan=orbital();var manifest=CityManifest.fromPlan(identity(plan),71,CityManifest.Backend.NATIVE_CUBIC,plan,CityManifest.Kind.ORBITAL_PILOT);
        var file=directory.resolve("orbital.manifest");manifest.writeNew(file);var restored=CityManifest.read(file);
        assertEquals(CityManifest.Kind.ORBITAL_PILOT,restored.kind());assertEquals(plan,restored.regenerate());
        assertNotEquals(CityBlueprint.create(2,ANCHOR,LIMITS).hash(),restored.regenerate().hash());
        var wrong=new CityManifest(manifest.schema(),manifest.blueprintVersion(),manifest.journalSchema(),manifest.identity(),71,2,ANCHOR,LIMITS,manifest.backend(),plan.solidCount(),plan.cutCount(),CityManifest.Kind.CITY);
        assertThrows(IllegalArgumentException.class,wrong::regenerate);
        assertThrows(IllegalArgumentException.class,()->new CityManifest(1,1,CityBatchJournal.VERSION,identity(plan),71,2,ANCHOR,LIMITS,manifest.backend(),plan.solidCount(),plan.cutCount(),CityManifest.Kind.ORBITAL_PILOT));
    }
    @Test void routeOnlyAcceptsTheNextNearClearGateOnARealLaterTick(){
        var gates=OrbitalRouteBlueprint.gates(ANCHOR);var progress=new OrbitalRouteProgress(gates);
        assertEquals(0,progress.observe(at(gates.get(1)),10,true).passed());
        assertEquals(1,progress.observe(at(gates.getFirst()),11,true).passed());
        assertEquals(1,progress.observe(at(gates.get(1)),11,true).passed());
        assertEquals(2,progress.observe(at(gates.get(1)),12,true).passed());
    }
    @Test void unavailableOrBlockedGateDoesNotStartOrAdvanceItsClock(){
        var gates=OrbitalRouteBlueprint.gates(ANCHOR);var progress=new OrbitalRouteProgress(gates);
        assertEquals(new OrbitalRouteProgress.Snapshot(0,gates.size(),0,false),progress.observe(at(gates.getFirst()),100,false));
        assertEquals(1,progress.observe(at(gates.getFirst()),200,true).passed());
        assertEquals(1,progress.observe(at(gates.get(1)),201,false).passed());
        assertThrows(IllegalArgumentException.class,()->progress.observe(at(gates.get(1)),-1,true));
    }
    @Test void completedRouteKeepsMeasuredTimeAndCannotMintAnotherCompletion(){
        var gates=OrbitalRouteBlueprint.gates(ANCHOR);var progress=new OrbitalRouteProgress(gates);long tick=100;
        for(var gate:gates){progress.observe(at(gate),tick,true);tick+=20;}
        var done=progress.snapshot(tick);assertTrue(done.complete());assertEquals((gates.size()-1)*20,done.elapsedTicks());
        assertEquals(done,progress.observe(at(gates.getFirst()),tick+1000,true));
        assertThrows(IllegalArgumentException.class,()->new OrbitalRouteProgress.Position(Double.NaN,0,0));
    }
}
