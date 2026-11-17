package com.terminaldetector.drmd.gate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.physics.PhysicsProfile;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.MinecraftServer;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Opt-in observation only. Never moves actors, modifies a world or certifies performance. */
public final class NativeBenchmarkRecorder {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.benchmark");
    private static final Object LOCK=new Object();
    private static final Gson JSON=new GsonBuilder().serializeNulls().create();
    private static final String[] FIELDS={"seed","coords","dimension","flight_state","runtime_source_sha",
        "runtime_game_sha256","java_version","fabric_loader_version","sodium_version","cubic_backend",
        "ps1_profile","hybrid_on","expanded_physics","view_distance","sim_distance","gpu_vendor",
        "gpu_renderer","gpu_version","gpu_hardware","heap_max_bytes","requested_entities","active_entities",
        "moving_entities","rendered_entities","ragdolls","rigid_bodies","cubes_loaded","mesh_queue","draw_calls"};
    private enum Phase { WAITING,WARMING,STARTING,SAMPLING,CLOSED }
    private static Config config;
    private static Phase phase=Phase.WAITING;
    private static volatile boolean registered;
    private static boolean truncated,invalidClock,invalidHeap;
    private static long warmStart,startNano,lastFrame;
    private static Instant startUtc;
    private static double actualWarmup;
    private static long[] frameNanos,tickNanos,heapBytes;
    private static int frameCount,tickCount,heapCount;
    private static MinecraftServer observedServer;
    private static ClientWorld observedWorld;
    private static Options startOptions;
    private static Map<String,Object> context=missingContext();

    private NativeBenchmarkRecorder(){}

    private record Config(String run,String scenario,boolean sidecar,long warmupNanos,long sampleNanos,
                          int capacity,Path output) {}
    private record Options(Object presentation,boolean hybrid,Object physics,int view,int simulation) {}
    /** The arrays are transferred, not copied: no producer writes after phase becomes CLOSED. */
    private record Capture(Config config,Map<String,Object> context,Instant started,Instant ended,double warmup,
                           long[] frames,int frameCount,long[] ticks,int tickCount,long[] heap,int heapCount,
                           String status,boolean truncated,boolean invalidClock,boolean invalidHeap) {}

    private static void require(boolean value,String reason){if(!value)throw new IllegalArgumentException("BENCHMARK_RECORDER "+reason);}
    private static String property(String name,String fallback){return System.getProperty("drmd.benchmark."+name,fallback).trim();}
    private static long seconds(String name,String fallback,boolean zeroAllowed){
        double seconds=Double.parseDouble(property(name,fallback));
        require(Double.isFinite(seconds)&&seconds>=0&&seconds<=600&&(zeroAllowed||seconds>0),name+" must be finite within "+(zeroAllowed?"[0,600]":"(0,600]")+" seconds");
        long nanos=Math.round(seconds*1_000_000_000d);require(zeroAllowed||nanos>0,name+" rounds to zero nanoseconds");return nanos;
    }
    private static Config readConfig(){
        String run=property("runId",""),scenario=property("scenario",""),mode=property("mode","standalone");
        require(run.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}")&&!run.startsWith("REPLACE_"),"provide an actual safe runId");
        require(!scenario.isBlank()&&scenario.length()<=200&&!scenario.startsWith("REPLACE_")&&scenario.chars().noneMatch(Character::isISOControl),"provide an actual scenario label");
        require(mode.equals("standalone")||mode.equals("sidecar"),"mode must be standalone or sidecar");
        int capacity=Integer.parseInt(property("maxSamples","65536"));
        // Three long arrays <=6 MiB; <=40 encoded bytes/value keeps JSON below 32 MiB plus bounded metadata.
        require(capacity>=16&&capacity<=262144,"maxSamples must be within [16,262144]");
        String file=property("output","benchmark-"+run+".json");
        require(file.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}\\.json"),"output must be a fresh JSON basename");
        Path output=FabricLoader.getInstance().getGameDir().resolve("benchmark-observations").resolve(file);
        require(!Files.exists(output),"output already exists: "+output);
        return new Config(run,scenario,mode.equals("sidecar"),seconds("warmupSeconds","5",true),seconds("sampleSeconds","30",false),capacity,output);
    }

