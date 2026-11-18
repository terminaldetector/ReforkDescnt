package com.terminaldetector.drmd.world.threat;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Optional bounded extension. A rejected envelope is retained verbatim and never becomes empty authority. */
final class ReconAdmissions {
    private final Map<UUID,ReconAdmission> attempts=new LinkedHashMap<>();
    private final Set<UUID> reservedIds=new HashSet<>();
    private NbtElement opaque;private boolean present;
    boolean available(){return opaque==null;}
    ReconAdmission get(UUID owner){return attempts.get(owner);}
    Optional<ReconAdmission.Snapshot> snapshot(UUID owner){var record=get(owner);return record==null?Optional.empty():Optional.of(record.snapshot());}
    boolean reserved(UUID id){return reservedIds.contains(id);}
    private static Set<UUID> ids(ReconAdmission.Snapshot s){return s.assaultIssued()?Set.of(s.attempt(),s.scout(),s.assault()):Set.of(s.attempt(),s.scout());}
    boolean put(ReconAdmission next){
        if(!available())return false;var s=next.snapshot();var old=attempts.get(s.owner());
        if(old!=null&&old.active()||old==null&&attempts.size()>=SurvivalThreatState.MAX_PLAYERS)return false;
        for(var id:ids(s))if(reservedIds.contains(id))return false;
        if(old!=null)reservedIds.removeAll(ids(old.snapshot()));
        attempts.put(s.owner(),next);reservedIds.addAll(ids(s));present=true;return true;
    }
    boolean issue(UUID owner,UUID actor,long now){
        var attempt=get(owner);if(!available()||attempt==null||reservedIds.contains(actor)||!attempt.issue(actor,now))return false;
        reservedIds.add(actor);return true;
    }
    boolean rollbackUnspawned(UUID owner,UUID scout){
        var record=get(owner);if(!available()||record==null||!record.isScout(scout)||record.snapshot().observed()||record.snapshot().assaultIssued())return false;
        attempts.remove(owner);reservedIds.removeAll(ids(record.snapshot()));return true;
    }
    static ReconAdmissions read(NbtElement raw,Set<UUID> players,Map<UUID,SurvivalThreatState.Actor> actors){
        var out=new ReconAdmissions();if(raw==null)return out;var preserved=raw.copy();out.present=true;
        try{
            if(!(raw instanceof NbtCompound root))throw new IllegalArgumentException("recon compound required");
            keys(root,Set.of("schema","attempts"));if(integer(root,"schema")!=1)throw new IllegalArgumentException("unsupported recon schema");
            var records=compound(root,"attempts");if(records.getKeys().size()>SurvivalThreatState.MAX_PLAYERS)throw new IllegalArgumentException("recon owner cap");
            var allIds=new HashSet<UUID>();
            for(var key:records.getKeys()){
                var owner=UUID.fromString(key);if(!owner.toString().equals(key)||!players.contains(owner)||out.attempts.containsKey(owner))throw new IllegalArgumentException("orphan/duplicate recon owner");
                var p=compound(records,key);boolean issued=bool(p,"assaultIssued");
                var expected=new HashSet<>(Set.of("attempt","scout","scoutPurpose","world","seed","backend","bottom","top","origin","started","expires","observed","observedAt","closed","assaultIssued"));
                if(issued){expected.add("assault");expected.add("assaultPurpose");}keys(p,expected);
                if(!string(p,"scoutPurpose").equals("SCOUT_SUPPORT")||issued&&!string(p,"assaultPurpose").equals("ASSAULT_ORB"))throw new IllegalArgumentException("unknown recon purpose");
                var context=new ReconAdmission.Context(string(p,"world"),number(p,"seed"),string(p,"backend"),integer(p,"bottom"),integer(p,"top"));
                var point=compound(p,"origin");keys(point,Set.of("x","y","z"));
                var s=new ReconAdmission.Snapshot(uuid(p,"attempt"),owner,context,new ThreatProgress.Point(decimal(point,"x"),decimal(point,"y"),decimal(point,"z")),uuid(p,"scout"),number(p,"started"),number(p,"expires"),bool(p,"observed"),number(p,"observedAt"),bool(p,"closed"),issued,issued?uuid(p,"assault"):null);
                for(var id:issued?Set.of(s.attempt(),s.scout(),s.assault()):Set.of(s.attempt(),s.scout())){
                    if(players.contains(id)||!allIds.add(id))throw new IllegalArgumentException("duplicate recon identity");
                    var actor=actors.get(id);if(actor!=null&&(id.equals(s.attempt())||!actor.owner().equals(owner)||actor.expires()>s.expires()))throw new IllegalArgumentException("recon roster mismatch");
                }
                out.attempts.put(owner,new ReconAdmission(s));
            }
            out.reservedIds.addAll(allIds);
            return out;
        }catch(RuntimeException invalid){var quarantined=new ReconAdmissions();quarantined.present=true;quarantined.opaque=preserved;return quarantined;}
    }
    NbtElement write(){
        if(opaque!=null)return opaque.copy();if(!present)return null;
        var root=new NbtCompound();root.putInt("schema",1);var records=new NbtCompound();
        for(var entry:new TreeMap<>(attempts).entrySet()){
            var s=entry.getValue().snapshot();var p=new NbtCompound();p.putUuid("attempt",s.attempt());p.putUuid("scout",s.scout());p.putString("scoutPurpose","SCOUT_SUPPORT");
            p.putString("world",s.context().world());p.putLong("seed",s.context().seed());p.putString("backend",s.context().backend());p.putInt("bottom",s.context().bottom());p.putInt("top",s.context().top());
            var at=new NbtCompound();at.putDouble("x",s.origin().x());at.putDouble("y",s.origin().y());at.putDouble("z",s.origin().z());p.put("origin",at);
            p.putLong("started",s.started());p.putLong("expires",s.expires());p.putBoolean("observed",s.observed());p.putLong("observedAt",s.observedAt());p.putBoolean("closed",s.closed());p.putBoolean("assaultIssued",s.assaultIssued());
            if(s.assaultIssued()){p.putUuid("assault",s.assault());p.putString("assaultPurpose","ASSAULT_ORB");}records.put(entry.getKey().toString(),p);
        }root.put("attempts",records);return root;
    }
    private static void keys(NbtCompound p,Set<String> keys){if(!p.getKeys().equals(keys))throw new IllegalArgumentException("unknown/missing recon fields");}
    private static void require(NbtCompound p,String key,int type){if(!p.contains(key,type))throw new IllegalArgumentException("wrong recon field type");}
    private static NbtCompound compound(NbtCompound p,String key){require(p,key,NbtElement.COMPOUND_TYPE);return p.getCompound(key);}
    private static int integer(NbtCompound p,String key){require(p,key,NbtElement.INT_TYPE);return p.getInt(key);}
    private static long number(NbtCompound p,String key){require(p,key,NbtElement.LONG_TYPE);return p.getLong(key);}
    private static double decimal(NbtCompound p,String key){require(p,key,NbtElement.DOUBLE_TYPE);return p.getDouble(key);}
    private static UUID uuid(NbtCompound p,String key){if(!p.containsUuid(key))throw new IllegalArgumentException("recon UUID required");return p.getUuid(key);}
    private static String string(NbtCompound p,String key){require(p,key,NbtElement.STRING_TYPE);String v=p.getString(key);if(v.isEmpty()||v.length()>160)throw new IllegalArgumentException("recon string bound");return v;}
    private static boolean bool(NbtCompound p,String key){require(p,key,NbtElement.BYTE_TYPE);byte v=p.getByte(key);if(v!=0&&v!=1)throw new IllegalArgumentException("recon bool");return v==1;}
}
