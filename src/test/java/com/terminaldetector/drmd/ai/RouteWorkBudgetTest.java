package com.terminaldetector.drmd.ai;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RouteWorkBudgetTest {
    @Test void rotatesAcrossSearchesEvenWhenActorTickOrderNeverChanges() {
        var b=new RouteWorkBudget(16,48,8);var ids=new ArrayList<UUID>();var served=new HashSet<UUID>();for(int i=0;i<16;i++){var id=new UUID(0,i);ids.add(id);assertTrue(b.begin(id,0));}
        for(long t=0;t<4;t++){int used=0;for(var id:ids){assertTrue(b.begin(id,t));int grant=b.take(id,t);used+=grant;if(grant>0)served.add(id);}assertTrue(used<=48);}
        assertEquals(Set.copyOf(ids),served);
    }
    @Test void boundsAdmissionAndReleasesAbandonedSearches() {
        var b=new RouteWorkBudget(2,8,4);var a=new UUID(0,1);var c=new UUID(0,2);var d=new UUID(0,3);
        assertTrue(b.begin(a,0));assertTrue(b.begin(c,0));assertFalse(b.begin(d,0));b.release(a);assertTrue(b.begin(d,0));assertTrue(b.begin(a,21));assertEquals(1,b.activeSearches());
    }
    @Test void repeatedRequestsCannotExceedTheWorldBudget() {
        var b=new RouteWorkBudget(2,8,4);var id=new UUID(0,1);assertTrue(b.begin(id,10));int sum=0;for(int i=0;i<100;i++)sum+=b.take(id,10);assertEquals(8,sum);assertEquals(4,b.take(id,11));assertEquals(0,b.take(new UUID(0,2),11));
    }
}
