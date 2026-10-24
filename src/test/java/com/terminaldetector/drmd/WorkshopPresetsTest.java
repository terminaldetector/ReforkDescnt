package com.terminaldetector.drmd;

import com.terminaldetector.drmd.workshop.*;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopPresetsTest {
    @TempDir Path directory;
    @Test void savesLoadsAndReplacesWholeConstructionAndBehaviorDraft() throws Exception {
        var c=WeaponConfig.fromExistingWeapon("plasma");
        c.clusters.clear(); c.clusters.addAll(HybridWeaponModels.sample(HybridWeaponModels.PLASMA));
        var m=c.clusters.getFirst(); m.pitch=12; m.yaw=-7; m.roll=31;
        m.scaleX=.7f; m.scaleY=1.3f; m.scaleZ=1.8f; m.colorR=143; m.bobAmp=2; m.bobFreq=3;
        c.weapon.put("Damage",77f); c.projectile.put("InitialVelocity",4567f);
        WorkshopPresets.save(directory,"plasma",c);
        c.clusters.clear();
        var loaded=WorkshopPresets.load(directory,"plasma");
        assertEquals("plasma",loaded.targetWeaponId); assertEquals(2,loaded.clusters.size());
        var n=loaded.clusters.getFirst();
        assertEquals(12,n.pitch); assertEquals(-7,n.yaw); assertEquals(31,n.roll);
        assertEquals(.7f,n.scaleX); assertEquals(1.3f,n.scaleY); assertEquals(1.8f,n.scaleZ);
        assertEquals(143,n.colorR); assertEquals(2,n.bobAmp); assertEquals(3,n.bobFreq);
        assertEquals(77f,loaded.weapon.get("Damage"));
        assertEquals(4567f,loaded.projectile.get("InitialVelocity"));
        loaded.clusters.getFirst().name="Replacement";
        WorkshopPresets.save(directory,"plasma",loaded);
        assertEquals("Replacement",WorkshopPresets.load(directory,"plasma").clusters.getFirst().name);
    }
    @Test void presetNameCannotEscapeThePresetDirectory() {
        assertThrows(IllegalArgumentException.class,()->WorkshopPresets.save(directory,"../world",new WeaponConfig()));
    }
    @Test void legacyPartialNbtGetsOriginalDefaults() {
        var old=new NbtCompound(); old.putString("model","barrel"); old.putFloat("fwd",22);
        var m=ClusterModule.fromNbt(old);
        assertEquals(22,m.posFwd); assertEquals(1,m.scaleX); assertEquals(255,m.colorR); assertEquals(0,m.kick);
    }
}
