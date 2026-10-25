package com.terminaldetector.drmd.world.build;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Bounded state transaction; storage writes must suppress publication until the result is known. */
public final class PlacementTransaction {
    public static final int MAX_CELLS=256;
    public record Change<P,S>(P position,S before,S after) {}
    public interface Storage<P,S> {
        S read(P position);
        boolean write(P position,S state);
    }
    public enum Status { COMMITTED,REJECTED,ROLLED_BACK,ROLLBACK_FAILED }
    public record Result(Status status,RuntimeException failure) {
        public boolean committed(){return status==Status.COMMITTED;}
    }
    private PlacementTransaction() {}

    public static <P,S> Result commit(List<Change<P,S>> changes,Storage<P,S> storage,BooleanSupplier stillEligible) {
        if(changes==null || changes.isEmpty() || changes.size()>MAX_CELLS)return new Result(Status.REJECTED,null);
        var positions=new HashSet<P>();
        try {
            for(var change:changes) {
                if(change==null || change.position()==null || change.before()==null || change.after()==null
                    || Objects.equals(change.before(),change.after()) || !positions.add(change.position())
                    || !Objects.equals(storage.read(change.position()),change.before()))return new Result(Status.REJECTED,null);
            }
            if(!stillEligible.getAsBoolean())return new Result(Status.REJECTED,null);
        }catch(RuntimeException failure){return new Result(Status.REJECTED,failure);}
        int attempted=0;
        try {
            for(var change:changes) {
                attempted++;
                if(!storage.write(change.position(),change.after()) || !Objects.equals(storage.read(change.position()),change.after()))
                    return rollback(changes,storage,attempted,null);
            }
            for(var change:changes)if(!Objects.equals(storage.read(change.position()),change.after()))
                return rollback(changes,storage,attempted,null);
            // Reentrant block callbacks may have changed inventory/authority while writing.
            if(!stillEligible.getAsBoolean())return rollback(changes,storage,attempted,null);
            return new Result(Status.COMMITTED,null);
        }catch(RuntimeException failure){return rollback(changes,storage,attempted,failure);}
    }

    private static <P,S> Result rollback(List<Change<P,S>> changes,Storage<P,S> storage,int attempted,RuntimeException cause) {
        boolean restored=true;
        for(int i=attempted-1;i>=0;i--) {
            var change=changes.get(i);
            try {
                if(!Objects.equals(storage.read(change.position()),change.before()))storage.write(change.position(),change.before());
                if(!Objects.equals(storage.read(change.position()),change.before()))restored=false;
            }catch(RuntimeException failure){restored=false;if(cause==null)cause=failure;}
        }
        return new Result(restored?Status.ROLLED_BACK:Status.ROLLBACK_FAILED,cause);
    }
}
