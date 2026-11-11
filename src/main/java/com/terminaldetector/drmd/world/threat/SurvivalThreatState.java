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
    public static SurvivalThreatState get(ServerWorld world){return world.getPersistentStateManager().getOrCreate(new Type<>(SurvivalThreatState::new,SurvivalThreatState::fromNbt,null),ID);}
    ThreatProgress player(UUID id){var old=players.get(id);if(old!=null)return old;if(players.size()>=MAX_PLAYERS)return null;
        var created=new ThreatProgress();players.put(id,created);markDirty();return created;}
    public Optional<ThreatProgress.Snapshot> progress(UUID id){var p=players.get(id);return p==null?Optional.empty():Optional.of(p.snapshot());}
    Collection<Map.Entry<UUID,Actor>> actors(){return List.copyOf(actors.entrySet());}
    public int rosterSize(UUID owner){int count=0;for(var actor:actors.values())if(actor.owner.equals(owner))count++;return count;}
    public int actorCount(){return actors.size();}
    boolean hasActor(UUID id){return actors.containsKey(id);}
    Actor actor(UUID id){return actors.get(id);}
    boolean matches(UUID id,UUID owner,long expires){return Objects.equals(actors.get(id),new Actor(owner,expires));}
    public boolean alarm(UUID owner,UUID victim,ThreatProgress.Point at,long now){
        if(now<0)throw new IllegalArgumentException("negative world clock");
        boolean purged=victims.entrySet().removeIf(e->now>=e.getValue());if(purged)markDirty();
        if(victims.containsKey(victim)||victims.size()>=MAX_VICTIMS||rosterSize(owner)>0)return false;
        var p=player(owner);if(p==null||!p.alarm(now,at))return false;
        victims.put(victim,ThreatProgress.deadline(now,ThreatProgress.ALARM_TTL));markDirty();return true;
    }
    boolean canDeploy(UUID owner){var p=players.get(owner);return actors.size()<MAX_ACTORS&&rosterSize(owner)<ThreatProgress.PATROL_SIZE
            &&p!=null&&(p.phase()==ThreatProgress.Phase.SEARCHING||p.phase()==ThreatProgress.Phase.HUNTING)&&p.spawned()<ThreatProgress.PATROL_SIZE;}
    boolean addActor(UUID owner,UUID id,long expires){
        if(actors.containsKey(id)||!canDeploy(owner))return false;
        actors.put(id,new Actor(owner,expires));players.get(owner).spawnedActor();markDirty();return true;
    }
    void removeActor(UUID id){if(actors.remove(id)!=null)markDirty();}
    void extendActor(UUID id,long expires){var old=actors.get(id);if(old!=null&&old.expires!=expires){actors.put(id,new Actor(old.owner,expires));markDirty();}}
    void cancel(UUID owner,long now){var p=players.get(owner);if(p==null)return;var before=p.snapshot();p.cancel(now);if(!before.equals(p.snapshot()))markDirty();
        for(var e:List.copyOf(actors.entrySet()))if(e.getValue().owner.equals(owner))extendActor(e.getKey(),Math.min(e.getValue().expires,ThreatProgress.deadline(now,200)));}
    void restorePlayer(UUID id,ThreatProgress progress){if(players.size()>=MAX_PLAYERS&&!players.containsKey(id))throw new IllegalArgumentException("too many threat participants");players.put(id,progress);markDirty();}
    public static SurvivalThreatState fromNbt(NbtCompound n,RegistryWrapper.WrapperLookup lookup){
        var result=new SurvivalThreatState();if(n.getInt("schema")>1)return result;
        var data=n.getCompound("players");if(data.getKeys().size()<=MAX_PLAYERS)for(var id:data.getKeys())try{
            var p=data.getCompound(id);var phase=ThreatProgress.Phase.valueOf(p.getString("phase"));
            result.restorePlayer(UUID.fromString(id),new ThreatProgress(new ThreatProgress.Snapshot(phase,p.getLong("played"),p.getLong("since"),p.getLong("until"),p.getLong("encounterEnds"),p.getLong("alarmAt"),p.getLong("lastSeen"),p.getBoolean("pending"),p.getInt("spawned"),point(p.getCompound("origin")),point(p.getCompound("known")))));
        }catch(RuntimeException ignored){}
        var roster=n.getCompound("actors");if(roster.getKeys().size()<=MAX_ACTORS)for(var id:roster.getKeys())try{
            var a=roster.getCompound(id);var owner=a.getUuid("owner");if(result.players.containsKey(owner)&&result.rosterSize(owner)<ThreatProgress.PATROL_SIZE)result.actors.put(UUID.fromString(id),new Actor(owner,a.getLong("expires")));
        }catch(RuntimeException ignored){}
        var seen=n.getCompound("victims");if(seen.getKeys().size()<=MAX_VICTIMS)for(var id:seen.getKeys())try{long expiry=seen.getLong(id);if(expiry>=0)result.victims.put(UUID.fromString(id),expiry);}catch(RuntimeException ignored){}
        return result;
    }
    private static ThreatProgress.Point point(NbtCompound n){return new ThreatProgress.Point(n.getDouble("x"),n.getDouble("y"),n.getDouble("z"));}
    private static NbtCompound point(ThreatProgress.Point p){var n=new NbtCompound();n.putDouble("x",p.x());n.putDouble("y",p.y());n.putDouble("z",p.z());return n;}
    @Override public NbtCompound writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup lookup){
        n.putInt("schema",1);n.putString("faction",FACTION);var data=new NbtCompound();
        for(var e:new TreeMap<>(players).entrySet()){var p=e.getValue().snapshot();var tag=new NbtCompound();tag.putString("phase",p.phase().name());tag.putLong("played",p.played());tag.putLong("since",p.since());tag.putLong("until",p.until());tag.putLong("encounterEnds",p.encounterEnds());tag.putLong("alarmAt",p.alarmAt());tag.putLong("lastSeen",p.lastSeen());tag.putBoolean("pending",p.pending());tag.putInt("spawned",p.spawned());tag.put("origin",point(p.origin()));tag.put("known",point(p.known()));data.put(e.getKey().toString(),tag);}n.put("players",data);
        var roster=new NbtCompound();for(var e:new TreeMap<>(actors).entrySet()){var tag=new NbtCompound();tag.putUuid("owner",e.getValue().owner);tag.putLong("expires",e.getValue().expires);roster.put(e.getKey().toString(),tag);}n.put("actors",roster);
        var seen=new NbtCompound();for(var e:new TreeMap<>(victims).entrySet())seen.putLong(e.getKey().toString(),e.getValue());n.put("victims",seen);return n;
    }
}
