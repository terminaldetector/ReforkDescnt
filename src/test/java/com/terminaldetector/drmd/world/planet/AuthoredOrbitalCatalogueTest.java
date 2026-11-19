package com.terminaldetector.drmd.world.planet;

import com.terminaldetector.drmd.world.fixture.city.CityBlueprint;
import com.terminaldetector.drmd.world.fixture.city.OrbitalFieldBlueprint;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AuthoredOrbitalCatalogueTest {
    private static NbtCompound raw(int count){
        var raw=new NbtCompound();raw.putInt("schema",1);raw.putInt("geometry",1);var list=new NbtList();
        for(int i=0;i<count;i++){
            var job=new UUID(1,i+1);var object=OrbitalFieldBlueprint.objects(new CityBlueprint.Point(i*256,96,0)).getFirst();var p=object.origin();var n=new NbtCompound();
            n.putUuid("id",AuthoredOrbitalCatalogue.objectId(job,object.id()));n.putUuid("job",job);n.putString("dimension","minecraft:overworld");n.putLong("seed",71);n.putString("hash","a".repeat(64));n.putString("name",object.id());n.putString("shape",object.shape().name());n.putInt("x",p.x());n.putInt("y",p.y());n.putInt("z",p.z());n.putInt("sx",object.sizeX());n.putInt("sy",object.sizeY());n.putInt("sz",object.sizeZ());n.putBoolean("valid",true);list.add(n);
        }
        raw.put("objects",list);return raw;
    }
    @Test void actualEditInvalidationIsPersistedWithoutResurrectingPristineTemplate(){
        var state=AuthoredOrbitalCatalogue.read(raw(1),null);assertTrue(state.available());var object=state.entries().getFirst().object();var p=object.origin();
        assertFalse(state.invalidateAt(p.x()-1,p.y(),p.z()));assertTrue(state.entries().getFirst().valid());
        assertTrue(state.invalidateAt(p.x(),p.y(),p.z()));assertFalse(state.entries().getFirst().valid());assertFalse(state.invalidateAt(p.x(),p.y(),p.z()));
        var restored=AuthoredOrbitalCatalogue.read(state.writeNbt(new NbtCompound(),null),null);assertEquals(state.entries(),restored.entries());assertFalse(restored.entries().getFirst().valid());
    }
    @Test void futureOrMalformedCatalogueRetainsRawDataWithoutProxyAuthority(){
        for(int kind=0;kind<3;kind++){
            var raw=raw(1);if(kind==0)raw.putInt("schema",99);else if(kind==1)raw.putInt("geometry",99);else ((NbtCompound)((NbtList)raw.get("objects")).getFirst()).putString("shape","UNRECOGNIZED");
            var read=AuthoredOrbitalCatalogue.read(raw,null);assertFalse(read.available());assertTrue(read.entries().isEmpty());assertFalse(read.invalidateAt(0,96,0));assertEquals(raw,read.writeNbt(new NbtCompound(),null));
        }
    }
    @Test void duplicateOrOverBudgetRecordsCannotBecomeDistantAuthority(){
        assertTrue(AuthoredOrbitalCatalogue.read(raw(16),null).available());assertFalse(AuthoredOrbitalCatalogue.read(raw(17),null).available());
        var duplicated=raw(1);var list=(NbtList)duplicated.get("objects");list.add(list.getFirst().copy());assertFalse(AuthoredOrbitalCatalogue.read(duplicated,null).available());
        var spoofed=raw(1);((NbtCompound)((NbtList)spoofed.get("objects")).getFirst()).putUuid("id",UUID.randomUUID());assertFalse(AuthoredOrbitalCatalogue.read(spoofed,null).available());
    }
}
