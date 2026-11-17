package com.terminaldetector.drmd.world.threat;

import net.minecraft.nbt.*;
import java.util.*;

/** Bounded optional intent extension. Unknown data stays opaque and cannot acquire runtime authority. */
final class SiegePlans {
    static final int MAX_PLANS=8;
    private record Stored(String key,SiegePlan.Snapshot snapshot,NbtCompound original){}
    private final Map<UUID,Stored> plans=new LinkedHashMap<>();
    private final Map<String,NbtElement> quarantined=new LinkedHashMap<>();
    private NbtCompound envelope=new NbtCompound();
    private NbtElement opaque;
    private boolean present;

    static SiegePlans read(NbtElement raw,Set<UUID> liveActors){
        var result=new SiegePlans();if(raw==null)return result;result.present=true;var owned=raw.copy();
        if(!(owned instanceof NbtCompound tag)||!tag.contains("schema",NbtElement.INT_TYPE)||tag.getInt("schema")!=1
            ||!tag.contains("plans",NbtElement.COMPOUND_TYPE)||tag.getCompound("plans").getKeys().size()>MAX_PLANS){result.opaque=owned;return result;}
        var records=tag.getCompound("plans");result.envelope=tag;result.envelope.remove("plans");
        for(var key:new TreeSet<>(records.getKeys())){
            var value=records.get(key);
            try{
                var id=UUID.fromString(key);if(!(value instanceof NbtCompound p)||result.plans.containsKey(id))throw new IllegalArgumentException("invalid duplicate plan record");
                var snapshot=decode(id,p);
                if(result.conflicts(snapshot,null,liveActors))throw new IllegalArgumentException("conflicting plan identity");
                result.plans.put(id,new Stored(key,snapshot,p.copy()));
            }catch(RuntimeException invalid){result.quarantined.put(key,value.copy());}
        }
        return result;
    }
    boolean readOnly(){return opaque!=null;}
    int size(){return plans.size();}
    Optional<SiegePlan.Snapshot> get(UUID id){var p=plans.get(id);return p==null?Optional.empty():Optional.of(p.snapshot);}
    boolean put(SiegePlan.Snapshot next,Set<UUID> liveActors){
        Objects.requireNonNull(next);if(readOnly())return false;
        var old=plans.get(next.id());
        if(old==null&&plans.size()+quarantined.size()>=MAX_PLANS)return false;
        for(var key:quarantined.keySet())try{if(UUID.fromString(key).equals(next.id()))return false;}catch(IllegalArgumentException ignored){}
        if(old!=null&&(old.snapshot.equals(next)||!SiegePlan.canFollow(old.snapshot,next)))return false;
        if(conflicts(next,next.id(),liveActors))return false;
        plans.put(next.id(),new Stored(old==null?next.id().toString():old.key,next,old==null?new NbtCompound():old.original));present=true;return true;
    }
    boolean remove(UUID id){var old=plans.get(id);if(readOnly()||old==null||old.snapshot.phase()!=SiegePlan.Phase.CLOSED)return false;plans.remove(id);return true;}
    private boolean conflicts(SiegePlan.Snapshot next,UUID except,Set<UUID> liveActors){
        for(int slot=0;slot<SiegePlan.SLOTS;slot++){var actor=next.actors().at(slot);if(actor!=null&&liveActors.contains(actor))return true;}
        for(var entry:plans.entrySet())if(!entry.getKey().equals(except)){
            var other=entry.getValue().snapshot;if(other.participant().equals(next.participant()))return true;
            for(int slot=0;slot<SiegePlan.SLOTS;slot++){var actor=next.actors().at(slot);if(actor!=null&&other.actors().contains(actor))return true;}
        }return false;
    }
    NbtElement write(){
        if(opaque!=null)return opaque.copy();if(!present)return null;
        var result=envelope.copy();result.putInt("schema",1);var records=new NbtCompound();
        for(var entry:quarantined.entrySet())records.put(entry.getKey(),entry.getValue().copy());
        for(var stored:plans.values())records.put(stored.key,encode(stored.snapshot,stored.original));
        result.put("plans",records);return result;
    }
    private static SiegePlan.Snapshot decode(UUID id,NbtCompound p){
        if(!string(p,"faction").equals(SurvivalThreatState.FACTION))throw new IllegalArgumentException("unknown existing coalition");
        var phase=SiegePlan.Phase.valueOf(string(p,"phase"));var actors=new SiegePlan.Bindings(optionalUuid(p,"actor0"),optionalUuid(p,"actor1"));
        require(p,"acks",NbtElement.LIST_TYPE);var list=(NbtList)p.get("acks");
        if(list.size()>SiegePlan.MAX_ACKS||!list.isEmpty()&&list.getHeldType()!=NbtElement.COMPOUND_TYPE)throw new IllegalArgumentException("invalid acknowledgement list");
        var acks=new ArrayList<SiegePlan.Ack>();
        for(int i=0;i<list.size();i++){var ack=list.getCompound(i);require(ack,"slot",NbtElement.INT_TYPE);acks.add(new SiegePlan.Ack(ack.getInt("slot"),uuid(ack,"actor"),uuid(ack,"shot"),number(ack,"at")));}
        return new SiegePlan.Snapshot(id,uuid(p,"participant"),string(p,"world"),phase,number(p,"proposedAt"),number(p,"clock"),number(p,"online"),
            number(p,"since"),number(p,"until"),number(p,"warningEnds"),number(p,"encounterEnds"),number(p,"cooldownEnds"),
            observation(compound(p,"origin")),observation(compound(p,"known")),actors,acks);
    }
    private static SiegePlan.Observation observation(NbtCompound p){
        UUID body=optionalUuid(p,"body");ThreatProgress.Point local=p.contains("local")?point(compound(p,"local")):null;
        return new SiegePlan.Observation(uuid(p,"site"),uuid(p,"observer"),uuid(p,"participant"),string(p,"world"),number(p,"at"),point(compound(p,"point")),body,local);
    }
    private static NbtCompound encode(SiegePlan.Snapshot s,NbtCompound original){
        var p=original.copy();p.putUuid("participant",s.participant());p.putString("faction",SurvivalThreatState.FACTION);p.putString("world",s.world());p.putString("phase",s.phase().name());
        p.putLong("proposedAt",s.proposedAt());p.putLong("clock",s.clock());p.putLong("online",s.online());p.putLong("since",s.since());p.putLong("until",s.until());
        p.putLong("warningEnds",s.warningEnds());p.putLong("encounterEnds",s.encounterEnds());p.putLong("cooldownEnds",s.cooldownEnds());
        p.put("origin",observation(s.origin(),p.getCompound("origin")));p.put("known",observation(s.known(),p.getCompound("known")));
        for(int slot=0;slot<SiegePlan.SLOTS;slot++){String key="actor"+slot;var actor=s.actors().at(slot);if(actor==null)p.remove(key);else p.putUuid(key,actor);}
        var previous=p.getList("acks",NbtElement.COMPOUND_TYPE);var list=new NbtList();
        for(int i=0;i<s.acks().size();i++){
            var ack=s.acks().get(i);var n=i<previous.size()?previous.getCompound(i).copy():new NbtCompound();
            n.putInt("slot",ack.slot());n.putUuid("actor",ack.actor());n.putUuid("shot",ack.shot());n.putLong("at",ack.at());list.add(n);
        }
        p.put("acks",list);return p;
    }
    private static NbtCompound observation(SiegePlan.Observation s,NbtCompound original){
        var p=original.copy();p.putUuid("site",s.site());p.putUuid("observer",s.observer());p.putUuid("participant",s.participant());p.putString("world",s.world());p.putLong("at",s.at());
        p.put("point",point(s.point(),p.getCompound("point")));
        if(s.body()==null){p.remove("body");p.remove("local");}else{p.putUuid("body",s.body());p.put("local",point(s.local(),p.getCompound("local")));}
        return p;
    }
    private static ThreatProgress.Point point(NbtCompound p){require(p,"x",NbtElement.DOUBLE_TYPE);require(p,"y",NbtElement.DOUBLE_TYPE);require(p,"z",NbtElement.DOUBLE_TYPE);return new ThreatProgress.Point(p.getDouble("x"),p.getDouble("y"),p.getDouble("z"));}
    private static NbtCompound point(ThreatProgress.Point s,NbtCompound original){var p=original.copy();p.putDouble("x",s.x());p.putDouble("y",s.y());p.putDouble("z",s.z());return p;}
    private static long number(NbtCompound p,String key){require(p,key,NbtElement.LONG_TYPE);return p.getLong(key);}
    private static String string(NbtCompound p,String key){require(p,key,NbtElement.STRING_TYPE);return p.getString(key);}
    private static UUID uuid(NbtCompound p,String key){if(!p.containsUuid(key))throw new IllegalArgumentException("missing UUID "+key);return p.getUuid(key);}
    private static UUID optionalUuid(NbtCompound p,String key){return p.contains(key)?uuid(p,key):null;}
    private static NbtCompound compound(NbtCompound p,String key){require(p,key,NbtElement.COMPOUND_TYPE);return p.getCompound(key);}
    private static void require(NbtCompound p,String key,int type){if(!p.contains(key,type))throw new IllegalArgumentException("missing typed plan field "+key);}
}
