package com.terminaldetector.drmd.world.enclave;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Server data only: no world references, mission instances, spawning or rewards. */
public final class EnclaveMissionCatalog {
    public static final int MAX_DEFINITIONS=32,MAX_CANDIDATE_VISITS=256;
    public record Snapshot(Map<String,EnclaveMissionContract> definitions,String problem){
        public Snapshot{
            definitions=Map.copyOf(definitions);Objects.requireNonNull(problem);
            if(definitions.size()>MAX_DEFINITIONS||(!problem.isEmpty()&&!definitions.isEmpty()))throw new IllegalArgumentException("invalid mission catalog snapshot");
        }
        public boolean valid(){return problem.isEmpty();}
        public Optional<EnclaveMissionContract> find(String id){return Optional.ofNullable(definitions.get(id));}
    }
    private static final AtomicReference<Snapshot> CURRENT=new AtomicReference<>(new Snapshot(Map.of(),"Mission data has not loaded"));
    private static boolean registered;
    private EnclaveMissionCatalog(){}
    public static Snapshot snapshot(){return CURRENT.get();}
    public static Optional<EnclaveMissionContract> find(String id){return CURRENT.get().find(id);}
    public static synchronized void register(){
        if(registered)return;
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener(){
            @Override public Identifier getFabricId(){return Identifier.of("drmd","enclave_missions");}
            @Override public void reload(ResourceManager resources){CURRENT.set(read(resources));}
        });
        registered=true;
    }
    /** Entire reload fails closed; old active bindings cannot fall back to stale definitions. */
    static Snapshot read(ResourceManager resources){
        try{
            var ids=new TreeSet<Identifier>(Comparator.comparing(Identifier::toString));int[] visits={0};
            // Enumerate callback results, rather than materializing an unbounded
            // findResources map. Highest-priority override is resolved below.
            try(var packs=resources.streamResourcePacks()){
                var iterator=packs.iterator();int packCount=0;
                while(iterator.hasNext()){
                    if(++packCount>MAX_CANDIDATE_VISITS)throw new IllegalArgumentException("mission resource pack work cap256");
                    var pack=iterator.next();
                    if(!pack.getNamespaces(ResourceType.SERVER_DATA).contains("drmd"))continue;
                    pack.findResources(ResourceType.SERVER_DATA,"drmd","missions",(id,unusedSupplier)->{
                        if(++visits[0]>MAX_CANDIDATE_VISITS)throw new IllegalArgumentException("mission resource candidate cap256");
                        if(!id.getNamespace().equals("drmd")||!id.getPath().startsWith("missions/")||!id.getPath().endsWith(".json"))return;
                        ids.add(id);if(ids.size()>MAX_DEFINITIONS)throw new IllegalArgumentException("mission definition cap32");
                    });
                }
            }
            var definitions=new LinkedHashMap<String,EnclaveMissionContract>();
            for(var id:ids){
                var resource=resources.getResource(id).orElseThrow(()->new IOException("mission resource disappeared: "+id));
                final byte[] bytes;
                try(var stream=resource.getInputStream()){bytes=stream.readNBytes(EnclaveMissionContract.MAX_BYTES+1);}
                var contract=EnclaveMissionContract.parse(bytes);
                var relative=id.getPath().substring("missions/".length(),id.getPath().length()-".json".length());
                if(!contract.id().equals("drmd:"+relative))throw new IllegalArgumentException("mission ID does not match resource path: "+id);
                if(definitions.putIfAbsent(contract.id(),contract)!=null)throw new IllegalArgumentException("duplicate mission ID");
            }
            return definitions.isEmpty()?new Snapshot(Map.of(),"No playable authored mission definitions"):new Snapshot(definitions,"");
        }catch(IOException|RuntimeException refused){
            var reason=Objects.toString(refused.getMessage(),refused.getClass().getSimpleName());
            return new Snapshot(Map.of(),"Mission data unavailable: "+reason.substring(0,Math.min(240,reason.length())));
        }
    }
}
