package com.terminaldetector.drmd.world.fixture.city;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.UUID;
import java.util.zip.CRC32;
import static org.junit.jupiter.api.Assertions.*;

class CityManifestTest {
    @TempDir Path directory;
    private static final CityBlueprint.Plan PLAN=CityBlueprint.create(711,new CityBlueprint.Point(0,64,0),
        new CityBlueprint.Limits(-64,512,-1024,1024,-1024,1024));
    private static CityBatchJournal.Identity id(UUID save){return new CityBatchJournal.Identity(save,new UUID(6,7),new UUID(8,9),"minecraft:overworld",PLAN.hash());}
    private static CityManifest manifest(){return CityManifest.fromPlan(id(new UUID(4,5)),100,CityManifest.Backend.NATIVE_CUBIC,PLAN);}
    private static CityManifest changed(CityManifest m,long seed,CityBlueprint.Point anchor,int solids){
        return new CityManifest(m.schema(),m.blueprintVersion(),m.journalSchema(),m.identity(),m.worldSeed(),seed,anchor,m.bounds(),m.backend(),solids,m.cutCount());
    }
    @Test void boundedFileRoundTripRegeneratesTheExactPlan() throws IOException {
        var m=manifest();Path file=directory.resolve("current.manifest");m.writeNew(file);assertTrue(Files.size(file)<=16_384);
        var restored=CityManifest.read(file);assertEquals(m,restored);var geometry=restored.regenerate();
        assertEquals(PLAN.cells(),geometry.cells());assertEquals(PLAN.hash(),geometry.hash());assertEquals(PLAN.buildings(),geometry.buildings());
    }
    @Test void sameDimensionSeedOwnerAndGeometryDoNotAuthorizeAnotherSave() throws IOException {
        var m=manifest();Path file=directory.resolve("current.manifest");m.writeNew(file);byte[] original=Files.readAllBytes(file);
        var restored=CityManifest.read(file);
        assertThrows(IllegalArgumentException.class,()->restored.requireContext(new UUID(44,55),restored.identity().world(),restored.worldSeed(),restored.bounds(),restored.backend()));
        restored.requireContext(restored.identity().saveId(),restored.identity().world(),restored.worldSeed(),restored.bounds(),restored.backend());
        assertArrayEquals(original,Files.readAllBytes(file));
    }
    @Test void changedBoundsDimensionWorldSeedOrBackendRefusesResume(){
        var m=manifest();var id=m.identity();
        assertThrows(IllegalArgumentException.class,()->m.requireContext(id.saveId(),"minecraft:the_nether",m.worldSeed(),m.bounds(),m.backend()));
        assertThrows(IllegalArgumentException.class,()->m.requireContext(id.saveId(),id.world(),m.worldSeed()+1,m.bounds(),m.backend()));
        var wider=new CityBlueprint.Limits(m.bounds().bottomY(),m.bounds().topY()+16,m.bounds().minX(),m.bounds().maxX(),m.bounds().minZ(),m.bounds().maxZ());
        assertThrows(IllegalArgumentException.class,()->m.requireContext(id.saveId(),id.world(),m.worldSeed(),wider,m.backend()));
        assertThrows(IllegalArgumentException.class,()->m.requireContext(id.saveId(),id.world(),m.worldSeed(),m.bounds(),CityManifest.Backend.STOCK));
    }
    @Test void unsupportedSchemaAndCorruptOrTruncatedBytesArePreserved() throws IOException {
        var m=manifest();Path file=directory.resolve("current.manifest");m.writeNew(file);byte[] original=Files.readAllBytes(file);
        byte[] unsupported=original.clone();ByteBuffer.wrap(unsupported).putInt(4,77);var crc=new CRC32();crc.update(unsupported,0,unsupported.length-4);
        ByteBuffer.wrap(unsupported).putInt(unsupported.length-4,(int)crc.getValue());Files.write(file,unsupported);
        assertThrows(IOException.class,()->CityManifest.read(file));assertArrayEquals(unsupported,Files.readAllBytes(file));
        byte[] corrupt=original.clone();corrupt[corrupt.length-1]^=1;Files.write(file,corrupt);
        assertThrows(IOException.class,()->CityManifest.read(file));assertArrayEquals(corrupt,Files.readAllBytes(file));
        byte[] truncated={1,2,3};Files.write(file,truncated);assertThrows(IOException.class,()->CityManifest.read(file));assertArrayEquals(truncated,Files.readAllBytes(file));
    }
    @Test void existingOrPendingManifestIsNotOverwritten() throws IOException {
        var m=manifest();Path file=directory.resolve("current.manifest");m.writeNew(file);byte[] original=Files.readAllBytes(file);
        assertThrows(IOException.class,()->m.writeNew(file));assertArrayEquals(original,Files.readAllBytes(file));
        Path second=directory.resolve("other.manifest"),pending=directory.resolve("other.manifest.pending");byte[] partial={5,9,3};Files.write(pending,partial);
        assertThrows(IOException.class,()->m.writeNew(second));assertFalse(Files.exists(second));assertArrayEquals(partial,Files.readAllBytes(pending));
    }
    @Test void oversizedManifestIsRefusedBeforeUnboundedRead() throws IOException {
        Path file=directory.resolve("current.manifest");byte[] bytes=new byte[16_385];Files.write(file,bytes);
        assertThrows(IOException.class,()->CityManifest.read(file));assertArrayEquals(bytes,Files.readAllBytes(file));
    }
    @Test void wrongPlanSeedAnchorCountsAndBlueprintVersionCannotPublish() {
        var m=manifest();
        for(var invalid:new CityManifest[]{changed(m,m.citySeed()+1,m.anchor(),m.solidCount()),
            changed(m,m.citySeed(),new CityBlueprint.Point(1,64,0),m.solidCount()),changed(m,m.citySeed(),m.anchor(),m.solidCount()+1)}){
            assertThrows(IllegalArgumentException.class,invalid::regenerate);
            Path file=directory.resolve(UUID.randomUUID()+".manifest");assertThrows(IllegalArgumentException.class,()->invalid.writeNew(file));assertFalse(Files.exists(file));
        }
        assertThrows(IllegalArgumentException.class,()->new CityManifest(m.schema(),999,m.journalSchema(),m.identity(),m.worldSeed(),m.citySeed(),m.anchor(),m.bounds(),m.backend(),m.solidCount(),m.cutCount()));
    }
}
