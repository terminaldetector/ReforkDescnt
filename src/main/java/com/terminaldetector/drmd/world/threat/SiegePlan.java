package com.terminaldetector.drmd.world.threat;

import java.util.*;
import static com.terminaldetector.drmd.world.threat.ThreatProgress.*;

/** Non-autostart intent/order protocol. An explicit live director deployment may
 * consume its bounded orders; saved intent alone cannot authorize a raid.
 * Five shots per slot is an encounter order, not a global actor/stat change. */
public final class SiegePlan {
    public static final int SLOTS=2,CANDIDATE_SHOTS_PER_SLOT=5,MAX_ACKS=10;
    public enum Phase { PLANNED,WARNING,SEARCHING,SIEGE,RETREATING,COOLDOWN,CLOSED }
    public record Observation(UUID site,UUID observer,UUID participant,String world,long at,
                              Point point,UUID body,Point local) {
        public Observation {
            Objects.requireNonNull(site);Objects.requireNonNull(observer);Objects.requireNonNull(participant);Objects.requireNonNull(point);validWorld(world);
            if(at<0||(body==null)!=(local==null))throw new IllegalArgumentException("invalid site observation");
        }
        boolean sameSite(Observation other){return site.equals(other.site)&&participant.equals(other.participant)&&world.equals(other.world)
            &&Objects.equals(body,other.body)&&Objects.equals(local,other.local);}
    }
    public record Bindings(UUID first,UUID second) {
        public Bindings {if(first!=null&&first.equals(second))throw new IllegalArgumentException("duplicate future actor");}
        public UUID at(int slot){checkSlot(slot);return slot==0?first:second;}
        boolean contains(UUID actor){return actor!=null&&(actor.equals(first)||actor.equals(second));}
        boolean complete(){return first!=null&&second!=null;}
    }
    public record Ack(int slot,UUID actor,UUID shot,long at) {
        public Ack {checkSlot(slot);Objects.requireNonNull(actor);Objects.requireNonNull(shot);if(at<0)throw new IllegalArgumentException("negative emission time");}
    }
    public record Readiness(boolean loaded,boolean ownLos,boolean energy,boolean cooldown,boolean muzzleClear) {
        boolean ready(){return loaded&&ownLos&&energy&&cooldown&&muzzleClear;}
    }
    public record Snapshot(UUID id,UUID participant,String world,Phase phase,long proposedAt,long clock,long online,
                           long since,long until,long warningEnds,long encounterEnds,long cooldownEnds,
                           Observation origin,Observation known,Bindings actors,List<Ack> acks) {
        public Snapshot {
            Objects.requireNonNull(id);Objects.requireNonNull(participant);Objects.requireNonNull(phase);Objects.requireNonNull(origin);Objects.requireNonNull(known);Objects.requireNonNull(actors);
            validWorld(world);acks=List.copyOf(acks);
            if(proposedAt<0||clock<proposedAt||online<0||online>GRACE_TICKS||since<proposedAt||since>clock
                ||until<since||until-since>COOLDOWN_TICKS||warningEnds<0||encounterEnds<0||cooldownEnds<0
                ||origin.at()>proposedAt||known.at()<origin.at()||known.at()>clock
                ||!origin.sameSite(known)||!participant.equals(origin.participant())||!world.equals(origin.world())||acks.size()>MAX_ACKS)
                throw new IllegalArgumentException("invalid plan snapshot");
            if(warningEnds==0){if(encounterEnds!=0||cooldownEnds!=0||!(phase==Phase.PLANNED||phase==Phase.CLOSED))throw new IllegalArgumentException("missing warning");}
            else if(warningEnds<proposedAt||encounterEnds!=deadline(warningEnds,SEARCH_TICKS+HUNT_TICKS))throw new IllegalArgumentException("invalid fixed encounter");
            if(phase==Phase.PLANNED&&warningEnds!=0)throw new IllegalArgumentException("planned intent cannot carry active deadlines");
            if(phase==Phase.CLOSED&&warningEnds>0&&(cooldownEnds<=0||clock<cooldownEnds))throw new IllegalArgumentException("closed before recovery");
            if(phase==Phase.WARNING&&(until!=warningEnds||since>Long.MAX_VALUE-WARNING_TICKS||warningEnds-since!=WARNING_TICKS))throw new IllegalArgumentException("shortened warning");
            if((phase==Phase.SEARCHING||phase==Phase.SIEGE)&&(since<warningEnds||until>encounterEnds))throw new IllegalArgumentException("invalid search/fire deadline");
            if(phase==Phase.SIEGE&&(!actors.contains(known.observer())||until!=encounterEnds))throw new IllegalArgumentException("siege without own observation");
            if((phase==Phase.RETREATING||phase==Phase.COOLDOWN)&&cooldownEnds<=0)throw new IllegalArgumentException("missing recovery deadline");
            if(phase==Phase.RETREATING&&(cooldownEnds!=deadline(since,COOLDOWN_TICKS)||until!=Math.min(cooldownEnds,deadline(since,LOST_SIGHT_TICKS))))throw new IllegalArgumentException("invalid retreat deadline");
            if(phase==Phase.COOLDOWN&&until!=cooldownEnds)throw new IllegalArgumentException("invalid cooldown");
            var ids=new HashSet<UUID>();int[] used=new int[SLOTS];long previous=-1;
            for(var ack:acks){
                if(!ack.actor().equals(actors.at(ack.slot()))||!ids.add(ack.shot())||++used[ack.slot()]>CANDIDATE_SHOTS_PER_SLOT
                    ||warningEnds==0||ack.at()<warningEnds||ack.at()>=encounterEnds||ack.at()>clock||ack.at()<previous)
                    throw new IllegalArgumentException("invalid finite emission history");
                previous=ack.at();
            }
        }
    }
    /** Instance-bound current-tick capability; it is never serialized or constructed by a caller. */
    public static final class FirePermit {
        private final SiegePlan issuer;private final int slot,sequence;private final UUID actor;private final long at;private final Observation sight;
        private FirePermit(SiegePlan issuer,int slot,int sequence,UUID actor,long at,Observation sight){this.issuer=issuer;this.slot=slot;this.sequence=sequence;this.actor=actor;this.at=at;this.sight=sight;}
        public int slot(){return slot;}public int sequence(){return sequence;}public UUID actor(){return actor;}public long at(){return at;}public Point point(){return sight.point();}
    }
    private final UUID id,participant;private final String world;private final long proposedAt;private final Observation origin;
    private Phase phase;private long clock,online,since,until,warningEnds,encounterEnds,cooldownEnds;
    private Observation known;private Bindings actors;private final List<Ack> acks=new ArrayList<>();
    private final FirePermit[] pending=new FirePermit[SLOTS];
    public SiegePlan(UUID id,UUID participant,String world,long now,Observation observed){
        this(new Snapshot(id,participant,world,Phase.PLANNED,now,now,0,now,now,0,0,0,observed,observed,new Bindings(null,null),List.of()));
    }
    public SiegePlan(Snapshot s){
        id=s.id();participant=s.participant();world=s.world();proposedAt=s.proposedAt();origin=s.origin();phase=s.phase();clock=s.clock();online=s.online();since=s.since();until=s.until();
        warningEnds=s.warningEnds();encounterEnds=s.encounterEnds();cooldownEnds=s.cooldownEnds();known=s.known();actors=s.actors();acks.addAll(s.acks());
    }
    public Snapshot snapshot(){return new Snapshot(id,participant,world,phase,proposedAt,clock,online,since,until,warningEnds,encounterEnds,cooldownEnds,origin,known,actors,acks);}
    public int remaining(int slot){checkSlot(slot);return CANDIDATE_SHOTS_PER_SLOT-(int)acks.stream().filter(a->a.slot()==slot).count();}
    public boolean bindFutureIdentity(int slot,UUID actor){
        checkSlot(slot);Objects.requireNonNull(actor);
        if(rank(phase)>2||actors.at(slot)!=null||actors.contains(actor))return false;
        actors=slot==0?new Bindings(actor,actors.second()):new Bindings(actors.first(),actor);return true;
    }
    private void updateClock(long now){if(now<clock)throw new IllegalArgumentException("plan clock moved backwards");if(now!=clock)Arrays.fill(pending,null);clock=now;}
    private void close(long now){phase=Phase.CLOSED;since=until=now;Arrays.fill(pending,null);}
    private void retire(long now){
        Arrays.fill(pending,null);if(phase==Phase.PLANNED){close(now);return;}
        phase=Phase.RETREATING;since=now;cooldownEnds=deadline(now,COOLDOWN_TICKS);until=Math.min(cooldownEnds,deadline(now,LOST_SIGHT_TICKS));
    }
    private boolean matching(Observation sight,long now){return sight!=null&&origin.sameSite(sight)&&sight.at()<=now&&now-sight.at()<LOST_SIGHT_TICKS;}
    private boolean observe(Observation sight,long now){
        if(!matching(sight,now)||!actors.contains(sight.observer())||sight.at()<known.at()
            ||sight.at()==known.at()&&!sight.point().equals(known.point()))return false;
        if(sight.at()>known.at())known=sight;
        return true;
    }
    public void advance(long now,int onlineTicks,boolean eligible,boolean insideArea,Observation sight,boolean pairDefeated){
        updateClock(now);Arrays.fill(pending,null);
        if(phase==Phase.CLOSED)return;
        if(phase==Phase.COOLDOWN){if(now>=until)close(now);return;}
        if(phase==Phase.RETREATING){if(now>=cooldownEnds)close(now);else if(now>=until){since=until;until=cooldownEnds;phase=Phase.COOLDOWN;}return;}
        if(!eligible||!insideArea){retire(now);return;}
        online=Math.min(GRACE_TICKS,online+Math.max(0,Math.min(100,onlineTicks)));
        if(phase==Phase.PLANNED){
            if(now-origin.at()>=ALARM_TTL){close(now);return;}
            if(online>=GRACE_TICKS){
                if(now>Long.MAX_VALUE-WARNING_TICKS-SEARCH_TICKS-HUNT_TICKS-COOLDOWN_TICKS){close(now);return;}
                phase=Phase.WARNING;since=now;warningEnds=until=deadline(now,WARNING_TICKS);encounterEnds=deadline(warningEnds,SEARCH_TICKS+HUNT_TICKS);
            }return;
        }
        if(now>=encounterEnds||acks.size()==MAX_ACKS||pairDefeated&&actors.complete()){retire(now);return;}
        if(phase==Phase.WARNING){if(now<warningEnds)return;phase=Phase.SEARCHING;since=warningEnds;until=Math.min(encounterEnds,deadline(warningEnds,SEARCH_TICKS));}
        if(observe(sight,now)){if(phase==Phase.SEARCHING){phase=Phase.SIEGE;since=now;until=encounterEnds;}}
        else if(phase==Phase.SIEGE&&now-known.at()>=LOST_SIGHT_TICKS){phase=Phase.SEARCHING;since=now;until=Math.min(encounterEnds,deadline(now,SEARCH_TICKS));}
        if(phase==Phase.SEARCHING&&now>=until)retire(now);
    }
    public Optional<FirePermit> requestFire(int slot,UUID actor,long now,Observation sight,Readiness readiness){
        checkSlot(slot);if(now<clock)return Optional.empty();updateClock(now);
        if(phase!=Phase.SIEGE||now>=encounterEnds||remaining(slot)==0||actor==null||!actor.equals(actors.at(slot))
            ||readiness==null||!readiness.ready()||!matching(sight,now)||sight.at()!=now||!actor.equals(sight.observer())){
            pending[slot]=null;return Optional.empty();
        }
        var previous=pending[slot];
        if(previous!=null&&previous.at==now&&previous.actor.equals(actor)&&previous.sight.equals(sight))return Optional.of(previous);
        var permit=new FirePermit(this,slot,CANDIDATE_SHOTS_PER_SLOT-remaining(slot)+1,actor,now,sight);pending[slot]=permit;return Optional.of(permit);
    }
    public boolean acknowledge(FirePermit permit,UUID projectile,long now){
        if(permit==null||permit.issuer!=this)return false;
        if(now>clock)updateClock(now);
        if(now!=permit.at&&pending[permit.slot]==permit)pending[permit.slot]=null;
        if(permit==null||permit.issuer!=this||pending[permit.slot]!=permit||projectile==null||now!=clock||now!=permit.at
            ||phase!=Phase.SIEGE||now>=encounterEnds||!permit.actor.equals(actors.at(permit.slot))||remaining(permit.slot)==0
            ||permit.sequence!=CANDIDATE_SHOTS_PER_SLOT-remaining(permit.slot)+1||acks.stream().anyMatch(a->a.shot().equals(projectile)))return false;
        acks.add(new Ack(permit.slot,permit.actor,projectile,now));pending[permit.slot]=null;
        if(acks.size()==MAX_ACKS)retire(now);return true;
    }
    public static boolean canFollow(Snapshot old,Snapshot next){
        if(!old.id().equals(next.id())||!old.participant().equals(next.participant())||!old.world().equals(next.world())
            ||old.proposedAt()!=next.proposedAt()||!old.origin().equals(next.origin())||next.clock()<old.clock()||next.online()<old.online()
            ||rank(next.phase())<rank(old.phase())||old.warningEnds()>0&&old.warningEnds()!=next.warningEnds()
            ||old.encounterEnds()>0&&old.encounterEnds()!=next.encounterEnds()||old.cooldownEnds()>0&&old.cooldownEnds()!=next.cooldownEnds()
            ||next.known().at()<old.known().at()||next.known().at()==old.known().at()&&!next.known().point().equals(old.known().point())
            ||next.acks().size()<old.acks().size()||!next.acks().subList(0,old.acks().size()).equals(old.acks()))return false;
        for(int slot=0;slot<SLOTS;slot++)if(old.actors().at(slot)!=null&&!old.actors().at(slot).equals(next.actors().at(slot)))return false;
        return true;
    }
    private static int rank(Phase p){return switch(p){case PLANNED->0;case WARNING->1;case SEARCHING,SIEGE->2;case RETREATING->3;case COOLDOWN->4;case CLOSED->5;};}
    private static void checkSlot(int slot){if(slot<0||slot>=SLOTS)throw new IllegalArgumentException("invalid future slot");}
    private static void validWorld(String world){if(world==null||world.length()>256||!world.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))throw new IllegalArgumentException("invalid world identity");}
}
