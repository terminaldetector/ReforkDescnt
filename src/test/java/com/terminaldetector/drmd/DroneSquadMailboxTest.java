package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.DroneSquadMailbox;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DroneSquadMailboxTest {
    private static UUID id(long value){return new UUID(0,value);}
    @Test void repeatedReadsNeverRefreshObservationExpiryOrCoordinates(){
        var box=new DroneSquadMailbox();assertTrue(box.offer(id(1),id(2),id(3),1,2,3,100));
        for(int tick=100;tick<160;tick++){
            var message=box.peek(id(1),tick).orElseThrow();
            assertEquals(100,message.observedAt());assertEquals(160,message.expiresAt());
            assertEquals(1,message.x());assertEquals(2,message.y());assertEquals(3,message.z());
        }
        assertTrue(box.peek(id(1),99).isEmpty());assertTrue(box.peek(id(1),160).isEmpty());
        box.cleanup(160);assertEquals(0,box.stats().size());
    }
    @Test void capacityFloodEvictsOldestInsteadOfCreatingAQueue(){
        var box=new DroneSquadMailbox();
        for(int i=0;i<512;i++){
            assertTrue(box.offer(id(1000+i),id(2),id(3),i,2,3,100));
            assertTrue(box.stats().size()<=256);
        }
        assertEquals(256,box.stats().size());assertEquals(256,box.stats().evictions());
        assertTrue(box.peek(id(1000),100).isEmpty());assertTrue(box.peek(id(1255),100).isEmpty());
        assertTrue(box.peek(id(1256),100).isPresent());assertTrue(box.peek(id(1511),100).isPresent());
    }
    @Test void expiredFloodCleanupIsLimitedTo32EntriesPerFiveTickPass(){
        var box=new DroneSquadMailbox();for(int i=0;i<256;i++)box.offer(id(1000+i),id(2),id(3),1,2,3,100);
        box.cleanup(200);assertEquals(224,box.stats().size());assertEquals(32,box.stats().cleanupExamined());
        box.cleanup(200);box.cleanup(204);assertEquals(224,box.stats().size());assertEquals(200,box.stats().cleanupTick());
        for(int tick=205;tick<=235;tick+=5){box.cleanup(tick);assertTrue(box.stats().cleanupExamined()<=32);}
        assertEquals(0,box.stats().size());
    }
    @Test void invalidStaleAndDuplicateObservationsCannotRewriteOrExtendARealMessage(){
        var box=new DroneSquadMailbox();assertTrue(box.offer(id(1),id(2),id(3),1,2,3,100));
        assertFalse(box.offer(id(1),id(2),id(3),9,8,7,99));
        assertFalse(box.offer(id(1),id(2),id(3),9,8,7,100));
        assertFalse(box.offer(id(4),id(2),id(3),Double.NaN,2,3,101));
        assertFalse(box.offer(id(4),id(2),id(3),1,Double.POSITIVE_INFINITY,3,101));
        assertFalse(box.offer(null,id(2),id(3),1,2,3,101));
        assertFalse(box.offer(id(4),null,id(3),1,2,3,101));
        assertFalse(box.offer(id(4),id(2),null,1,2,3,101));
        assertEquals(1,box.stats().size());var original=box.peek(id(1),159).orElseThrow();
        assertEquals(1,original.x());assertEquals(100,original.observedAt());assertEquals(160,original.expiresAt());
        assertTrue(box.offer(id(1),id(2),id(3),9,8,7,101));
        assertEquals(161,box.peek(id(1),160).orElseThrow().expiresAt());
    }
}
