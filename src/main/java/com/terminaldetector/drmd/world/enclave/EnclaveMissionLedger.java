package com.terminaldetector.drmd.world.enclave;

import net.minecraft.nbt.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.terminaldetector.drmd.world.enclave.EnclaveMissionContract.*;

/** Save-owned mission protocol. World authority and successful mining belong to the adapter. */
public final class EnclaveMissionLedger {
    public static final int MAX_SITES=64, MAX_OWNERS=64, MAX_INSTANCES=256;
    public enum Outcome { ACCEPTED, ACTIVE, READY, CLAIMED, UNAVAILABLE, BLOCKED, OWNER_BUSY, CAPACITY }
    public record Context(String dimension,long seed,String backend,int bottomY,int topY) {
        public Context {
            if(dimension==null||dimension.length()>160||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                ||backend==null||backend.isBlank()||backend.length()>80||bottomY>=topY)
                throw new IllegalArgumentException("invalid mission world context");
        }
        boolean contains(Point p) { return p.y()>=bottomY&&p.y()<topY; }
    }
    public record Site(UUID id,Context context,Point herald,Point anchor,String origin,
                       EnclaveMissionContract contract,boolean valid) {
        public Site {
            Objects.requireNonNull(id);Objects.requireNonNull(context);Objects.requireNonNull(herald);
            Objects.requireNonNull(anchor);Objects.requireNonNull(contract);
            // This playable slice uses the existing MINERS/EXPLORE_SHAFT relationship only.
            if(!EnclaveOrigin.MINERS.id.equals(origin)||!context.contains(herald)||!context.contains(anchor))
                throw new IllegalArgumentException("unsupported site origin or bounds");
            for(var step:contract.at(anchor)) {
                if(step.type()==Type.BREAK_BLOCK&&!context.contains(step.block()))
                    throw new IllegalArgumentException("objective outside backend bounds");
                if(step.type()==Type.ENTER_VOLUME&&(!context.contains(step.volume().min())
                    ||step.volume().max().y()>context.topY()))
                    throw new IllegalArgumentException("entry outside backend bounds");
                if(step.type()==Type.ENTER_VOLUME&&step.volume().contains(herald))
                    throw new IllegalArgumentException("entry and extraction must be separate");
            }
        }
        List<Step> steps() { return contract.at(anchor); }
    }
    public record Progress(UUID owner,UUID site,String contractId,int cursor,Outcome status) {}
    private record Key(UUID owner,UUID site,String contractId) {}
    private record Location(String dimension,Point herald) {}
    private static final class Instance {
        final Key key; int cursor; Outcome status; boolean entryArmed;
        Instance(Key key,int cursor,Outcome status) { this.key=key;this.cursor=cursor;this.status=status; }
        Progress view(int count) {
            return new Progress(key.owner,key.site,key.contractId,cursor,
                status==Outcome.ACTIVE&&cursor==count?Outcome.READY:status);
        }
    }
    private UUID namespace;
    private final Map<UUID,Site> sites=new LinkedHashMap<>();
    private final Map<Location,UUID> locations=new HashMap<>();
    private final Map<Key,Instance> instances=new LinkedHashMap<>();
    private final Map<UUID,Instance> active=new HashMap<>();
    private final Set<UUID> owners=new HashSet<>();
    private NbtElement quarantined;
    private String problem;
    private long mutationVersion;

