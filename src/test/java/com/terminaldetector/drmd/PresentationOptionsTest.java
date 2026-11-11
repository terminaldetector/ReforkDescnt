package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.presentation.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class PresentationOptionsTest {
    @Test void oldSettingsRetainNativeAndDoNotChangeIndependentHybridOrPhysicsChoices() {
        var p = new Properties(); p.setProperty("hybridRender", "false"); p.setProperty("physicsProfile", "STANDARD");
        assertEquals(new PresentationOptions(PresentationProfile.HYBRID_NATIVE, 360), PresentationOptions.read(p));
        assertEquals("false", p.getProperty("hybridRender")); assertEquals("STANDARD", p.getProperty("physicsProfile"));
    }
    @Test void allRuntimeProfilesAndCustomResolutionSurviveStoredProperties() throws IOException {
        for (var profile : PresentationProfile.values()) {
            var p = new Properties(); var choice = new PresentationOptions(profile, 540); choice.write(p);
            var out = new ByteArrayOutputStream(); p.store(out, ""); var loaded = new Properties();
            loaded.load(new ByteArrayInputStream(out.toByteArray())); assertEquals(choice, PresentationOptions.read(loaded));
        }
    }
    @Test void invalidLegacyAndUnboundedValuesHaveSafeDeterministicFallbacks() {
        var p = new Properties(); p.setProperty("presentationProfile", "unknown"); p.setProperty("ps1SceneHeight", "NaN");
        assertEquals(new PresentationOptions(PresentationProfile.HYBRID_NATIVE, 360), PresentationOptions.read(p));
        p.setProperty("presentationProfile", " ps1_hybrid ");p.setProperty("ps1SceneHeight", "2147483647");
        assertEquals(new PresentationOptions(PresentationProfile.PS1_HYBRID, 1080), PresentationOptions.read(p));
        assertEquals(90, new PresentationOptions(PresentationProfile.PS1_HYBRID, -1).sceneHeight());
    }
    @Test void defaultsReallyYield640By360AndWindowAspectIsPreserved() {
        assertEquals(new PresentationSize(640, 360), PresentationSize.fit(1920, 1080, 360, 16384));
        assertEquals(new PresentationSize(640, 360), PresentationSize.fit(1280, 720, 360, 16384));
        assertEquals(new PresentationSize(450, 360), PresentationSize.fit(1280, 1024, 360, 16384));
        assertEquals(new PresentationSize(640, 640), PresentationSize.fit(1920, 1920, 640, 16384));
    }
    @Test void resizeMinimizeSmallWindowsAndGpuBudgetsAreBounded() {
        assertFalse(PresentationSize.fit(0, 1080, 360, 16384).valid());
        assertEquals(new PresentationSize(320, 200), PresentationSize.fit(320, 200, 360, 16384));
        for (int w : new int[]{1,320,1920,Integer.MAX_VALUE}) for(int h : new int[]{1,200,1080,Integer.MAX_VALUE}) {
            var size = PresentationSize.fit(w,h,1080,2048);
            assertTrue(size.valid() && size.width()<=w && size.height()<=h);
            assertTrue(size.width()<=2048 && size.height()<=2048 && (long)size.width()*size.height()<=PresentationSize.MAX_PIXELS);
        }
    }
}
