package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.mega.ControlStateRules;
import com.terminaldetector.drmd.world.mega.ControlStateRules.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ControlStateRulesTest {
    private static final UUID A=new UUID(1,2),B=new UUID(3,4);
    private static final String WORLD="minecraft:overworld",OTHER="minecraft:the_end";
    private static Update core(UUID body,int epoch,boolean active,String dimension){return new Update(body,epoch,active,dimension,Kind.CORE,3,false);}
    private static Update sky(UUID body,int epoch,boolean active,String dimension){return new Update(body,epoch,active,dimension,Kind.SKY,7,true);}

    @Test void aFirstServerStateMustMatchTheActualClientDimension() {
        assertTrue(ControlStateRules.admit(null,core(A,1,true,WORLD),WORLD));
        assertTrue(ControlStateRules.admit(null,sky(A,1,true,WORLD),WORLD));
        assertFalse(ControlStateRules.admit(null,core(A,1,true,WORLD),OTHER));
        assertFalse(ControlStateRules.admit(null,core(A,1,true,WORLD),null));
        assertFalse(ControlStateRules.admit(null,null,WORLD));
    }
    @Test void duplicateAcquisitionAndOlderEpochCannotResetTheCommandSequences() {
        var current=core(A,20,true,WORLD);
        assertFalse(ControlStateRules.admit(current,current,WORLD));
        assertFalse(ControlStateRules.admit(current,core(A,19,true,WORLD),WORLD));
        assertFalse(ControlStateRules.admit(current,core(A,19,false,WORLD),WORLD));
    }
    @Test void aReleaseIsAnEpochTombstoneForLateActivation() {
        var active=core(A,20,true,WORLD);var release=active.released();
        assertTrue(ControlStateRules.admit(active,release,WORLD));
        assertFalse(ControlStateRules.admit(release,active,WORLD));
        assertFalse(ControlStateRules.admit(release,release,WORLD));
        assertEquals(0,ControlStateRules.sampleFireMask(release,7));
    }
    @Test void theSameEpochCannotReleaseOrReplaceAForeignBodyKindOrDimension() {
        var current=core(A,20,true,WORLD);
        assertFalse(ControlStateRules.admit(current,core(B,20,false,WORLD),WORLD));
        assertFalse(ControlStateRules.admit(current,sky(A,20,false,WORLD),WORLD));
        assertFalse(ControlStateRules.admit(current,core(A,20,false,OTHER),OTHER));
        assertTrue(ControlStateRules.admit(current,current.released(),WORLD));
    }
    @Test void aGenuinelyNewerLeaseCanSelectTheNextActualBodyOrWorld() {
        var old=core(A,20,false,WORLD);
        assertTrue(ControlStateRules.admit(old,core(B,21,true,WORLD),WORLD));
        assertTrue(ControlStateRules.admit(old,sky(B,21,true,OTHER),OTHER));
        assertFalse(ControlStateRules.admit(old,core(B,21,true,OTHER),WORLD));
    }
    @Test void genericInputCannotAdvertiseOrSendAuxiliaryAndTurretCapabilities() {
        var core=core(A,1,true,WORLD);var sky=sky(A,1,true,WORLD);
        assertEquals(3,ControlStateRules.sampleFireMask(core,7));
        assertEquals(0,ControlStateRules.sampleFireMask(core,4));
        assertEquals(1,ControlStateRules.sampleFireMask(core,5));
        assertEquals(2,ControlStateRules.sampleFireMask(core,6));
        assertEquals(7,ControlStateRules.sampleFireMask(sky,7));
        assertFalse(ControlStateRules.sendTurret(core));assertTrue(ControlStateRules.sendTurret(sky));
        assertFalse(ControlStateRules.sendTurret(sky.released()));assertFalse(ControlStateRules.sendTurret(null));
    }
    @Test void malformedCapabilitiesCannotEnterTheClientState() {
        assertThrows(IllegalArgumentException.class,()->new Update(A,1,true,WORLD,Kind.CORE,7,false));
        assertThrows(IllegalArgumentException.class,()->new Update(A,1,true,WORLD,Kind.CORE,3,true));
        assertThrows(IllegalArgumentException.class,()->new Update(A,1,true,WORLD,Kind.SKY,3,false));
        assertThrows(IllegalArgumentException.class,()->core(A,-1,true,WORLD));
        assertThrows(IllegalArgumentException.class,()->core(A,1,true," "));
    }
}
