package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.BodyJointConstraint;
import com.terminaldetector.drmd.physics.collision.BodyBounds;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import java.util.*;

/** One authoritative joint registry per server world, independent of entity load/save order.
 * No duplicate entity-owned links can resurrect a broken joint after an endpoint reload. */
public final class BodyJointRegistry extends PersistentState {
    public static final int MAX_JOINTS=4096,MAX_PER_BODY=64;
    private Map<UUID,BodyJointLink> links=new LinkedHashMap<>();
    private Map<UUID,Long> starts=new HashMap<>();
    private final Map<UUID,BodyBounds> locations=new HashMap<>();
    private Map<UUID,Set<UUID>> endpoints=new HashMap<>();
    public static BodyJointRegistry get(ServerWorld world){return world.getPersistentStateManager().getOrCreate(new Type<>(BodyJointRegistry::new,BodyJointRegistry::fromNbt,null),"drmd_body_joints");}
    public List<BodyJointLink> links(){return links.values().stream().sorted(Comparator.comparing(l->l.id().toString())).toList();}
    public List<BodyJointLink> forBody(UUID body){return endpoints.getOrDefault(body,Set.of()).stream().map(links::get).sorted(Comparator.comparing(l->l.id().toString())).toList();}
    /** Last real complete volume, retained when an endpoint unloads. Numeric data only;
     * it locates loading requests, never creates bodies or assigns a restored pose. */
    public Optional<BodyBounds> lastBounds(UUID body){return Optional.ofNullable(locations.get(body));}
    void rememberLocation(BlockBodyEntity body) {
        if(!containsBody(body.getUuid())||body.isRemoved()||body.cells().isEmpty())return;
        var bounds=body.loadingBounds();if(!bounds.equals(locations.put(body.getUuid(),bounds)))markDirty();
    }
    public boolean containsBody(UUID body){return endpoints.containsKey(body);}
    public boolean active(UUID id,long tick){return links.containsKey(id)&&tick>=starts.getOrDefault(id,Long.MIN_VALUE);}
    public boolean activeBody(UUID body,long tick){return endpoints.getOrDefault(body,Set.of()).stream().anyMatch(id->active(id,tick));}
    public Optional<BodyJointLink> find(UUID id){return Optional.ofNullable(links.get(id));}
    public void add(ServerWorld world,BlockBodyEntity a,BlockBodyEntity b,BodyJointLink link) {
        if(!world.getServer().isOnThread()||a.getWorld()!=world||b.getWorld()!=world||a==b||a.isRemoved()||b.isRemoved()
            ||!link.a().body().equals(a.getUuid())||!link.b().body().equals(b.getUuid())
            ||a.getLocalBlock(link.a().cell()).isAir()||b.getLocalBlock(link.b().cell()).isAir())throw new IllegalArgumentException("live material endpoints in the same world required");
        var loaded=BodySpatialIndex.loaded(world);if(!loaded.contains(a)||!loaded.contains(b))throw new IllegalArgumentException("unspawned NBT clones cannot own live joints");
        if(links.containsKey(link.id())||links.size()>=MAX_JOINTS||forBody(a.getUuid()).size()>=MAX_PER_BODY||forBody(b.getUuid()).size()>=MAX_PER_BODY)throw new IllegalArgumentException("joint admission exceeded");
        var next=new LinkedHashMap<>(links);next.put(link.id(),link);replace(next,Map.of(link.id(),world.getTime()+1));rememberLocation(a);rememberLocation(b);
    }
    public boolean configure(UUID id,double speed,double motorForce,double breakForce,double breakTorque) {
        var old=links.get(id);if(old==null)return false;
        var updated=new BodyJointLink(old.id(),old.a(),old.b(),old.kind(),old.lower(),old.upper(),speed,motorForce,breakForce,breakTorque);
        if(updated.equals(old))return true;var next=new LinkedHashMap<>(links);next.put(id,updated);replace(next);return true;
    }
    public boolean remove(UUID id){if(!links.containsKey(id))return false;var next=new LinkedHashMap<>(links);next.remove(id);replace(next);return true;}
    public void removeBody(UUID body){if(!containsBody(body))return;var next=new LinkedHashMap<>(links);for(var link:forBody(body))next.remove(link.id());replace(next);}
    /** Called only after a successful atomic local edit. Anchors follow their actual cells. */
    public void materialChanged(BlockBodyEntity original,List<BlockBodyEntity> fragments) {
        var affected=forBody(original.getUuid());if(affected.isEmpty())return;
        var next=new LinkedHashMap<>(links);boolean changed=false;
        for(var link:affected) {
            BodyJointLink.Endpoint a=migrate(link.a(),original,fragments),b=migrate(link.b(),original,fragments);
            if(a==null||b==null){next.remove(link.id());changed=true;}
            else if(!a.equals(link.a())||!b.equals(link.b())){next.put(link.id(),new BodyJointLink(link.id(),a,b,link.kind(),link.lower(),link.upper(),link.speed(),link.motorForce(),link.breakForce(),link.breakTorque()));changed=true;}
        }
        if(changed)replace(next);rememberLocation(original);for(var fragment:fragments)rememberLocation(fragment);
    }
    private static BodyJointLink.Endpoint migrate(BodyJointLink.Endpoint endpoint,BlockBodyEntity original,List<BlockBodyEntity> fragments) {
        if(!endpoint.body().equals(original.getUuid())||!original.getLocalBlock(endpoint.cell()).isAir())return endpoint;
        for(var body:fragments)if(!body.getLocalBlock(endpoint.cell()).isAir())return endpoint.on(body.getUuid());
        return null;
    }
    private void replace(Map<UUID,BodyJointLink> next){replace(next,Map.of());}
    private void replace(Map<UUID,BodyJointLink> next,Map<UUID,Long> updates) {
        var times=new HashMap<UUID,Long>();for(var id:next.keySet())times.put(id,updates.getOrDefault(id,starts.getOrDefault(id,Long.MIN_VALUE)));
        var index=new HashMap<UUID,Set<UUID>>();
        for(var link:next.values())for(var body:List.of(link.a().body(),link.b().body()))index.computeIfAbsent(body,k->new HashSet<>()).add(link.id());
        links=next;endpoints=index;starts=times;locations.keySet().retainAll(index.keySet());markDirty();
    }
    public static BodyJointRegistry fromNbt(NbtCompound n,RegistryWrapper.WrapperLookup lookup) {
        if(n.getInt("schema")>2)throw new IllegalArgumentException("unsupported joint registry schema");
        var state=new BodyJointRegistry();var list=n.getList("joints",NbtElement.COMPOUND_TYPE);var records=new LinkedHashMap<UUID,BodyJointLink>();var times=new HashMap<UUID,Long>();
        if(list.size()>MAX_JOINTS)throw new IllegalArgumentException("saved joint admission exceeded");
        for(int i=0;i<list.size();i++) {
            try {var entry=list.getCompound(i);var link=BodyJointLink.read(entry);times.put(link.id(),entry.contains("activateAt")?entry.getLong("activateAt"):Long.MIN_VALUE);if(records.putIfAbsent(link.id(),link)!=null)throw new IllegalArgumentException("duplicate joint identity");}
            catch(IllegalArgumentException invalid){throw new IllegalArgumentException("invalid saved joint at index"+i,invalid);}
        }
        state.replace(records,times);
        var locations=n.getList("locations",NbtElement.COMPOUND_TYPE);
        if(locations.size()>MAX_JOINTS*2)throw new IllegalArgumentException("saved body location admission exceeded");
        for(int i=0;i<locations.size();i++) {
            var entry=locations.getCompound(i);if(!entry.containsUuid("body"))throw new IllegalArgumentException("missing located body identity");
            var id=entry.getUuid("body");if(!state.containsBody(id))throw new IllegalArgumentException("unreferenced located body");
            var bounds=new BodyBounds(readPoint(entry,"min"),readPoint(entry,"max"));
            // Validate the existing plan limits once at load, before any ticket mutation.
            BodyVolumePlan.columns(bounds,-30000000,30000000);
            if(state.locations.putIfAbsent(id,bounds)!=null)throw new IllegalArgumentException("duplicate body location");
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup lookup) {
        n.putInt("schema",2);var list=new NbtList();for(var link:links()){var entry=link.write();entry.putLong("activateAt",starts.getOrDefault(link.id(),Long.MIN_VALUE));list.add(entry);}n.put("joints",list);
        var positions=new NbtList();for(var id:locations.keySet().stream().sorted(Comparator.comparing(UUID::toString)).toList()) {
            var entry=new NbtCompound();entry.putUuid("body",id);var bounds=locations.get(id);writePoint(entry,"min",bounds.min());writePoint(entry,"max",bounds.max());positions.add(entry);
        }
        n.put("locations",positions);return n;
    }
    private static Vec3 readPoint(NbtCompound n,String key) {
        for(String axis:List.of("x","y","z"))if(!n.contains(key+axis,NbtElement.NUMBER_TYPE))throw new IllegalArgumentException("missing body location component");
        return new Vec3(n.getDouble(key+"x"),n.getDouble(key+"y"),n.getDouble(key+"z"));
    }
    private static void writePoint(NbtCompound n,String key,Vec3 p){n.putDouble(key+"x",p.x());n.putDouble(key+"y",p.y());n.putDouble(key+"z",p.z());}

}
