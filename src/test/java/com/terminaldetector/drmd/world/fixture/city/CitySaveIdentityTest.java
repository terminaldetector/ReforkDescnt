package com.terminaldetector.drmd.world.fixture.city;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CitySaveIdentityTest {
    @TempDir Path directory;

    @Test void independentSavesHaveDistinctPersistentIdsAndExplicitReopenPreservesThem() throws IOException {
        Path a=Files.createDirectory(directory.resolve("save-a")),b=Files.createDirectory(directory.resolve("save-b"));UUID first,second;
        try(var lease=CitySaveIdentity.create(a)){first=lease.saveId();}
        try(var lease=CitySaveIdentity.create(b)){second=lease.saveId();}assertNotEquals(first,second);
        try(var lease=CitySaveIdentity.openExisting(a)){assertEquals(first,lease.saveId());}
        try(var lease=CitySaveIdentity.openExisting(b)){assertEquals(second,lease.saveId());}
    }
    @Test void inspectOpenDoesNotProvisionMissingDirectoriesOrIdentity() throws IOException {
        assertThrows(IOException.class,()->CitySaveIdentity.openExisting(directory));
        try(var files=Files.list(directory)){assertEquals(0,files.count());}
    }
    @Test void corruptIdentityIsRefusedWithoutReplacement() throws IOException {
        Path id;try(var lease=CitySaveIdentity.create(directory)){id=lease.directory().resolve("save-id.bin");}
        byte[] bytes=Files.readAllBytes(id);bytes[bytes.length-1]^=1;Files.write(id,bytes);
        assertThrows(IOException.class,()->CitySaveIdentity.create(directory));assertArrayEquals(bytes,Files.readAllBytes(id));
        assertThrows(IOException.class,()->CitySaveIdentity.openExisting(directory));assertArrayEquals(bytes,Files.readAllBytes(id));
    }
    @Test void onlyOneLeaseCanOwnTheSaveAndCloseReleasesIt() throws IOException {
        UUID id;try(var lease=CitySaveIdentity.create(directory)){
            id=lease.saveId();assertThrows(IOException.class,()->CitySaveIdentity.openExisting(directory));
            assertThrows(IOException.class,()->CitySaveIdentity.create(directory));
        }
        try(var lease=CitySaveIdentity.openExisting(directory)){assertEquals(id,lease.saveId());}
    }
    @Test void missingIdentityBesideExistingOrPartialCityDataIsNotReprovisioned() throws IOException {
        Path root=Files.createDirectories(directory.resolve("drmd/city"));byte[] prior={4,1,8,2};
        Files.write(root.resolve("current.manifest"),prior);
        assertThrows(IOException.class,()->CitySaveIdentity.create(directory));assertFalse(Files.exists(root.resolve("save-id.bin")));
        assertArrayEquals(prior,Files.readAllBytes(root.resolve("current.manifest")));
        Files.delete(root.resolve("current.manifest"));Files.write(root.resolve("save-id.bin.pending"),prior);
        assertThrows(IOException.class,()->CitySaveIdentity.create(directory));assertFalse(Files.exists(root.resolve("save-id.bin")));
        assertArrayEquals(prior,Files.readAllBytes(root.resolve("save-id.bin.pending")));
    }
    @Test void missingSaveRootIsNeverInventedByTheFixture() {
        Path missing=directory.resolve("not-a-save");assertThrows(IOException.class,()->CitySaveIdentity.create(missing));
        assertFalse(Files.exists(missing));
    }
}
