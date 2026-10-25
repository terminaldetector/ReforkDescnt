package com.terminaldetector.drmd;

import com.terminaldetector.drmd.config.ProfileDefaults;
import com.terminaldetector.drmd.physics.PhysicsProfile;
import org.junit.jupiter.api.Test;
import java.util.Properties;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfileDefaultsTest {
    @Test void missingKeysUseNewProductDefaults() {
        assertTrue(ProfileDefaults.hybrid(new Properties()));assertEquals(PhysicsProfile.EXPANDED,PhysicsProfile.parse(null));
    }
    @Test void explicitOffAndStandardSurviveAStoredPropertiesRoundTrip() throws Exception {
        var saved=new Properties();saved.setProperty("hybridRender","false");saved.setProperty("physicsProfile","STANDARD");
        var bytes=new ByteArrayOutputStream();saved.store(bytes,"");var loaded=new Properties();loaded.load(new ByteArrayInputStream(bytes.toByteArray()));
        assertFalse(ProfileDefaults.hybrid(loaded));assertEquals(PhysicsProfile.STANDARD,PhysicsProfile.parse(loaded.getProperty("physicsProfile")));
    }
    @Test void malformedValuesDoNotSilentlyDisableNewProfiles() {
        var props=new Properties();props.setProperty("hybridRender","truue");
        assertTrue(ProfileDefaults.hybrid(props));assertEquals(PhysicsProfile.EXPANDED,PhysicsProfile.parse("unknown"));
    }
    @Test void explicitLegacySpellingsAreMigratedWithoutOverwritingTheirChoice() {
        for(String value:new String[]{"off","FALSE","0"," false "}) {
            var props=new Properties();props.setProperty("hybridRender",value);assertFalse(ProfileDefaults.hybrid(props));
            assertEquals(PhysicsProfile.STANDARD,PhysicsProfile.parse(value));
        }
    }
    @Test void RenderingAndPhysicsChoicesAreIndependent() {
        for(boolean hybrid:new boolean[]{false,true})for(var physics:PhysicsProfile.values()) {
            var props=new Properties();props.setProperty("hybridRender",Boolean.toString(hybrid));props.setProperty("physicsProfile",physics.name());
            assertEquals(hybrid,ProfileDefaults.hybrid(props));assertEquals(physics,PhysicsProfile.parse(props.getProperty("physicsProfile")));
        }
    }
}