    public boolean available() { return problem==null; }
    public Optional<String> problem() { return Optional.ofNullable(problem); }
    public long mutationVersion() { return mutationVersion; }
    public Optional<UUID> namespace() { return Optional.ofNullable(namespace); }
    public Optional<Site> site(UUID id) { return Optional.ofNullable(sites.get(id)); }
    public Optional<Site> site(String dimension,Point herald) {
        return site(locations.get(new Location(dimension,herald)));
    }
    public Set<UUID> activeOwners() { return Set.copyOf(active.keySet()); }
    public Optional<Progress> active(UUID owner) {
        var current=active.get(owner);
        return current==null?Optional.empty():Optional.of(current.view(sites.get(current.key.site).steps().size()));
    }
    public Site bind(Context context,Point herald,Point anchor,String origin,EnclaveMissionContract contract) {
        if(!available())throw new IllegalStateException("mission state quarantined");
        var location=new Location(context.dimension,herald);
        if(locations.containsKey(location))throw new IllegalStateException("site identity is already reserved");
        if(sites.size()>=MAX_SITES)throw new IllegalStateException("site capacity reached");
        var site=new Site(UUID.randomUUID(),context,herald,anchor,origin,contract,true);
        if(namespace==null)namespace=UUID.randomUUID();
        sites.put(site.id,site);locations.put(location,site.id);mutationVersion++;
        return site;
    }
    private Site usable(UUID id,Context actual,String currentHash) {
        if(!available())return null;
        var site=sites.get(id);
        return site!=null&&site.valid&&site.context.equals(actual)&&site.contract.hash().equals(currentHash)?site:null;
    }
    public Outcome status(UUID owner,UUID id,Context actual,String currentHash) {
        var site=usable(id,actual,currentHash);
        if(site==null)return sites.containsKey(id)||!available()?Outcome.BLOCKED:Outcome.UNAVAILABLE;
        var instance=instances.get(new Key(owner,id,site.contract.id()));
        if(instance==null)return Outcome.UNAVAILABLE;
        return instance.view(site.steps().size()).status;
    }
    public Outcome accept(UUID owner,UUID id,Context actual,String currentHash,Point ownerPosition) {
        Objects.requireNonNull(owner);Objects.requireNonNull(ownerPosition);
        var site=usable(id,actual,currentHash);
        if(site==null)return sites.containsKey(id)||!available()?Outcome.BLOCKED:Outcome.UNAVAILABLE;
        var key=new Key(owner,id,site.contract.id());
        var existing=instances.get(key);
        if(existing!=null)return existing.view(site.steps().size()).status;
        if(active.containsKey(owner))return Outcome.OWNER_BUSY;
        if(instances.size()>=MAX_INSTANCES||(!owners.contains(owner)&&owners.size()>=MAX_OWNERS))return Outcome.CAPACITY;
        var instance=new Instance(key,0,Outcome.ACTIVE);
        instance.entryArmed=!site.steps().getFirst().volume().contains(ownerPosition);
        instances.put(key,instance);active.put(owner,instance);owners.add(owner);mutationVersion++;
        return Outcome.ACCEPTED;
    }
    /** Explicit owner withdrawal forgets only unclaimed progress; claim receipts never leave history. */
    public boolean abandon(UUID owner,Context actual) {
        Objects.requireNonNull(owner);Objects.requireNonNull(actual);
        if(!available())return false;
        var instance=active.get(owner);if(instance==null||instance.status!=Outcome.ACTIVE)return false;
        var site=sites.get(instance.key.site);
        if(site==null||!site.context.equals(actual))return false;
        active.remove(owner);instances.remove(instance.key);
        if(instances.keySet().stream().noneMatch(key->key.owner.equals(owner)))owners.remove(owner);
        mutationVersion++;return true;
    }
    public void pause(UUID owner) {
        var instance=active.get(owner);
        if(instance!=null)instance.entryArmed=false;
    }
    public boolean observePosition(UUID owner,Context actual,String currentHash,Point position) {
        Objects.requireNonNull(position);
        var instance=active.get(owner);
        if(instance==null)return false;
        var site=usable(instance.key.site,actual,currentHash);
        if(site==null) { pause(owner);return false; }
        var steps=site.steps();
        if(instance.cursor>=steps.size())return false;
        var step=steps.get(instance.cursor);
        if(step.type()!=Type.ENTER_VOLUME)return false;
        if(!step.volume().contains(position)) { instance.entryArmed=true;return false; }
        if(!instance.entryArmed)return false;
        advance(instance,steps,position);return true;
    }
    public boolean observeBreak(UUID owner,Context actual,String currentHash,Point broken,String previousBlock,Point ownerPosition) {
        Objects.requireNonNull(broken);Objects.requireNonNull(previousBlock);Objects.requireNonNull(ownerPosition);
        var instance=active.get(owner);
        if(instance==null)return false;
        var site=usable(instance.key.site,actual,currentHash);
        if(site==null) { pause(owner);return false; }
        var steps=site.steps();
        if(instance.cursor>=steps.size())return false;
        var step=steps.get(instance.cursor);
        if(step.type()!=Type.BREAK_BLOCK||!step.block().equals(broken)||!step.expectedBlock().equals(previousBlock))return false;
        advance(instance,steps,ownerPosition);return true;
    }
    private void advance(Instance instance,List<Step> steps,Point ownerPosition) {
        instance.cursor++;instance.entryArmed=false;
        if(instance.cursor<steps.size()&&steps.get(instance.cursor).type()==Type.ENTER_VOLUME)
            instance.entryArmed=!steps.get(instance.cursor).volume().contains(ownerPosition);
        mutationVersion++;
    }
    /** Called only by the same FactionMemory transaction that applies the fixed reputation delta. */
    boolean claim(UUID owner,UUID id,Context actual,String currentHash) {
        var site=usable(id,actual,currentHash);
        if(site==null)return false;
        var instance=instances.get(new Key(owner,id,site.contract.id()));
        if(instance==null||instance.status!=Outcome.ACTIVE||instance.cursor!=site.steps().size())return false;
        instance.status=Outcome.CLAIMED;instance.entryArmed=false;active.remove(owner);mutationVersion++;return true;
    }
    public boolean invalidate(String dimension,Point herald) {
        if(!available())return false;
        var old=site(dimension,herald).orElse(null);
        if(old==null||!old.valid)return false;
        sites.put(old.id,new Site(old.id,old.context,old.herald,old.anchor,old.origin,old.contract,false));
        for(var instance:instances.values())if(instance.key.site.equals(old.id)&&instance.status==Outcome.ACTIVE) {
            instance.status=Outcome.BLOCKED;instance.entryArmed=false;active.remove(instance.key.owner);
        }
        mutationVersion++;return true;
    }

