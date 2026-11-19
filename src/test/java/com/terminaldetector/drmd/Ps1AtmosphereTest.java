package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.presentation.*;
import com.terminaldetector.drmd.world.level.WorldLevels;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Ps1AtmosphereTest {
    @Test void theActualSurfaceSkyAndOrbitJoinsHaveFiniteContinuousDensityAndGroundFade() {
        assertEquals(1,Ps1Atmosphere.density(WorldLevels.SURFACE_TOP,1),1e-6);
        assertEquals(.45,Ps1Atmosphere.density(WorldLevels.SKY_TOP,1),1e-6);
        assertEquals(.08,Ps1Atmosphere.density(WorldLevels.ORBITAL_TOP,1),1e-6);
        assertEquals(0,Ps1Atmosphere.groundOpacity(WorldLevels.SURFACE_TOP));
        assertEquals(1,Ps1Atmosphere.groundOpacity(WorldLevels.SKY_TOP));
        for(int boundary:new int[]{WorldLevels.SURFACE_TOP,WorldLevels.SKY_TOP,WorldLevels.ORBITAL_TOP}) {
            assertEquals(Ps1Atmosphere.density(boundary-.001,1),Ps1Atmosphere.density(boundary+.001,1),1e-6);
            assertEquals(Ps1Atmosphere.groundOpacity(boundary-.001),Ps1Atmosphere.groundOpacity(boundary+.001),1e-6);
        }
    }
    @Test void climbingNeverIntroducesADensityOrOpacityBandJump() {
        float density=1,opacity=0;
        for(int y=WorldLevels.SURFACE_TOP-32;y<=WorldLevels.ORBITAL_TOP+100;y++) {
            float next=Ps1Atmosphere.density(y,1),fade=Ps1Atmosphere.groundOpacity(y);
            assertTrue(next<=density+1e-7&&next>=.07999f);
            assertTrue(Math.abs(next-density)<.01);
            assertTrue(fade>=opacity&&fade<=1&&fade-opacity<.01);
            density=next;opacity=fade;
        }
    }
    @Test void realSourceDistanceAndThinnerOrbitalAirControlTheColourFadeWithoutMaskingTheNearWorld() {
        assertEquals(0,Ps1Atmosphere.fogAmount(600,1,512,0));
        assertEquals(0,Ps1Atmosphere.fogAmount(600,1,1800,1800));
        double terrain=Ps1Atmosphere.fogAmount(600,1,5000,512),orbitalObject=Ps1Atmosphere.fogAmount(1800,1,5000,512);
        assertTrue(terrain>orbitalObject&&orbitalObject>0);
        assertTrue(Ps1Atmosphere.fogAmount(600,1,10000,512)>terrain);
        assertEquals(.85,Ps1Atmosphere.fogAmount(600,2,100000,0),1e-12);
        assertEquals(0,Ps1Atmosphere.fogAmount(600,0,100000,0));
    }
    @Test void nativeDebugOffscreenAndMalformedInputsCannotInheritThePreviousAtmosphereUniform() {
        var options=new Ps1FarFogOptions(1);
        for(var profile:PresentationProfile.values())for(boolean world:new boolean[]{false,true})for(boolean nested:new boolean[]{false,true}) {
            float effective=options.effective(profile,world,nested);
            assertEquals(profile==PresentationProfile.PS1_HYBRID&&world&&!nested?.08:0,
                Ps1Atmosphere.density(2200,effective),1e-6);
        }
        assertEquals(0,Ps1Atmosphere.density(Double.NaN,1));
        assertEquals(0,Ps1Atmosphere.density(100,Float.POSITIVE_INFINITY));
        assertEquals(0,Ps1Atmosphere.fogAmount(600,1,Double.POSITIVE_INFINITY,0));
        assertEquals(0,Ps1Atmosphere.groundOpacity(Double.NaN));
    }
}
