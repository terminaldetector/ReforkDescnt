package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.build.PlacementTransaction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlacementTransactionTest {
    private static class Store implements PlacementTransaction.Storage<Integer,String> {
        final Map<Integer,String> cells=new HashMap<>(Map.of(0,"air",1,"air"));
        int writes;boolean denyHead,throwAfterHead,denyRestore;
        public String read(Integer key){return cells.getOrDefault(key,"air");}
        public boolean write(Integer key,String value) {
            writes++;
            if(key==1 && value.equals("head") && denyHead)return false;
            if(key==0 && value.equals("air") && denyRestore)return false;
            cells.put(key,value);
            if(key==1 && value.equals("head") && throwAfterHead)throw new IllegalStateException("rejected after write");
            return true;
        }
    }
    private static List<PlacementTransaction.Change<Integer,String>> pair() {
        return List.of(new PlacementTransaction.Change<>(0,"air","foot"),new PlacementTransaction.Change<>(1,"air","head"));
    }
    @Test void aWholePairBecomesVisibleToTheCallerBeforeAnyReceiptIsCharged() {
        var store=new Store();int[] charged={0};
        var result=PlacementTransaction.commit(pair(),store,()->true);
        if(result.committed())charged[0]++;
        assertEquals(Map.of(0,"foot",1,"head"),store.cells);assertEquals(1,charged[0]);
    }
    @Test void secondCellRejectionRestoresFirstAndDoesNotCharge() {
        var store=new Store();store.denyHead=true;
        var result=PlacementTransaction.commit(pair(),store,()->true);
        assertEquals(PlacementTransaction.Status.ROLLED_BACK,result.status());
        assertEquals(Map.of(0,"air",1,"air"),store.cells);assertFalse(result.committed());
    }
    @Test void exceptionAfterAStorageMutationRestoresEvenTheThrowingCell() {
        var store=new Store();store.throwAfterHead=true;
        var result=PlacementTransaction.commit(pair(),store,()->true);
        assertEquals(PlacementTransaction.Status.ROLLED_BACK,result.status());assertNotNull(result.failure());
        assertEquals(Map.of(0,"air",1,"air"),store.cells);
    }
    @Test void aStaleSecondCellRejectsBeforeTheFirstWrite() {
        var store=new Store();store.cells.put(1,"bedrock");
        var result=PlacementTransaction.commit(pair(),store,()->true);
        assertEquals(PlacementTransaction.Status.REJECTED,result.status());assertEquals(0,store.writes);
        assertEquals("bedrock",store.read(1));
    }
    @Test void duplicatePositionsCannotBeCountedAsTwoParts() {
        var store=new Store();var changes=List.of(new PlacementTransaction.Change<>(0,"air","foot"),new PlacementTransaction.Change<>(0,"air","head"));
        assertFalse(PlacementTransaction.commit(changes,store,()->true).committed());assertEquals(0,store.writes);
    }
    @Test void lostMaterialOrAuthorityAfterWritesCausesRollback() {
        var store=new Store();int[] observations={0};
        var result=PlacementTransaction.commit(pair(),store,()->++observations[0]==1);
        assertEquals(PlacementTransaction.Status.ROLLED_BACK,result.status());assertEquals(Map.of(0,"air",1,"air"),store.cells);
    }
    @Test void aFailedRollbackIsNeverReportedAsACommitOrCompleteRecovery() {
        var store=new Store();store.denyHead=true;store.denyRestore=true;
        var result=PlacementTransaction.commit(pair(),store,()->true);
        assertEquals(PlacementTransaction.Status.ROLLBACK_FAILED,result.status());assertFalse(result.committed());
    }
    @Test void writingTheHeadCannotSilentlyInvalidateThePreviouslyWrittenFoot() {
        var store=new Store() {
            @Override public boolean write(Integer key,String value) {
                boolean wrote=super.write(key,value);
                if(key==1 && value.equals("head"))cells.put(0,"air");
                return wrote;
            }
        };
        var result=PlacementTransaction.commit(pair(),store,()->true);
        assertEquals(PlacementTransaction.Status.ROLLED_BACK,result.status());
        assertEquals(Map.of(0,"air",1,"air"),store.cells);
    }
    @Test void ineligibleOrOversizedChangesCannotWriteAnything() {
        var store=new Store();assertEquals(PlacementTransaction.Status.REJECTED,PlacementTransaction.commit(pair(),store,()->false).status());
        var huge=new ArrayList<PlacementTransaction.Change<Integer,String>>();
        for(int i=0;i<=PlacementTransaction.MAX_CELLS;i++)huge.add(new PlacementTransaction.Change<>(i,"air","stone"));
        assertFalse(PlacementTransaction.commit(huge,store,()->true).committed());assertEquals(0,store.writes);
    }
}
