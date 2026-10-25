package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.gravity.GravityNoticeGate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GravityNoticeGateTest {
    private final GravityNoticeGate gate=new GravityNoticeGate();
    private final UUID player=UUID.randomUUID(),field=UUID.randomUUID();
    @Test void heldFieldNeverBecomesAPeriodicReminder() {
        assertTrue(gate.observe(player,field,0));
        for(int tick=1;tick<2000;tick++)assertFalse(gate.observe(player,field,tick));
    }
    @Test void fieldEdgeAndSourceJitterCannotSpamThePlayer() {
        int notifications=0;
        for(int tick=0;tick<100;tick++)
            if(gate.observe(player,tick%3==0?null:tick%3==1?field:new UUID(0,1),tick))notifications++;
        assertEquals(1,notifications);
    }
    @Test void returningAfterCooldownAndChangingSourcesAreRealTransitions() {
        assertTrue(gate.observe(player,field,0));
        assertFalse(gate.observe(player,null,99));
        assertTrue(gate.observe(player,field,100));
        assertFalse(gate.observe(player,UUID.randomUUID(),101));
        assertTrue(gate.observe(player,UUID.randomUUID(),200));
    }
    @Test void playersAndSessionsHaveIndependentNoticeHistory() {
        assertTrue(gate.observe(player,field,0));
        assertTrue(gate.observe(UUID.randomUUID(),field,1));
        gate.forget(player);
        assertTrue(gate.observe(player,field,2));
        gate.clear();
        assertTrue(gate.observe(player,field,3));
    }
}
