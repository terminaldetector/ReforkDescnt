package com.terminaldetector.drmd;
import com.terminaldetector.drmd.world.contraption.boarding.BoardingEpochBarrier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BoardingEpochBarrierTest {
    @Test void orderedLiveUpdatesContinueButOlderClocksCannotReplaceThem(){
        var gate=new BoardingEpochBarrier();assertTrue(gate.accepts(7));gate.observe(7);
        assertTrue(gate.accepts(7));assertFalse(gate.accepts(6));assertFalse(gate.accepts(-1));assertTrue(gate.accepts(8));
    }
    @Test void anExternalTransferRejectsLateStateEvenWhenGeometryWasStillPending(){
        var gate=new BoardingEpochBarrier();gate.observe(12);gate.retireSeen();
        assertFalse(gate.accepts(11));assertFalse(gate.accepts(12));assertTrue(gate.accepts(13));
        gate.observe(13);gate.retireThrough(13);
        assertFalse(gate.accepts(13),"late active state cannot reopen a released lease");assertTrue(gate.accepts(14));
    }
    @Test void aNewConnectionMayStartItsServerClockAgain(){
        var gate=new BoardingEpochBarrier();gate.observe(100);gate.retireSeen();gate.reset();
        assertTrue(gate.accepts(0));gate.observe(0);assertTrue(gate.accepts(0));assertTrue(gate.accepts(1));
    }
}