    /** Missing legacy extension stays missing until an actual bind. Quarantine preserves owned raw data. */
    public static EnclaveMissionLedger read(NbtElement raw) {
        var result=new EnclaveMissionLedger();
        if(raw==null)return result;
        try {
            if(!(raw instanceof NbtCompound root))throw new IllegalArgumentException("mission compound required");
            keys(root,"schema","namespace","sites","instances");
            if(integer(root,"schema")!=1)throw new IllegalArgumentException("unsupported mission schema");
            result.namespace=uuid(root,"namespace");
            var savedSites=list(root,"sites",MAX_SITES);
            for(int i=0;i<savedSites.size();i++) {
                var tag=savedSites.getCompound(i);
                keys(tag,"id","context","herald","anchor","origin","definition","hash","valid");
                var json=text(tag,"definition",MAX_BYTES);
                var contract=EnclaveMissionContract.parse(json.getBytes(StandardCharsets.UTF_8));
                if(!contract.hash().equals(text(tag,"hash",64)))throw new IllegalArgumentException("mission definition hash mismatch");
                var site=new Site(uuid(tag,"id"),context(compound(tag,"context")),point(compound(tag,"herald")),
                    point(compound(tag,"anchor")),text(tag,"origin",80),contract,bool(tag,"valid"));
                if(result.sites.putIfAbsent(site.id,site)!=null
                    ||result.locations.putIfAbsent(new Location(site.context.dimension,site.herald),site.id)!=null)
                    throw new IllegalArgumentException("duplicate reserved mission site");
            }
            var savedInstances=list(root,"instances",MAX_INSTANCES);
            for(int i=0;i<savedInstances.size();i++) {
                var tag=savedInstances.getCompound(i);keys(tag,"owner","site","contract","cursor","status");
                var key=new Key(uuid(tag,"owner"),uuid(tag,"site"),text(tag,"contract",160));
                var site=result.sites.get(key.site);
                if(site==null||!site.contract.id().equals(key.contractId))throw new IllegalArgumentException("orphan mission instance");
                int cursor=integer(tag,"cursor");
                var status=Outcome.valueOf(text(tag,"status",24));
                if(cursor<0||cursor>site.steps().size()
                    ||(status!=Outcome.ACTIVE&&status!=Outcome.CLAIMED&&status!=Outcome.BLOCKED)
                    ||(status==Outcome.CLAIMED&&cursor!=site.steps().size())
                    ||(status==Outcome.ACTIVE&&!site.valid)||(status==Outcome.BLOCKED&&site.valid))
                    throw new IllegalArgumentException("invalid mission cursor/status");
                var instance=new Instance(key,cursor,status);
                if(result.instances.putIfAbsent(key,instance)!=null)throw new IllegalArgumentException("duplicate mission claim key");
                if(status==Outcome.ACTIVE&&result.active.putIfAbsent(key.owner,instance)!=null)
                    throw new IllegalArgumentException("multiple active missions for owner");
                result.owners.add(key.owner);
                if(result.owners.size()>MAX_OWNERS)throw new IllegalArgumentException("mission owner cap exceeded");
            }
            if(result.sites.isEmpty())throw new IllegalArgumentException("empty reserved mission namespace");
            return result;
        } catch(RuntimeException invalid) {
            var closed=new EnclaveMissionLedger();closed.quarantined=raw.copy();
            closed.problem="Mission extension quarantined: "+invalid.getClass().getSimpleName();return closed;
        }
    }
    public NbtElement write() {
        if(quarantined!=null)return quarantined.copy();
        if(namespace==null)return null;
        var root=new NbtCompound();root.putInt("schema",1);root.putUuid("namespace",namespace);
        var savedSites=new NbtList();
        for(var site:sites.values()) {
            var tag=new NbtCompound();tag.putUuid("id",site.id);tag.put("context",context(site.context));
            tag.put("herald",point(site.herald));tag.put("anchor",point(site.anchor));tag.putString("origin",site.origin);
            tag.putString("definition",site.contract.toJson().toString());tag.putString("hash",site.contract.hash());
            tag.putBoolean("valid",site.valid);savedSites.add(tag);
        }
        root.put("sites",savedSites);var savedInstances=new NbtList();
        for(var instance:instances.values()) {
            var tag=new NbtCompound();tag.putUuid("owner",instance.key.owner);tag.putUuid("site",instance.key.site);
            tag.putString("contract",instance.key.contractId);tag.putInt("cursor",instance.cursor);
            tag.putString("status",instance.status.name());savedInstances.add(tag);
        }
        root.put("instances",savedInstances);return root;
    }
    private static NbtCompound context(Context context) {
        var tag=new NbtCompound();tag.putString("dimension",context.dimension);tag.putLong("seed",context.seed);
        tag.putString("backend",context.backend);tag.putInt("bottom",context.bottomY);tag.putInt("top",context.topY);return tag;
    }
    private static Context context(NbtCompound tag) {
        keys(tag,"dimension","seed","backend","bottom","top");require(tag,"seed",NbtElement.LONG_TYPE);
        return new Context(text(tag,"dimension",160),tag.getLong("seed"),text(tag,"backend",80),integer(tag,"bottom"),integer(tag,"top"));
    }
    private static NbtCompound point(Point point) {
        var tag=new NbtCompound();tag.putInt("x",point.x());tag.putInt("y",point.y());tag.putInt("z",point.z());return tag;
    }
    private static Point point(NbtCompound tag) {
        keys(tag,"x","y","z");return new Point(integer(tag,"x"),integer(tag,"y"),integer(tag,"z"));
    }
    private static void keys(NbtCompound tag,String... keys) {
        if(!tag.getKeys().equals(Set.of(keys)))throw new IllegalArgumentException("unknown/missing mission state fields");
    }
    private static void require(NbtCompound tag,String key,int type) {
        if(!tag.contains(key,type))throw new IllegalArgumentException("wrong mission NBT type: "+key);
    }
    private static int integer(NbtCompound tag,String key) { require(tag,key,NbtElement.INT_TYPE);return tag.getInt(key); }
    private static boolean bool(NbtCompound tag,String key) {
        require(tag,key,NbtElement.BYTE_TYPE);byte value=tag.getByte(key);
        if(value!=0&&value!=1)throw new IllegalArgumentException("invalid mission boolean");return value==1;
    }
    private static String text(NbtCompound tag,String key,int cap) {
        require(tag,key,NbtElement.STRING_TYPE);var value=tag.getString(key);
        if(value.isEmpty()||value.length()>cap)throw new IllegalArgumentException("mission text bound exceeded");return value;
    }
    private static UUID uuid(NbtCompound tag,String key) {
        require(tag,key,NbtElement.INT_ARRAY_TYPE);
        if(tag.getIntArray(key).length!=4)throw new IllegalArgumentException("invalid mission UUID");return tag.getUuid(key);
    }
    private static NbtCompound compound(NbtCompound tag,String key) { require(tag,key,NbtElement.COMPOUND_TYPE);return tag.getCompound(key); }
    private static NbtList list(NbtCompound tag,String key,int cap) {
        require(tag,key,NbtElement.LIST_TYPE);var value=(NbtList)tag.get(key);
        if(value.size()>cap||(!value.isEmpty()&&value.getHeldType()!=NbtElement.COMPOUND_TYPE))
            throw new IllegalArgumentException("mission list bound/type exceeded");return value;
    }
}
