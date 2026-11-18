package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.presentation.*;
import com.terminaldetector.drmd.entity.model.PyroShipGeometry;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Ps1AssetProfileTest {
    @Test void oldPropertiesKeepOriginalAssetsAndIndependentChoicesRoundTrip() throws IOException {
        var p=new Properties();p.setProperty("ps1FarFogDensity","0.7");p.setProperty("ps1VertexSnapPixels","1.7");
        assertEquals(Ps1AssetProfile.NATIVE,Ps1AssetProfile.read(p));assertEquals(0,Ps1FramePacingOptions.read(p).fps());
        Ps1AssetProfile.LOW_POLY.write(p);new Ps1FramePacingOptions(30).write(p);
        var bytes=new ByteArrayOutputStream();p.store(bytes,"");var restored=new Properties();restored.load(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(Ps1AssetProfile.LOW_POLY,Ps1AssetProfile.read(restored));assertEquals(30,Ps1FramePacingOptions.read(restored).fps());
        assertEquals("0.7",restored.getProperty("ps1FarFogDensity"));assertEquals("1.7",restored.getProperty("ps1VertexSnapPixels"));
        p.setProperty("ps1AssetProfile","unknown future model");assertEquals(Ps1AssetProfile.NATIVE,Ps1AssetProfile.read(p));
    }
    @Test void onlyActualPs1WorldCanSelectTheCoarseAsset(){
        for(var asset:Ps1AssetProfile.values())for(var profile:PresentationProfile.values())for(boolean world:new boolean[]{false,true})for(boolean offscreen:new boolean[]{false,true})
            assertEquals(asset==Ps1AssetProfile.LOW_POLY&&profile==PresentationProfile.PS1_HYBRID&&world&&!offscreen,asset.eligible(profile,world,offscreen));
        assertEquals(Ps1AssetProfile.NATIVE,Ps1AssetProfile.LOW_POLY.next());
    }
    @Test void realCoarseGeometryRetainsPrimaryMountsAndUvsWhileRemovingFifteenCosmeticBoxes(){
        assertEquals(32,PyroShipGeometry.cuboids(Ps1AssetProfile.NATIVE));assertEquals(384,PyroShipGeometry.triangles(Ps1AssetProfile.NATIVE));
        assertEquals(17,PyroShipGeometry.cuboids(Ps1AssetProfile.LOW_POLY));assertEquals(204,PyroShipGeometry.triangles(Ps1AssetProfile.LOW_POLY));
        var original=new HashMap<String,PyroShipGeometry.Part>();for(var p:PyroShipGeometry.parts(Ps1AssetProfile.NATIVE))original.put(p.name(),p);
        var remaining=new HashSet<String>();
        for(var part:PyroShipGeometry.parts(Ps1AssetProfile.LOW_POLY)){
            var full=original.get(part.name());assertEquals(full.boxes().getFirst(),part.boxes().getFirst());
            assertEquals(full.parent(),part.parent());assertEquals(full.x(),part.x());assertEquals(full.y(),part.y());assertEquals(full.z(),part.z());
            assertEquals(full.pitch(),part.pitch());assertEquals(full.yaw(),part.yaw());assertEquals(full.roll(),part.roll());
            assertTrue(part.parent().isEmpty()||remaining.contains(part.parent()));
            for(var box:part.boxes()){assertFalse(box.cosmetic());assertTrue(full.boxes().contains(box));assertTrue(box.width()>0&&box.height()>0&&box.depth()>0);}
            remaining.add(part.name());
        }
        assertEquals(Set.of("fuselage","wing_l","wing_r","laser_l","laser_r","missile_l","missile_r","top_pod","top_pod_tubes","bottom_rig","bottom_rig_emitter","fin","thruster_l","thruster_r"),remaining);
        assertEquals(new PyroShipGeometry.Box(2,2,-2.5f,-2.5f,-4f,5f,5f,10f,false),original.get("fuselage").boxes().getFirst());
        assertEquals(-2f,original.get("wing_l").x());assertEquals(.12f,original.get("wing_l").roll());
        assertThrows(UnsupportedOperationException.class,()->PyroShipGeometry.parts(Ps1AssetProfile.LOW_POLY).clear());
        assertEquals(32,PyroShipGeometry.cuboids(Ps1AssetProfile.NATIVE));
    }
}
