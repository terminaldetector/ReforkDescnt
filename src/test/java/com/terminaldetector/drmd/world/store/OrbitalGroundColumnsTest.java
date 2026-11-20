package com.terminaldetector.drmd.world.store;

import com.terminaldetector.drmd.world.fixture.city.CityBlueprint;
import com.terminaldetector.drmd.world.fixture.city.CityBlueprint.Point;
import com.terminaldetector.drmd.world.fixture.city.OrbitalRouteBlueprint;
import com.terminaldetector.drmd.world.fixture.city.OrbitalFieldBlueprint.ObjectSpec;
import com.terminaldetector.drmd.world.planet.OrbitalObjectGeometry.Shape;
import net.minecraft.util.math.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitalGroundColumnsTest {
    @Test void originalRoofSignatureIsColumnLocalAndTheExcludedVolumeHasHalfOpenBounds() {
        var station = new ObjectSpec("abandoned_station", Shape.STATION, new Point(-1124, 1408, 1196), 24, 16, 20);
        var columns = OrbitalGroundColumns.index(List.of(station), shape -> 0xa7a7a7);
        assertEquals(6, columns.size());
        var column = columns.get(ChunkPos.toLong(-70, 74));
        assertNotNull(column);
        assertTrue(column.contains(-1112, 1422, 1198));
        assertFalse(column.contains(-1112, 1413, 1195));
        assertFalse(column.contains(-1112, 1424, 1198));
        assertTrue(column.couldBeOldRoof((short) 1423, 0xa7a7a7));
        assertFalse(column.couldBeOldRoof((short) 1423, 0x101010));
        assertFalse(column.couldBeOldRoof((short) 68, 0xa7a7a7));
    }

    @Test void pilotDeckOnlyExcludesItsExactSolidCellsAndLegacySignatures(){
        var anchor=new Point(256,1408,256);
        var limits=new CityBlueprint.Limits(-16384,16384,-1000,1000,-1000,1000);
        var pilot=OrbitalRouteBlueprint.create(2,anchor,limits);
        var columns=OrbitalGroundColumns.index(List.of(),shape->0,pilot.cells(),state->0x646464);
        var column=columns.get(ChunkPos.toLong(12,15));
        assertNotNull(column);
        assertTrue(column.contains(192,1416,255));
        assertFalse(column.contains(197,1416,255));
        assertFalse(column.contains(192,68,255));
        assertTrue(column.couldBeOldRoof((short)1417,0x646464));
        assertFalse(column.couldBeOldRoof((short)1417,0x447733));
        assertFalse(column.couldBeOldRoof((short)2161,0x646464));
    }

    @Test void revokingAnOldRoofRebuildsItsParentsAndTheUnknownSampleSurvivesClose(@TempDir Path directory) {
        var store = new SurfaceStore(new FileSectionStorage(directory));
        int x = -1120, z = 1192;
        int adjacent = x - 48;
        store.set(x, z, 1423, 0xa7a7a7);
        store.set(adjacent, z, 67, 0x447733);
        while (store.dirtyParentCount() > 0) store.rebuildDirty(4);
        assertEquals(1423, store.heightAt(x, z, 1));
        assertTrue(store.forget(x, z));
        assertFalse(store.forget(x, z));
        var changedParents = new ArrayList<Long>();
        while (store.dirtyParentCount() > 0) store.rebuildDirty(4, changedParents::add);
        assertFalse(changedParents.isEmpty());
        assertEquals(SurfaceSection.NO_HEIGHT, store.heightAt(x, z, 1));
        store.close();
        var reopened = new SurfaceStore(new FileSectionStorage(directory));
        assertEquals(SurfaceSection.NO_HEIGHT, reopened.heightAt(x, z, 0));
        assertEquals(SurfaceSection.NO_HEIGHT, reopened.heightAt(x, z, 1));
        assertEquals(67, reopened.heightAt(adjacent, z, 0));
    }
}
