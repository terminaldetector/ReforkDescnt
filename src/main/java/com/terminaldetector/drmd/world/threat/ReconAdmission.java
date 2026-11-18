package com.terminaldetector.drmd.world.threat;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** One finite timed recon attempt, owned exclusively by the existing threat director. */
public final class ReconAdmission {
    static final int UNSEEN_TICKS=ThreatProgress.SEARCH_TICKS;
    static final int OBSERVED_TICKS=ThreatProgress.WARNING_TICKS+ThreatProgress.SEARCH_TICKS+ThreatProgress.HUNT_TICKS;
    static final int MAX_LIFETIME=UNSEEN_TICKS+OBSERVED_TICKS;
    public record Context(String world,long seed,String backend,int bottom,int top){
        public Context{
            Objects.requireNonNull(world);Objects.requireNonNull(backend);
            if(world.length()>160||!world.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                ||!Set.of("STOCK","NATIVE_CUBIC").contains(backend)||bottom>=top)
                throw new IllegalArgumentException("invalid recon context");
        }
    }
    public record Snapshot(UUID attempt,UUID owner,Context context,ThreatProgress.Point origin,
                           UUID scout,long started,long expires,boolean observed,long observedAt,
                           boolean closed,boolean assaultIssued,UUID assault){
        public Snapshot{
            Objects.requireNonNull(attempt);Objects.requireNonNull(owner);Objects.requireNonNull(context);
            Objects.requireNonNull(origin);Objects.requireNonNull(scout);
            if(scout.equals(owner)||attempt.equals(scout)||attempt.equals(owner)||started<0||started>Long.MAX_VALUE-MAX_LIFETIME||expires<started||expires-started>MAX_LIFETIME
                ||origin.y()<context.bottom()||origin.y()>=context.top()||assaultIssued!=(assault!=null)
                ||(!observed&&(observedAt!=-1||assaultIssued||expires!=ThreatProgress.deadline(started,UNSEEN_TICKS)))
                ||(observed&&(observedAt<started||observedAt>=expires||expires!=ThreatProgress.deadline(observedAt,OBSERVED_TICKS)))
                ||assault!=null&&(assault.equals(scout)||assault.equals(owner)||assault.equals(attempt)))
                throw new IllegalArgumentException("invalid recon lifetime/identity/allowance");
        }
    }
    private Snapshot state;
    ReconAdmission(UUID attempt,UUID owner,Context context,ThreatProgress.Point origin,UUID scout,long now){
        this(new Snapshot(attempt,owner,context,origin,scout,now,ThreatProgress.deadline(now,UNSEEN_TICKS),false,-1,false,false,null));
    }
    ReconAdmission(Snapshot snapshot){state=Objects.requireNonNull(snapshot);}
    public Snapshot snapshot(){return state;}
    boolean active(){return !state.closed();}
    boolean isScout(UUID id){return state.scout().equals(id);}
    boolean owns(UUID id){return isScout(id)||state.assaultIssued()&&state.assault().equals(id);}
    boolean mayObserve(long now){return active()&&!state.observed()&&now>=state.started()&&now<state.expires();}
    boolean observe(long now,ThreatProgress.Point actual){
        Objects.requireNonNull(actual);if(!mayObserve(now))return false;
        state=new Snapshot(state.attempt(),state.owner(),state.context(),actual,state.scout(),state.started(),
            ThreatProgress.deadline(now,OBSERVED_TICKS),true,now,false,false,null);return true;
    }
    boolean mayIssue(long now){return active()&&state.observed()&&!state.assaultIssued()
        &&now>=ThreatProgress.deadline(state.observedAt(),ThreatProgress.WARNING_TICKS)&&now<state.expires();}
    /** Lifetime allowance is independent of actor presence and ThreatProgress.spawned. */
    boolean issue(UUID actor,long now){
        Objects.requireNonNull(actor);if(!mayIssue(now))return false;
        state=new Snapshot(state.attempt(),state.owner(),state.context(),state.origin(),state.scout(),state.started(),state.expires(),
            true,state.observedAt(),false,true,actor);return true;
    }
    boolean close(){
        if(state.closed())return false;
        state=new Snapshot(state.attempt(),state.owner(),state.context(),state.origin(),state.scout(),state.started(),state.expires(),
            state.observed(),state.observedAt(),true,state.assaultIssued(),state.assault());return true;
    }
}
