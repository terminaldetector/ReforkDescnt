package com.terminaldetector.drmd.world.fixture.city;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Explicit-route-only bounded save state. No startup discovery, world edits, rewards or resets. */
public final class OrbitalRouteMemory extends PersistentState {
    public static final String ID="drmd_orbital_routes";
    public static final int MAX_OWNERS=64;
    public record Context(UUID owner,UUID save,UUID job,String dimension,long worldSeed,String planHash,CityBlueprint.Point anchor){
        public Context{
            Objects.requireNonNull(owner);Objects.requireNonNull(save);Objects.requireNonNull(job);Objects.requireNonNull(anchor);
            if(dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||dimension.length()>160||planHash==null||!planHash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("invalid route context");
        }
        public static Context of(CityManifest manifest){var id=manifest.identity();return new Context(id.owner(),id.saveId(),id.jobId(),id.world(),manifest.worldSeed(),id.planHash(),manifest.anchor());}
    }
    private record Run(Context context,OrbitalRouteProgress.State state){}
    private final Map<UUID,Run> runs=new HashMap<>();
    private NbtCompound opaque;
    public static OrbitalRouteMemory of(ServerWorld world){
        if(!world.getServer().isOnThread())throw new IllegalStateException("route authority requires server thread");
        return world.getPersistentStateManager().getOrCreate(new Type<>(OrbitalRouteMemory::new,OrbitalRouteMemory::read,null),ID);
    }
    public boolean available(){return opaque==null;}
    public int size(){return runs.size();}
    public OrbitalRouteProgress progress(Context context,List<OrbitalRouteBlueprint.Gate> gates){
        if(!available())throw new IllegalStateException("unsupported/corrupt saved route data retained; no progress authority");
        var run=runs.get(context.owner());
        if(run!=null&&!run.context().equals(context))throw new IllegalArgumentException("saved route owner/save/job/world/definition changed; no implicit reset");
        return new OrbitalRouteProgress(gates,run==null?OrbitalRouteProgress.State.empty():run.state());
    }
    public OrbitalRouteProgress.Snapshot observe(Context context,List<OrbitalRouteBlueprint.Gate> gates,OrbitalRouteProgress.Position position,long tick,boolean loadedClearance){
        var progress=progress(context,gates);var before=progress.state();var result=progress.observe(position,tick,loadedClearance);
        if(!progress.state().equals(before)){
            if(!runs.containsKey(context.owner())&&runs.size()>=MAX_OWNERS)throw new IllegalStateException("saved route owner cap64");
            runs.put(context.owner(),new Run(context,progress.state()));markDirty();
        }
        return result;
    }
    private static void keys(NbtCompound n,String... names){if(!n.getKeys().equals(Set.of(names)))throw new IllegalArgumentException("unknown/missing saved route fields");}
    private static void type(NbtCompound n,String key,int expected){if(!n.contains(key,expected))throw new IllegalArgumentException("wrong saved route field type");}
    public static OrbitalRouteMemory read(NbtCompound raw,RegistryWrapper.WrapperLookup ignored){
        var out=new OrbitalRouteMemory();
        try{
            keys(raw,"schema","runs");type(raw,"schema",NbtElement.INT_TYPE);type(raw,"runs",NbtElement.LIST_TYPE);
            if(raw.getInt("schema")!=1)throw new IllegalArgumentException("future saved route schema");
            var values=(NbtList)raw.get("runs");if(values.size()>MAX_OWNERS)throw new IllegalArgumentException("route owner cap");
            var owners=new HashSet<UUID>();
            for(var value:values){
                if(!(value instanceof NbtCompound n))throw new IllegalArgumentException("route compound required");
                keys(n,"owner","save","job","dimension","worldSeed","hash","x","y","z","passed","started","last","finished");
                for(String key:List.of("owner","save","job"))if(!n.containsUuid(key))throw new IllegalArgumentException("route UUID required");
                for(String key:List.of("dimension","hash"))type(n,key,NbtElement.STRING_TYPE);
                for(String key:List.of("x","y","z","passed"))type(n,key,NbtElement.INT_TYPE);
                for(String key:List.of("worldSeed","started","last","finished"))type(n,key,NbtElement.LONG_TYPE);
                var context=new Context(n.getUuid("owner"),n.getUuid("save"),n.getUuid("job"),n.getString("dimension"),n.getLong("worldSeed"),n.getString("hash"),new CityBlueprint.Point(n.getInt("x"),n.getInt("y"),n.getInt("z")));
                var state=new OrbitalRouteProgress.State(n.getInt("passed"),n.getLong("started"),n.getLong("last"),n.getLong("finished"));
                new OrbitalRouteProgress(OrbitalRouteBlueprint.gates(context.anchor()),state);
                if(!owners.add(context.owner()))throw new IllegalArgumentException("duplicate route owner");
                out.runs.put(context.owner(),new Run(context,state));
            }
            return out;
        }catch(RuntimeException rejected){var quarantined=new OrbitalRouteMemory();quarantined.opaque=raw.copy();return quarantined;}
    }
    @Override public NbtCompound writeNbt(NbtCompound out,RegistryWrapper.WrapperLookup ignored){
        if(opaque!=null)return opaque.copy();out.putInt("schema",1);var list=new NbtList();
        runs.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
            var run=entry.getValue();var c=run.context();var s=run.state();var n=new NbtCompound();
            n.putUuid("owner",c.owner());n.putUuid("save",c.save());n.putUuid("job",c.job());n.putString("dimension",c.dimension());n.putLong("worldSeed",c.worldSeed());n.putString("hash",c.planHash());
            n.putInt("x",c.anchor().x());n.putInt("y",c.anchor().y());n.putInt("z",c.anchor().z());n.putInt("passed",s.passed());n.putLong("started",s.started());n.putLong("last",s.last());n.putLong("finished",s.finished());list.add(n);
        });out.put("runs",list);return out;
    }
}
