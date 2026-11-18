package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.presentation.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Ps1GeometryOptionsTest {
    @Test void oldConfigDefaultsOffAndDoesNotChangeIndependentChoices() {
        var p = new Properties(); p.setProperty("presentationProfile", "HYBRID_NATIVE");
        p.setProperty("ps1ColorLevels", "111"); p.setProperty("ps1DitherStrength", ".13");
        p.setProperty("hybridRender", "false"); p.setProperty("cloudAltitudeEnabled", "false");
        var before = new Properties(); before.putAll(p);
        assertEquals(new Ps1GeometryOptions(0, 0), Ps1GeometryOptions.read(p));
        assertEquals(before, p);
        assertFalse(Ps1GeometryOptions.read(p).enabled());
    }

    @Test void actualPropertiesStoragePreservesEachKnobWhenTheOtherIsOff() throws IOException {
        for (float snap : new float[]{0, .25f, .5f, 1, 1.7f, 4}) for (float affine : new float[]{0, .13f, .5f, 1}) {
            var wanted = new Ps1GeometryOptions(snap, affine); var p = new Properties(); wanted.write(p);
            var bytes = new ByteArrayOutputStream(); p.store(bytes, ""); var loaded = new Properties();
            loaded.load(new ByteArrayInputStream(bytes.toByteArray()));
            assertEquals(wanted, Ps1GeometryOptions.read(loaded));
            assertEquals(snap > 0 || affine > 0, wanted.enabled());
        }
    }

    @Test void nonfiniteAndMalformedValuesCannotReachVertexOrFragmentStage() {
        for (String bad : new String[]{"NaN", "Infinity", "-Infinity", "broken", ""}) {
            var p = new Properties(); p.setProperty("ps1VertexSnapPixels", bad); p.setProperty("ps1AffineAmount", bad);
            assertEquals(new Ps1GeometryOptions(0, 0), Ps1GeometryOptions.read(p));
        }
        assertEquals(new Ps1GeometryOptions(4, 1), new Ps1GeometryOptions(Float.MAX_VALUE, Float.MAX_VALUE));
        assertEquals(new Ps1GeometryOptions(0, 0), new Ps1GeometryOptions(-20, -10));
        assertEquals(new Ps1GeometryOptions(2, 0), new Ps1GeometryOptions(2, Float.NaN));
        assertEquals(new Ps1GeometryOptions(0, .75f), new Ps1GeometryOptions(Float.POSITIVE_INFINITY, .75f));
    }

    @Test void selectedPs1AloneNeverAdmitsHighResolutionOverlaysOrRecursion() {
        var enabled = new Ps1GeometryOptions(1, .5f);
        for (var profile : PresentationProfile.values()) for (boolean world : new boolean[]{false, true})
            for (boolean offscreen : new boolean[]{false, true})
                assertEquals(profile == PresentationProfile.PS1_HYBRID && world && !offscreen,
                        enabled.eligible(profile, world, offscreen));
        assertFalse(new Ps1GeometryOptions(0, 0).eligible(PresentationProfile.PS1_HYBRID, true, false));
    }

    @Test void menuCyclesIncludeOffAndKeepTheOtherPersistedControl() {
        var snap = new Ps1GeometryOptions(0, .13f); var values = new HashSet<Float>();
        for (int i = 0; i < 5; i++) { values.add(snap.snapPixels()); snap = new Ps1GeometryOptions(snap.nextSnap(), snap.affineAmount()); }
        assertEquals(Set.of(0f, .5f, 1f, 2f, 4f), values); assertEquals(new Ps1GeometryOptions(0, .13f), snap);
        var affine = new Ps1GeometryOptions(1.7f, 0); values.clear();
        for (int i = 0; i < 5; i++) { values.add(affine.affineAmount()); affine = new Ps1GeometryOptions(affine.snapPixels(), affine.nextAffine()); }
        assertEquals(Set.of(0f, .25f, .5f, .75f, 1f), values); assertEquals(new Ps1GeometryOptions(1.7f, 0), affine);
    }
}
