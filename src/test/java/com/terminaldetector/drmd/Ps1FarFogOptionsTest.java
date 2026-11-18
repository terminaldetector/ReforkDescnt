package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.presentation.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Ps1FarFogOptionsTest {
    @Test void missingOldKeyStaysNeutralWithoutChangingExistingChoices(){
        var p=new Properties();p.setProperty("presentationProfile","HYBRID_NATIVE");p.setProperty("ps1VertexSnapPixels","1.7");p.setProperty("ps1AffineAmount",".13");p.setProperty("hybridRender","false");
        var before=new Properties();before.putAll(p);assertEquals(new Ps1FarFogOptions(0),Ps1FarFogOptions.read(p));assertEquals(before,p);
    }
    @Test void realPropertiesRoundTripPreservesDensityAndOtherControls() throws IOException {
        for(float density:new float[]{0,.13f,.25f,.5f,1,1.7f,2}){
            var p=new Properties();p.setProperty("ps1VertexSnapPixels","1.7");p.setProperty("ps1AffineAmount",".13");p.setProperty("presentationProfile","RENDER_DEBUG");
            new Ps1FarFogOptions(density).write(p);var bytes=new ByteArrayOutputStream();p.store(bytes,"");var restored=new Properties();restored.load(new ByteArrayInputStream(bytes.toByteArray()));
            assertEquals(new Ps1FarFogOptions(density),Ps1FarFogOptions.read(restored));assertEquals(new Ps1GeometryOptions(1.7f,.13f),Ps1GeometryOptions.read(restored));assertEquals("RENDER_DEBUG",restored.getProperty("presentationProfile"));
        }
    }
    @Test void nonfiniteMalformedAndExtremeSettingsNeverReachTheGpu(){
        for(String value:new String[]{"NaN","Infinity","-Infinity","broken",""}){var p=new Properties();p.setProperty("ps1FarFogDensity",value);assertEquals(0,Ps1FarFogOptions.read(p).density());}
        assertEquals(2,new Ps1FarFogOptions(Float.MAX_VALUE).density());assertEquals(0,new Ps1FarFogOptions(-1).density());
    }
    @Test void everyUnsupportedPhaseExplicitlyReturnsZeroRatherThanPreviousDensity(){
        var value=new Ps1FarFogOptions(1.7f);
        for(var profile:PresentationProfile.values())for(boolean world:new boolean[]{false,true})for(boolean offscreen:new boolean[]{false,true})
            assertEquals(profile==PresentationProfile.PS1_HYBRID&&world&&!offscreen?1.7f:0,value.effective(profile,world,offscreen));
    }
    @Test void liveMenuCycleReturnsToOffAndDoesNotRequireAPresetToBeStored(){
        var value=new Ps1FarFogOptions(0);var seen=new HashSet<Float>();
        for(int i=0;i<5;i++){seen.add(value.density());value=new Ps1FarFogOptions(value.nextDensity());}
        assertEquals(Set.of(0f,.25f,.5f,1f,2f),seen);assertEquals(0,value.density());assertEquals(.5f,new Ps1FarFogOptions(.31f).nextDensity());
    }
}
