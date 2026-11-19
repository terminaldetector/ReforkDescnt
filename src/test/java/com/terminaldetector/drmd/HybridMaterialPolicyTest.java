package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.geometry.HybridMaterialPolicy;
import com.terminaldetector.drmd.world.micro.MicroGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HybridMaterialPolicyTest {
    @Test void intactNaturalAndEngineeredMasksKeepOppositeSurfaceOwnership() {
        var soil=HybridMaterialPolicy.of(true,MicroGrid.FULL,0,false,false);
        var hull=HybridMaterialPolicy.of(false,MicroGrid.FULL,0,false,false);
        assertEquals(HybridMaterialPolicy.Mask.SMOOTH_NATURAL,soil.mask());
        assertTrue(soil.smooth());assertFalse(soil.anchor());
        assertEquals(HybridMaterialPolicy.Mask.HARD_ENGINEERED,hull.mask());
        assertFalse(hull.smooth());assertTrue(hull.anchor());
        assertFalse(soil.quarterDamage());assertFalse(hull.thermal());
    }
    @Test void fractureAndMeltMasksDoNotSilentlyConvertAnEngineeredSurfaceIntoOrganicTerrain() {
        for(boolean organic:new boolean[]{false,true})for(long cells:new long[]{MicroGrid.FULL,0x15L})
            for(int heat:new int[]{0,2})for(boolean bore:new boolean[]{false,true}) {
                var policy=HybridMaterialPolicy.of(organic,cells,heat,bore,false);
                boolean transition=cells!=MicroGrid.FULL||heat>0||bore;
                assertEquals(transition,policy.mask()==HybridMaterialPolicy.Mask.TRANSITION);
                assertEquals(organic,policy.smooth());assertEquals(!organic,policy.anchor());
                assertEquals(cells!=MicroGrid.FULL,policy.quarterDamage());
                assertEquals(heat>0||bore,policy.thermal());
            }
    }
    @Test void anEmptyCarvedNeighbourCannotPinTerrainButAnExplicitRigidInterfaceStillCan() {
        var removed=HybridMaterialPolicy.of(false,0,0,false,false);
        var interfaceCell=HybridMaterialPolicy.of(false,0,0,false,true);
        assertFalse(removed.anchor());assertTrue(interfaceCell.anchor());
        assertTrue(removed.quarterDamage());assertFalse(removed.smooth());
        assertFalse(HybridMaterialPolicy.of(true,0,3,true,true).anchor());
    }
}