    /** Called only by the owned opt-in bootstrap probe, before the existing gate bootstrap. */
    public static void register(){
        if(!ENABLED||registered)return;
        Config next=readConfig(); // property validation and filesystem reads stay outside LOCK
        long[] frames=new long[next.capacity()],ticks=new long[next.capacity()],heap=new long[next.capacity()];
        synchronized(LOCK){
            if(registered)return;
            config=next;frameNanos=frames;tickNanos=ticks;heapBytes=heap;registered=true;
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->close("partial_client_stopping"));
        System.out.println("BENCHMARK_RECORDER armed run="+next.run()+" mode="+(next.sidecar()?"sidecar":"standalone")+" output="+next.output());
    }
    public static boolean standalone(){return registered&&!config.sidecar();}

    private static Map<String,Object> item(Object value,String reason){
        var result=new LinkedHashMap<String,Object>();result.put("value",value);result.put("reason",reason);return result;
    }
    private static Map<String,Object> missingContext(){
        var result=new LinkedHashMap<String,Object>();
        for(String field:FIELDS)result.put(field,item(null,"No ready-world observation was captured"));
        return result;
    }
    private static void observed(Map<String,Object> values,String name,Object value,String missing){
        if(value instanceof String text&&(text.isBlank()||text.length()>4096)){
            value=null;missing="Observed text is empty or exceeds the bounded 4096-character field size";
        }
        values.put(name,item(value,value==null?missing:null));
    }
    private static String version(String id){return FabricLoader.getInstance().getModContainer(id).map(m->m.getMetadata().getVersion().getFriendlyString()).orElse(null);}
    private static Options options(MinecraftClient client){
        return new Options(DescentConfig.presentationProfile,DescentConfig.hybridRender,DescentClientState.physicsProfile,
            client.options.getViewDistance().getValue(),client.options.getSimulationDistance().getValue());
    }
    /** Runs on the render thread, outside the sampling lock. No server/world edits or GL timer queries. */
    private static Map<String,Object> captureContext(MinecraftClient client){
        var result=missingContext();
        double x=client.player.getX(),y=client.player.getY(),z=client.player.getZ();
        observed(result,"coords",Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)?List.of(x,y,z):null,"Client coordinates are non-finite");
        observed(result,"dimension",client.world.getRegistryKey().getValue().toString(),"No client dimension");
        observed(result,"flight_state",DescentClientState.enabled?"descent_6dof":DescentClientState.footGravity?"local_gravity_enabled":"descent_disabled","No flight mirror");
        observed(result,"java_version",System.getProperty("java.version"),"java.version property unavailable");
        observed(result,"fabric_loader_version",version("fabricloader"),"No loaded Fabric Loader metadata");
        observed(result,"sodium_version",version("sodium"),"Sodium not present in the loaded mod registry");
        observed(result,"cubic_backend",CubicAvailability.backend(client.world).name(),"No client backend observation");
        String presentation=switch(DescentConfig.presentationProfile){case HYBRID_NATIVE->"native";case PS1_HYBRID->"ps1";case RENDER_DEBUG->"debug";};
        observed(result,"ps1_profile",presentation,"No presentation profile");
        observed(result,"hybrid_on",DescentConfig.hybridRender,"No hybrid setting");
        observed(result,"expanded_physics",DescentClientState.physicsProfile==null?null:DescentClientState.physicsProfile==PhysicsProfile.EXPANDED,"Server physics profile has not synchronized to the client");
        observed(result,"view_distance",client.options.getViewDistance().getValue(),"No client view-distance option");
        observed(result,"sim_distance",client.options.getSimulationDistance().getValue(),"No client simulation-distance option");
        String vendor=GL11.glGetString(GL11.GL_VENDOR),renderer=GL11.glGetString(GL11.GL_RENDERER),glVersion=GL11.glGetString(GL11.GL_VERSION);
        observed(result,"gpu_vendor",vendor,"GL_VENDOR unavailable in current context");
        observed(result,"gpu_renderer",renderer,"GL_RENDERER unavailable in current context");
        observed(result,"gpu_version",glVersion,"GL_VERSION unavailable in current context");
        String lower=renderer==null?"":renderer.toLowerCase(Locale.ROOT);
        boolean software=lower.contains("llvmpipe")||lower.contains("softpipe")||lower.contains("swiftshader")||lower.contains("software rasterizer");
        observed(result,"gpu_hardware",software?"software":"unknown","Physical GPU hardware is not attested");
        long maximum=Runtime.getRuntime().maxMemory();
        observed(result,"heap_max_bytes",maximum>0?maximum:null,"JVM maximum heap unavailable");
        result.put("seed",item(null,"No authoritative server-world seed observation is attached"));
        result.put("runtime_source_sha",item(null,"Running classes do not attest an immutable Git source; bind a build stamp with the collector"));
        result.put("runtime_game_sha256",item(null,"This observer does not hash the loaded game-code artifact"));
        for(String name:List.of("requested_entities","active_entities","moving_entities","rendered_entities","ragdolls","rigid_bodies"))
            result.put(name,item(null,"No scenario-cohort counter is attached; labels and staged entities are not counts"));
        result.put("cubes_loaded",item(null,"No authoritative backend loaded-cube counter is attached"));
        result.put("mesh_queue",item(null,"No mesh-queue counter is attached"));
        result.put("draw_calls",item(null,"No per-frame draw-call counter is attached"));
        return result;
    }

    /** Top-level completed client frames only; recursive GameRenderer/world-view passes never call this. */
    public static void frameFinished(MinecraftClient client,long now){
        if(!ENABLED||!registered)return;
        boolean ready=client.world!=null&&client.player!=null&&client.currentScreen==null&&!client.isPaused();
        boolean begin=false;
        synchronized(LOCK){
            if(phase==Phase.CLOSED||phase==Phase.STARTING)return;
            if(phase==Phase.WAITING||phase==Phase.WARMING){
                if(!ready){phase=Phase.WAITING;return;}
                if(phase==Phase.WAITING){phase=Phase.WARMING;warmStart=now;}
                if(now-warmStart>=config.warmupNanos()){phase=Phase.STARTING;begin=true;}
                else return;
            }
        }
        if(begin){
            Instant utc=Instant.now();
            var captured=captureContext(client);var capturedOptions=options(client);
            synchronized(LOCK){
                if(phase!=Phase.STARTING)return;
                context=captured;startOptions=capturedOptions;observedWorld=client.world;observedServer=client.getServer();
                startNano=lastFrame=now;startUtc=utc;actualWarmup=(now-warmStart)/1e9;phase=Phase.SAMPLING;
            }
            return; // first baseline, not an invented frame interval
        }
        Runtime runtime=Runtime.getRuntime();long used=runtime.totalMemory()-runtime.freeMemory(),maximum=runtime.maxMemory();
        String status=null;Options currentOptions=options(client);
        synchronized(LOCK){
            if(phase!=Phase.SAMPLING)return;
            long elapsed=now-lastFrame;
            if(elapsed<=0)invalidClock=true;
            else if(frameCount<config.capacity())frameNanos[frameCount++]=elapsed;
            else truncated=true;
            lastFrame=now;
            if(used<0||used>maximum)invalidHeap=true;
            else if(heapCount<config.capacity())heapBytes[heapCount++]=used;
            else truncated=true;
            if(frameCount==config.capacity()||heapCount==config.capacity())truncated=true;
            if(truncated)status="truncated_sample_capacity";
            else if(invalidClock)status="partial_invalid_clock";
            else if(invalidHeap)status="partial_inconsistent_heap";
            else if(!ready)status="partial_readiness_lost";
            else if(client.world!=observedWorld)status="partial_world_changed";
            else if(!currentOptions.equals(startOptions))status="partial_options_changed";
            else if(now-startNano>=config.sampleNanos())status="target_window_complete";
        }
        if(status!=null)close(status);
    }

    /** Receives an actual enclosing MinecraftServer.tick span, not target TPS or callback counts. */
    public static void serverTickFinished(MinecraftServer server,long started,long ended){
        if(!ENABLED||!registered)return;
        synchronized(LOCK){
            if(phase!=Phase.SAMPLING||server!=observedServer||started-startNano<0)return;
            long elapsed=ended-started;
            if(elapsed<=0){invalidClock=true;return;}
            if(tickCount<config.capacity()){
                tickNanos[tickCount++]=elapsed;
                if(tickCount==config.capacity())truncated=true;
            }
            else truncated=true;
        }
    }

    private static void close(String status){
        Capture capture;
        synchronized(LOCK){
            if(!registered||phase==Phase.CLOSED)return;
            boolean measured=phase==Phase.SAMPLING;
            // Freeze at actual closure, after the final heap read and every accepted server span.
            long now=System.nanoTime();Instant utc=Instant.now();
            phase=Phase.CLOSED;
            if(measured&&now-startNano<=0)invalidClock=true;
            capture=new Capture(config,context,measured?startUtc:null,utc,actualWarmup,
                frameNanos,frameCount,tickNanos,tickCount,heapBytes,heapCount,status,truncated,invalidClock,invalidHeap);
        }
        // No I/O, GL access, large array copy or JSON serialization under LOCK.
        write(capture);
    }

    private static void rawSeries(JsonWriter writer,String name,String unit,long[] values,int count,double divisor,String reason)throws IOException{
        writer.name(name).beginObject().name("values");
        if(count==0)writer.nullValue();
        else {writer.beginArray();for(int i=0;i<count;i++){if(divisor==1)writer.value(values[i]);else writer.value(values[i]/divisor);}writer.endArray();}
        writer.name("unit").value(unit).name("reason");if(count==0)writer.value(reason);else writer.nullValue();writer.endObject();
    }
    private static void unavailableSeries(JsonWriter writer,String name,String unit,String reason)throws IOException{
        rawSeries(writer,name,unit,null,0,1,reason);
    }
    private static void write(Capture capture){
        try {
            require(capture.started()==null||capture.ended().isAfter(capture.started()),"UTC window is non-increasing; refusing to invent an adjusted timestamp");
            Files.createDirectories(capture.config().output().getParent());
            try(var stream=Files.newBufferedWriter(capture.config().output(),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
                var writer=new JsonWriter(stream)){
                writer.setSerializeNulls(true);writer.setIndent("  ");writer.beginObject();
                writer.name("schema").value("ungravity.benchmark-observations.v1");
                writer.name("run_id").value(capture.config().run());writer.name("scenario").value(capture.config().scenario());
                Object window=capture.started()==null?item(null,"Capture ended before an eligible post-warmup sampling window"):
                    item(Map.of("started_at",capture.started().toString(),"ended_at",capture.ended().toString(),"warmup_seconds",capture.warmup()),null);
                writer.name("window");JSON.toJson(window,Map.class,writer);
                writer.name("context");JSON.toJson(capture.context(),Map.class,writer);
                writer.name("samples").beginObject();
                rawSeries(writer,"frame_time_ms","ms",capture.frames(),capture.frameCount(),1e6,"No complete consecutive top-level frame intervals observed");
                rawSeries(writer,"server_tick_ms","ms",capture.ticks(),capture.tickCount(),1e6,"No integrated server ticks fully contained after warmup");
                rawSeries(writer,"heap_used_bytes","bytes",capture.heap(),capture.heapCount(),1,"No consistent JVM heap sample observed");
                unavailableSeries(writer,"gpu_frame_ms","ms","No actual GPU timer query is attached; CPU frame times are not GPU durations");
                unavailableSeries(writer,"cubes_loaded","count","No authoritative backend loaded-cube counter is attached");
                unavailableSeries(writer,"draw_calls","count","No per-frame draw-call counter is attached");
                unavailableSeries(writer,"mesh_queue","count","No mesh-queue counter is attached");
                writer.endObject().name("known_issues").beginArray();
                writer.value("capture_status="+capture.status());
                writer.value("requested_sample_seconds="+capture.config().sampleNanos()/1e9+"; max_samples_per_series="+capture.config().capacity());
                writer.value("Frame samples are top-level render-return intervals including intervening idle/vsync; server samples enclose MinecraftServer.tick, excluding server-loop sleep");
                writer.value("Context is a start snapshot; distance fields are client options, not effective authoritative cube/tick counts");
                writer.value("Scenario labels do not verify actor cohorts, route readiness, full window coverage or benchmark acceptance");
                if(capture.truncated())writer.value("Sample capacity reached; later spans may be absent before the next completed frame closes capture");
                if(capture.invalidClock())writer.value("Non-positive monotonic span observed and omitted; capture requires investigation");
                if(capture.invalidHeap())writer.value("An inconsistent JVM heap snapshot was omitted; capture requires investigation");
                writer.endArray().endObject();
            }
            System.out.println("BENCHMARK_RECORDER wrote status="+capture.status()+" frames="+capture.frameCount()+" ticks="+capture.tickCount()+" output="+capture.config().output());
        }catch(IOException|RuntimeException failure){
            // Keep any failed/truncated output for diagnosis; never overwrite it with a success marker.
            System.err.println("BENCHMARK_RECORDER FAIL "+failure);failure.printStackTrace(System.err);
        }
    }
}
