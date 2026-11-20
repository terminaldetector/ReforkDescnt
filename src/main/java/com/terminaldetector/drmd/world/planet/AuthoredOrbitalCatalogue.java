package com.terminaldetector.drmd.world.planet;

import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.fixture.city.CityBlueprint;
import com.terminaldetector.drmd.world.fixture.city.CityManifest;
import com.terminaldetector.drmd.world.fixture.city.OrbitalFieldBlueprint;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Certified static authored objects, never a source of world generation or block edits.
 * A conservative edit invalidates the entire distant proxy; it cannot resurrect itself on reopen. */
public final class AuthoredOrbitalCatalogue extends PersistentState {
    public static final String ID="drmd_authored_orbital";
    public static final int MAX_OBJECTS=16,READS_PER_TICK=64;
    private static final Type<AuthoredOrbitalCatalogue> TYPE=new Type<>(AuthoredOrbitalCatalogue::new,AuthoredOrbitalCatalogue::read,null);
    private static final Map<ServerWorld,Binding> LIVE=new WeakHashMap<>();
    private record Binding(AuthoredOrbitalCatalogue state,Certification pending){}
    public record Entry(UUID id,UUID job,String dimension,long worldSeed,String planHash,OrbitalFieldBlueprint.ObjectSpec object,boolean valid){
        public Entry{
            Objects.requireNonNull(id);Objects.requireNonNull(job);Objects.requireNonNull(object);
            if(dimension==null||dimension.length()>160||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||planHash==null||!planHash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("orbital catalogue context");
            if(!id.equals(objectId(job,object.id())))throw new IllegalArgumentException("orbital object identity");
            object.centre();Math.addExact(object.origin().x(),object.sizeX());Math.addExact(object.origin().y(),object.sizeY());Math.addExact(object.origin().z(),object.sizeZ());
        }
        public Entry invalidated(){return new Entry(id,job,dimension,worldSeed,planHash,object,false);}
    }
    private final List<Entry> entries=new ArrayList<>();
    private NbtCompound opaque;
    public static UUID objectId(UUID job,String name){return UUID.nameUUIDFromBytes((job+":"+name).getBytes(StandardCharsets.UTF_8));}
    public boolean available(){return opaque==null;}
    public List<Entry> entries(){return List.copyOf(entries);}
    private static void main(ServerWorld world){if(!world.getServer().isOnThread())throw new IllegalStateException("orbital catalogue requires server thread");}
    private static Binding binding(ServerWorld world){main(world);return LIVE.computeIfAbsent(world,w->new Binding(w.getPersistentStateManager().get(TYPE,ID),null));}
    public static void reset(){LIVE.clear();}
    /** Read existing save data only; a normal join does not provision a state or worker. */
    public static List<Entry> visible(ServerWorld world){
        var state=binding(world).state();if(state==null||!state.available())return List.of();
        String dimension=world.getRegistryKey().getValue().toString();long seed=world.getSeed();
        return state.entries.stream().filter(e->e.valid()&&e.worldSeed()==seed&&e.dimension().equals(dimension)).toList();
    }
    /** Orbital material stays outside the planetary heightfield after its proxy is invalidated. */
    public static List<OrbitalFieldBlueprint.ObjectSpec> surfaceExclusions(ServerWorld world){
        var binding=binding(world);
        var objects=new ArrayList<OrbitalFieldBlueprint.ObjectSpec>();
        String dimension=world.getRegistryKey().getValue().toString();long seed=world.getSeed();
        if(binding.state()!=null&&binding.state().available())for(var entry:binding.state().entries){
            if(entry.worldSeed()==seed&&entry.dimension().equals(dimension))objects.add(entry.object());
        }
        var pending=binding.pending();
        if(pending!=null&&!pending.done()&&pending.manifest.worldSeed()==seed&&pending.manifest.identity().world().equals(dimension))
            for(var object:pending.objects)if(!objects.contains(object))objects.add(object);
        return List.copyOf(objects);
    }
    /** Called only by an explicitly completed ORBITAL_FIELD job on the server thread. */
    public static Certification begin(ServerWorld world,CityManifest manifest,CityBlueprint.Plan plan){
        main(world);Objects.requireNonNull(manifest);Objects.requireNonNull(plan);
        if(manifest.kind()!=CityManifest.Kind.ORBITAL_FIELD||!manifest.identity().planHash().equals(plan.hash())
            ||!manifest.identity().world().equals(world.getRegistryKey().getValue().toString())||manifest.worldSeed()!=world.getSeed())throw new IllegalArgumentException("orbital certification requires its actual completed field context");
        var old=binding(world);if(old.pending()!=null&&!old.pending().done())throw new IllegalStateException("one pending orbital certification per world");
        var state=old.state();if(state==null)state=world.getPersistentStateManager().getOrCreate(TYPE,ID);
        var candidate=new Certification(state,manifest,plan);LIVE.put(world,new Binding(state,candidate));return candidate;
    }
    /** No block read, tickets or generation. This runs before SurfaceIngest's optional-store return. */
    public static void changed(ServerWorld world,BlockPos position){
        if(!com.terminaldetector.drmd.world.access.CubicAvailability.onWorldThread(world))return;
        var b=binding(world);if(b.pending()!=null)b.pending().changed(position);
        if(b.state()!=null)b.state().invalidateAt(position.getX(),position.getY(),position.getZ());
    }
    public boolean invalidateAt(int x,int y,int z){
        if(!available())return false;boolean changed=false;
        for(int i=0;i<entries.size();i++){var e=entries.get(i);if(e.valid()&&e.object().contains(x,y,z)){entries.set(i,e.invalidated());changed=true;}}
        if(changed)markDirty();return changed;
    }
    private boolean publish(List<Entry> proposed){
        if(!available())return false;
        if(proposed.isEmpty()||proposed.size()>MAX_OBJECTS||entries.size()+proposed.size()>MAX_OBJECTS)return false;
        var ids=new HashSet<UUID>();for(var e:entries)ids.add(e.id());
        for(var e:proposed)if(!e.valid()||!ids.add(e.id()))return false;
        entries.addAll(proposed);markDirty();return true;
    }
    public static final class Certification {
        private final AuthoredOrbitalCatalogue state;private final CityManifest manifest;private final CityBlueprint.Plan plan;
        private final List<OrbitalFieldBlueprint.ObjectSpec> objects;private int cursor;private boolean done;private String detail="Certifying actual loaded field cells64/tick";private long lastTick=Long.MIN_VALUE;
        private Certification(AuthoredOrbitalCatalogue state,CityManifest manifest,CityBlueprint.Plan plan){
            this.state=state;this.manifest=manifest;this.plan=plan;objects=OrbitalFieldBlueprint.objects(manifest.anchor());
            if(!state.available()){done=true;detail="Unsupported/corrupt catalogue retained; no proxy publication";}
            else if(state.entries.stream().anyMatch(e->e.job().equals(manifest.identity().jobId()))){done=true;detail="Original job already catalogued; damage invalidation is retained, no implicit republish";}
            else if(state.entries.size()+objects.size()>MAX_OBJECTS){done=true;detail="Orbital catalogue cap16; no proxy publication";}
        }
        public boolean done(){return done;}public int cursor(){return cursor;}public String detail(){return detail;}
        private void changed(BlockPos p){if(!done&&objects.stream().anyMatch(o->o.contains(p.getX(),p.getY(),p.getZ())))cancel("Field changed during loaded certification; no pristine proxy published");}
        public void cancel(String reason){if(!done){done=true;detail=reason;}}
        public void tick(ServerWorld world){
            main(world);if(done||lastTick==world.getServer().getTicks())return;lastTick=world.getServer().getTicks();
            if(!manifest.identity().world().equals(world.getRegistryKey().getValue().toString())||manifest.worldSeed()!=world.getSeed()){cancel("Certification world context changed");return;}
            var access=WorldAccess.of(world);int end=Math.min(plan.cells().size(),cursor+READS_PER_TICK);
            while(cursor<end){var cell=plan.cells().get(cursor);var p=cell.position();var actual=access.readIfLoaded(new BlockPos(p.x(),p.y(),p.z()));
                if(actual.isEmpty()||actual.get().hasBlockEntity()||!actual.get().equals(actual.get().getBlock().getDefaultState())||!Registries.BLOCK.getId(actual.get().getBlock()).toString().equals(cell.state())){cancel("Actual cell changed/unavailable at "+p+"; no distant proxy");return;}cursor++;
            }
            if(cursor==plan.cells().size()){
                var proposed=objects.stream().map(o->new Entry(objectId(manifest.identity().jobId(),o.id()),manifest.identity().jobId(),manifest.identity().world(),manifest.worldSeed(),manifest.identity().planHash(),o,true)).toList();
                done=true;detail=state.publish(proposed)?"Certified actual station and2asteroids; distant proxies follow these same voxels":"Catalogue unavailable/capacity/duplicate; no publication";
            }
        }
    }
    private static void keys(NbtCompound n,String... names){if(!n.getKeys().equals(Set.of(names)))throw new IllegalArgumentException("unknown/missing orbital fields");}
    private static void type(NbtCompound n,String name,int type){if(!n.contains(name,type))throw new IllegalArgumentException("wrong orbital field type");}
    public static AuthoredOrbitalCatalogue read(NbtCompound raw,RegistryWrapper.WrapperLookup ignored){
        var out=new AuthoredOrbitalCatalogue();
        try{
            keys(raw,"schema","geometry","objects");type(raw,"schema",NbtElement.INT_TYPE);type(raw,"geometry",NbtElement.INT_TYPE);type(raw,"objects",NbtElement.LIST_TYPE);
            if(raw.getInt("schema")!=1||raw.getInt("geometry")!=OrbitalObjectGeometry.VERSION)throw new IllegalArgumentException("future orbital geometry/schema");
            var list=(NbtList)raw.get("objects");if(list.size()>MAX_OBJECTS)throw new IllegalArgumentException("orbital object cap");var ids=new HashSet<UUID>();
            for(var value:list){
                if(!(value instanceof NbtCompound n))throw new IllegalArgumentException("orbital compound required");
                keys(n,"id","job","dimension","seed","hash","name","shape","x","y","z","sx","sy","sz","valid");
                if(!n.containsUuid("id")||!n.containsUuid("job"))throw new IllegalArgumentException("orbital UUID required");
                for(var k:List.of("dimension","hash","name","shape"))type(n,k,NbtElement.STRING_TYPE);
                for(var k:List.of("x","y","z","sx","sy","sz"))type(n,k,NbtElement.INT_TYPE);type(n,"seed",NbtElement.LONG_TYPE);type(n,"valid",NbtElement.BYTE_TYPE);
                if(n.getByte("valid")!=0&&n.getByte("valid")!=1)throw new IllegalArgumentException("orbital boolean required");
                var object=new OrbitalFieldBlueprint.ObjectSpec(n.getString("name"),OrbitalObjectGeometry.Shape.valueOf(n.getString("shape")),new CityBlueprint.Point(n.getInt("x"),n.getInt("y"),n.getInt("z")),n.getInt("sx"),n.getInt("sy"),n.getInt("sz"));
                var e=new Entry(n.getUuid("id"),n.getUuid("job"),n.getString("dimension"),n.getLong("seed"),n.getString("hash"),object,n.getBoolean("valid"));if(!ids.add(e.id()))throw new IllegalArgumentException("duplicate orbital id");out.entries.add(e);
            }
            return out;
        }catch(RuntimeException rejected){var quarantined=new AuthoredOrbitalCatalogue();quarantined.opaque=raw.copy();return quarantined;}
    }
    @Override public NbtCompound writeNbt(NbtCompound out,RegistryWrapper.WrapperLookup ignored){
        if(opaque!=null)return opaque.copy();out.putInt("schema",1);out.putInt("geometry",OrbitalObjectGeometry.VERSION);var list=new NbtList();
        for(var e:entries){var o=e.object();var p=o.origin();var n=new NbtCompound();n.putUuid("id",e.id());n.putUuid("job",e.job());n.putString("dimension",e.dimension());n.putLong("seed",e.worldSeed());n.putString("hash",e.planHash());n.putString("name",o.id());n.putString("shape",o.shape().name());n.putInt("x",p.x());n.putInt("y",p.y());n.putInt("z",p.z());n.putInt("sx",o.sizeX());n.putInt("sy",o.sizeY());n.putInt("sz",o.sizeZ());n.putBoolean("valid",e.valid());list.add(n);}out.put("objects",list);return out;
    }
}
