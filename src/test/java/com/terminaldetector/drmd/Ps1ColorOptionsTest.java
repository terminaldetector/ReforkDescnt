package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.presentation.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Ps1ColorOptionsTest {
    @Test void missingColourFieldsSupplyThePs1PresetWithoutChangingExistingChoices() {
        var p = new Properties(); p.setProperty("presentationProfile", "HYBRID_NATIVE");
        p.setProperty("ps1SceneHeight", "540"); p.setProperty("hybridRender", "false"); p.setProperty("physicsProfile", "EXPANDED");
        var original = new Properties(); original.putAll(p);
        assertEquals(new Ps1ColorOptions(32, .5f), Ps1ColorOptions.read(p));
        assertEquals(original, p);
        assertEquals(new PresentationOptions(PresentationProfile.HYBRID_NATIVE, 540), PresentationOptions.read(p));
    }

    @Test void rawAndCustomColourChoicesSurviveActualStoredProperties() throws IOException {
        for (int levels : new int[]{2, 8, 16, 32, 64, 111, 256}) for (float strength : new float[]{0, .13f, .25f, .5f, 1}) {
            var choice = new Ps1ColorOptions(levels, strength); var p = new Properties(); choice.write(p);
            var bytes = new ByteArrayOutputStream(); p.store(bytes, ""); var restored = new Properties();
            restored.load(new ByteArrayInputStream(bytes.toByteArray()));
            assertEquals(choice, Ps1ColorOptions.read(restored));
            assertEquals(levels < 256, choice.enabled());
        }
    }

    @Test void malformedNonfiniteAndUnboundedChoicesNeverReachTheShader() {
        var p = new Properties(); p.setProperty("ps1ColorLevels", "broken"); p.setProperty("ps1DitherStrength", "NaN");
        assertEquals(new Ps1ColorOptions(32, .5f), Ps1ColorOptions.read(p));
        p.setProperty("ps1ColorLevels", "2147483647"); p.setProperty("ps1DitherStrength", "Infinity");
        assertEquals(new Ps1ColorOptions(256, .5f), Ps1ColorOptions.read(p));
        p.setProperty("ps1ColorLevels", "-100"); p.setProperty("ps1DitherStrength", "-3");
        assertEquals(new Ps1ColorOptions(2, 0), Ps1ColorOptions.read(p));
        assertEquals(1, new Ps1ColorOptions(32, Float.MAX_VALUE).ditherStrength());
        assertEquals(.5f, new Ps1ColorOptions(32, Float.NEGATIVE_INFINITY).ditherStrength());
    }

    @Test void menuCyclesReachRawAndReturnToThePresetWithoutClearingIndependentStrength() {
        var seen = new HashSet<Integer>(); var options = new Ps1ColorOptions(32, .13f);
        for (int i = 0; i < 6; i++) { seen.add(options.levels()); options = new Ps1ColorOptions(options.nextLevels(), options.ditherStrength()); }
        assertEquals(Set.of(8, 16, 32, 64, 256), seen); assertEquals(new Ps1ColorOptions(16, .13f), options);
        var strength = new Ps1ColorOptions(32, .5f);
        for (int i = 0; i < 5; i++) strength = new Ps1ColorOptions(strength.levels(), strength.nextDither());
        assertEquals(.5f, strength.ditherStrength());
    }
}
