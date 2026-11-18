package com.terminaldetector.drmd.world.threat;

import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import java.util.*;

/** One world's bounded alarm ledger; absence of a loaded actor is not evidence of its death. */
public final class SurvivalThreatState extends PersistentState {
    public static final String ID="drmd_survival_threat",FACTION="drmd:hostile_machines";
    public static final int MAX_PLAYERS=1024,MAX_ACTORS=8,MAX_VICTIMS=128;
    public record Actor(UUID owner,long expires){public Actor{Objects.requireNonNull(owner);if(expires<0)throw new IllegalArgumentException("invalid actor expiry");}}
    private final Map<UUID,ThreatProgress> players=new HashMap<>();
    private final Map<UUID,Actor> actors=new HashMap<>();
    private final Map<UUID,Long> victims=new HashMap<>();
    private SiegePlans siegePlans=new SiegePlans();
    private ReconAdmissions recon=new ReconAdmissions();
    private NbtCompound opaqueRoot;
    /** Unknown whole-state versions retain all original fields and cannot mint authority. */
    public boolean available(){return opaqueRoot==null;}
    public boolean reconAvailable(){return available()&&recon.available();}
    public Optional<ReconAdmission.Snapshot> reconProgress(UUID owner){return available()?recon.snapshot(owner):Optional.empty();}
    /** Non-executable intent only: these identities never participate in the live recon roster. */
    public Optional<SiegePlan.Snapshot> siegePlan(UUID id){return siegePlans.get(id);}
    public int siegePlanCount(){return siegePlans.size();}
    public boolean siegePlansReadOnly(){return !available()||siegePlans.readOnly();}
    public boolean putSiegePlan(SiegePlan.Snapshot snapshot){if(!available()||!siegePlans.put(snapshot,actors.keySet()))return false;markDirty();return true;}
    public boolean removeSiegePlan(UUID id){if(!available()||!siegePlans.remove(id))return false;markDirty();return true;}
    public static SurvivalThreatState get(ServerWorld world){return world.getPersistentStateManager().getOrCreate(new Type<>(SurvivalThreatState::new,SurvivalThreatState::fromNbt,null),ID);}
    ThreatProgress player(UUID id){if(!available())return null;var old=players.get(id);if(old!=null)return old;if(players.size()>=MAX_PLAYERS)return null;
        var created=new ThreatProgress();players.put(id,created);markDirty();return created;}
    public Optional<ThreatProgress.Snapshot> progress(UUID id){var p=players.get(id);return p==null?Optional.empty():Optional.of(p.snapshot());}
    Collection<Map.Entry<UUID,Actor>> actors(){return List.copyOf(actors.entrySet());}
    public int rosterSize(UUID owner){int count=0;for(var actor:actors.values())if(actor.owner.equals(owner))count++;return count;}
    public int actorCount(){return actors.size();}
    boolean hasActor(UUID id){return actors.containsKey(id);}
    Actor actor(UUID id){return actors.get(id);}
    boolean matches(UUID id,UUID owner,long expires){return Objects.equals(actors.get(id),new Actor(owner,expires));}
    public boolean alarm(UUID owner,UUID victim,ThreatProgress.Point at,long now){
        if(!reconAvailable())return false;
        if(now<0)throw new IllegalArgumentException("negative world clock");
        boolean purged=victims.entrySet().removeIf(e->now>=e.getValue());if(purged)markDirty();
        if(victims.containsKey(victim)||victims.size()>=MAX_VICTIMS||rosterSize(owner)>0)return false;
        var p=player(owner);if(p==null||!p.alarm(now,at))return false;
        victims.put(victim,ThreatProgress.deadline(now,ThreatProgress.ALARM_TTL));markDirty();return true;
    }
    boolean canDeploy(UUID owner){if(!reconAvailable())return false;var p=players.get(owner);var timed=recon.get(owner);
        return actors.size()<MAX_ACTORS&&rosterSize(owner)<ThreatProgress.PATROL_SIZE
            &&p!=null&&(p.phase()==ThreatProgress.Phase.SEARCHING||p.phase()==ThreatProgress.Phase.HUNTING)&&p.spawned()<ThreatProgress.PATROL_SIZE
            &&(timed==null||!timed.active()||timed.snapshot().observed()&&!timed.snapshot().assaultIssued());}
    boolean addActor(UUID owner,UUID id,long expires){
        var timed=recon.get(owner);if(!available()||actors.containsKey(id)||recon.reserved(id)||!canDeploy(owner)||timed!=null&&timed.active())return false;
        actors.put(id,new Actor(owner,expires));players.get(owner).spawnedActor();markDirty();return true;
    }
    boolean canScout(UUID owner){
        if(!reconAvailable()||actors.size()>=MAX_ACTORS||rosterSize(owner)!=0)return false;
        var p=players.get(owner);var old=recon.get(owner);
        return p!=null&&p.phase()==ThreatProgress.Phase.QUIET&&p.snapshot().played()==ThreatProgress.GRACE_TICKS
            &&!p.snapshot().pending()&&(old==null||!old.active());
    }
    boolean reserveScout(UUID owner,UUID scout,ReconAdmission.Context context,ThreatProgress.Point at,long now){
        if(scout==null||context==null||at==null||at.y()<context.bottom()||at.y()>=context.top()||!canScout(owner)||players.containsKey(scout)||actors.containsKey(scout)||recon.reserved(scout)||now<0||now>Long.MAX_VALUE-ReconAdmission.MAX_LIFETIME)return false;
        UUID attempt=UUID.randomUUID();if(recon.reserved(attempt)||players.containsKey(attempt)||actors.containsKey(attempt))return false;
        var record=new ReconAdmission(attempt,owner,context,at,scout,now);
        if(!recon.put(record))return false;
        actors.put(scout,new Actor(owner,record.snapshot().expires()));markDirty();return true;
    }
    void rejectUnspawnedScout(UUID owner,UUID scout){
        if(!reconAvailable()||!recon.rollbackUnspawned(owner,scout))return;
        actors.remove(scout);markDirty();
    }
    boolean timedActor(UUID actor){var a=actors.get(actor);var r=a==null?null:recon.get(a.owner());return r!=null&&r.owns(actor);}
    boolean scoutActor(UUID actor){var a=actors.get(actor);var r=a==null?null:recon.get(a.owner());return r!=null&&r.isScout(actor);}
    boolean timed(UUID owner){var r=recon.get(owner);return r!=null&&r.active();}
    long timedExpiry(UUID owner){var r=recon.get(owner);if(r==null)throw new IllegalStateException("no timed attempt");return r.snapshot().expires();}
    boolean observeScout(UUID owner,UUID scout,ReconAdmission.Context context,ThreatProgress.Point actual,long now){
        if(!reconAvailable())return false;var r=recon.get(owner);var p=players.get(owner);var a=actors.get(scout);
        if(actual==null||r==null||p==null||a==null||!a.owner().equals(owner)||!r.isScout(scout)||!r.snapshot().context().equals(context)||!r.mayObserve(now)
            ||p.phase()!=ThreatProgress.Phase.QUIET||p.snapshot().pending()||p.snapshot().played()!=ThreatProgress.GRACE_TICKS
            ||actual.y()<context.bottom()||actual.y()>=context.top())return false;
        if(!p.alarm(now,actual)||!r.observe(now,actual))throw new IllegalStateException("inconsistent observed recon admission");
        actors.put(scout,new Actor(owner,r.snapshot().expires()));markDirty();return true;
    }
    boolean reserveTimedAssault(UUID owner,UUID actor,long now){
        if(actor==null||!canDeploy(owner)||players.containsKey(actor)||actors.containsKey(actor)||!recon.issue(owner,actor,now))return false;
        actors.put(actor,new Actor(owner,recon.get(owner).snapshot().expires()));players.get(owner).spawnedActor();markDirty();return true;
    }
    /** One issued combat UUID stays consumed even if its spawn fails or its scout dies. */
    void retireTimed(UUID owner,long now){
        if(now<0)throw new IllegalArgumentException("negative world clock");
        if(!reconAvailable())return;var r=recon.get(owner);if(r==null||!r.active())return;
        r.close();var p=players.get(owner);if(p!=null)p.retireRecon(now);
        for(var entry:List.copyOf(actors.entrySet()))if(entry.getValue().owner().equals(owner))
            extendActor(entry.getKey(),Math.min(entry.getValue().expires(),ThreatProgress.deadline(now,ThreatProgress.LOST_SIGHT_TICKS)));
        markDirty();
    }
    void actorDied(UUID actor,long now){
        if(!reconAvailable())return;var a=actors.get(actor);if(a==null)return;var r=recon.get(a.owner());
        if(r!=null&&r.active()&&r.isScout(actor)&&!r.snapshot().observed())retireTimed(a.owner(),now);
        removeActor(actor);
    }
    void removeActor(UUID id){if(reconAvailable()&&actors.remove(id)!=null)markDirty();}
    void extendActor(UUID id,long expires){
        if(!reconAvailable())return;var old=actors.get(id);if(old==null)return;var timed=recon.get(old.owner());
        if(timed!=null&&timed.owns(id))expires=Math.min(expires,timed.snapshot().expires());
        if(old.expires()!=expires){actors.put(id,new Actor(old.owner(),expires));markDirty();}
    }
    void cancel(UUID owner,long now){if(!reconAvailable())return;var p=players.get(owner);if(p==null)return;var before=p.snapshot();
        var timed=recon.get(owner);if(timed!=null&&timed.active()){timed.close();p.retireRecon(now);markDirty();}else p.cancel(now);if(!before.equals(p.snapshot()))markDirty();
        for(var e:List.copyOf(actors.entrySet()))if(e.getValue().owner.equals(owner))extendActor(e.getKey(),Math.min(e.getValue().expires,ThreatProgress.deadline(now,200)));}
    void restorePlayer(UUID id,ThreatProgress progress){if(!available())throw new IllegalStateException("unknown threat state is read-only");if(players.size()>=MAX_PLAYERS&&!players.containsKey(id))throw new IllegalArgumentException("too many threat participants");players.put(id,progress);markDirty();}
    public static SurvivalThreatState fromNbt(NbtCompound n,RegistryWrapper.WrapperLookup lookup){
        var result=new SurvivalThreatState();
        if(n.contains("schema")&&(!n.contains("schema",NbtElement.INT_TYPE)||n.getInt("schema")<0||n.getInt("schema")>1)){result.opaqueRoot=n.copy();return result;}
        var data=n.getCompound("players");if(data.getKeys().size()<=MAX_PLAYERS)for(var id:data.getKeys())try{
            var p=data.getCompound(id);var phase=ThreatProgress.Phase.valueOf(p.getString("phase"));
            result.restorePlayer(UUID.fromString(id),new ThreatProgress(new ThreatProgress.Snapshot(phase,p.getLong("played"),p.getLong("since"),p.getLong("until"),p.getLong("encounterEnds"),p.getLong("alarmAt"),p.getLong("lastSeen"),p.getBoolean("pending"),p.getInt("spawned"),point(p.getCompound("origin")),point(p.getCompound("known")))));
        }catch(RuntimeException ignored){}
        var roster=n.getCompound("actors");if(roster.getKeys().size()<=MAX_ACTORS)for(var id:roster.getKeys())try{
            var a=roster.getCompound(id);var owner=a.getUuid("owner");if(result.players.containsKey(owner)&&result.rosterSize(owner)<ThreatProgress.PATROL_SIZE)result.actors.put(UUID.fromString(id),new Actor(owner,a.getLong("expires")));
        }catch(RuntimeException ignored){}
        var seen=n.getCompound("victims");if(seen.getKeys().size()<=MAX_VICTIMS)for(var id:seen.getKeys())try{long expiry=seen.getLong(id);if(expiry>=0)result.victims.put(UUID.fromString(id),expiry);}catch(RuntimeException ignored){}
        result.siegePlans=SiegePlans.read(n.get("siegePlans"),result.actors.keySet());
        result.recon=ReconAdmissions.read(n.get("recon"),result.players.keySet(),result.actors);
        return result;
    }
    private static ThreatProgress.Point point(NbtCompound n){return new ThreatProgress.Point(n.getDouble("x"),n.getDouble("y"),n.getDouble("z"));}
    private static NbtCompound point(ThreatProgress.Point p){var n=new NbtCompound();n.putDouble("x",p.x());n.putDouble("y",p.y());n.putDouble("z",p.z());return n;}
    @Override public NbtCompound writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup lookup){
        if(opaqueRoot!=null)return opaqueRoot.copy();
        n.putInt("schema",1);n.putString("faction",FACTION);var data=new NbtCompound();
        for(var e:new TreeMap<>(players).entrySet()){var p=e.getValue().snapshot();var tag=new NbtCompound();tag.putString("phase",p.phase().name());tag.putLong("played",p.played());tag.putLong("since",p.since());tag.putLong("until",p.until());tag.putLong("encounterEnds",p.encounterEnds());tag.putLong("alarmAt",p.alarmAt());tag.putLong("lastSeen",p.lastSeen());tag.putBoolean("pending",p.pending());tag.putInt("spawned",p.spawned());tag.put("origin",point(p.origin()));tag.put("known",point(p.known()));data.put(e.getKey().toString(),tag);}n.put("players",data);
        var roster=new NbtCompound();for(var e:new TreeMap<>(actors).entrySet()){var tag=new NbtCompound();tag.putUuid("owner",e.getValue().owner);tag.putLong("expires",e.getValue().expires);roster.put(e.getKey().toString(),tag);}n.put("actors",roster);
        var seen=new NbtCompound();for(var e:new TreeMap<>(victims).entrySet())seen.putLong(e.getKey().toString(),e.getValue());n.put("victims",seen);
        var extension=siegePlans.write();if(extension==null)n.remove("siegePlans");else n.put("siegePlans",extension);
        var reconExtension=recon.write();if(reconExtension==null)n.remove("recon");else n.put("recon",reconExtension);return n;
    }
}
