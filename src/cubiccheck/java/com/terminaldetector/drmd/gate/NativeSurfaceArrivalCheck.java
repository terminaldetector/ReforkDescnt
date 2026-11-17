package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.planet.PlanetClientState;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.store.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** No block/cache/metadata edits: actual late native arrival into a genuine saved black observation. */
public final class NativeSurfaceArrivalCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.surfaceArrival");
    private static final BlockPos HIGH=new BlockPos(8,2300,8),LOW=new BlockPos(8,1024,8);
    private static final D6ChunkPos HIGH_CUBE=D6ChunkPos.ofBlock(8,2300,8),LOW_CUBE=D6ChunkPos.ofBlock(8,1024,8);
    private static final long KEY=SectionKey.of(0,0,0);
    private static final Cell BLACK=new Cell(2301,0),GOLD=new Cell(2301,16445005);
    private static final List<Map<String,Object>> TIMELINE=Collections.synchronizedList(new ArrayList<>());
    private static final List<Map<String,Object>> PACKETS=Collections.synchronizedList(new ArrayList<>());
    private static final AtomicLong EVENT_SEQUENCE=new AtomicLong();
    private static final AtomicInteger DROPPED=new AtomicInteger(),MUTATIONS=new AtomicInteger();
    private static final ThreadLocal<Integer> NATIVE_DEPTH=ThreadLocal.withInitial(()->0);
    private static final ArrayDeque<Map<String,Object>> INGEST=new ArrayDeque<>();
    private static volatile ServerPlayerEntity player;
    private static volatile Phase phase=Phase.BOOT;
    private static volatile Packet packet=new Packet(0,new Cell(SurfaceSection.NO_HEIGHT,0),"");
    private static volatile ClientSample clientSample;
    private static volatile boolean modePrepared,done;
    private static volatile String captureName,capturedName;
    private static volatile Path runtime;
    private static volatile Map<String,Object> outcome;
    private static volatile String wrongThread;
    private static int ticks,age,frames,unavailableTicks,packetSequence,baselinePacket,baselineMutations;
    private static int firstServerFullTick=-1,firstBothFullTick=-1;
    private static boolean requested,lowRequested;
    private static Cell lastDisk;
    private enum Phase { BOOT,LOW,ASCEND,OBSERVE,CLOSE }
    private record Cell(int height,int colour) {}
    private record Packet(int sequence,Cell cell,String payloadSha256) {}
    private record ClientSample(double y,boolean highFull,boolean lowFull,Cell cell,Packet packet) {}

    private NativeSurfaceArrivalCheck() {}
    public static void register() {
        if(!ENABLED)return;
        runtime=MinecraftClient.getInstance().runDirectory.toPath();
        if(!runtime.getFileName().toString().equals("cubic-surface-arrival-automation"))throw new IllegalStateException("dedicated disposable arrival runtime required");
        ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->{
            if(target(world,chunk))event("fabric-parent-load",world,Map.of());
        });
        ClientTickEvents.END_CLIENT_TICK.register(NativeSurfaceArrivalCheck::tick);
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(player!=null&&!done&&phase!=Phase.BOOT&&phase!=Phase.CLOSE)pump();
        });
    }
    private static boolean target(ServerWorld world,WorldChunk chunk) {
        return ENABLED&&!done&&world.getRegistryKey()==World.OVERWORLD&&chunk.getPos().x==0&&chunk.getPos().z==0;
    }
    private static Map<String,Object> metadata(ServerWorld world) {
        var row=new LinkedHashMap<String,Object>();
        row.put("serverTick",world.getServer().getTicks());row.put("thread",Thread.currentThread().getName());
        boolean owner=CubicAvailability.onWorldThread(world);row.put("ownerThread",owner);
        if(owner) {
            var access=WorldAccess.of(world);var column=CubicAvailability.loadedColumn(world,0,0);
            row.put("highFull",access.isVolumeAvailable(HIGH_CUBE));row.put("lowFull",access.isVolumeAvailable(LOW_CUBE));
            row.put("parentIdentity",column==null?0:System.identityHashCode(column));
            row.put("nativeSampleAtHigh",column==null?null:column.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,8,8));
        }
        return row;
    }
    private static void append(Map<String,Object> row) {
        synchronized(TIMELINE) {
            if(TIMELINE.size()>=4096){DROPPED.incrementAndGet();return;}
            TIMELINE.add(row);
        }
    }
    private static void event(String kind,ServerWorld world,Map<String,Object> extra) {
        var row=metadata(world);row.put("kind",kind);row.put("sequence",EVENT_SEQUENCE.incrementAndGet());
        row.put("nanoTime",System.nanoTime());row.putAll(extra);append(row);
    }
    /** Observes the existing optional public callback only; never asks the backend for a cube. */
    public static void nativeApply(Chunk chunk,int cubeY,boolean entering) {
        if(!ENABLED||done||!(chunk instanceof WorldChunk full)||!(full.getWorld() instanceof ServerWorld world)||!target(world,full))return;
        if(entering)NATIVE_DEPTH.set(NATIVE_DEPTH.get()+1);
        var extra=new LinkedHashMap<String,Object>();extra.put("argumentCubeY",cubeY);
        extra.put("argumentAsSectionIndexToCubeY",world.sectionIndexToCoord(cubeY));
        event(entering?"native-apply-head":"native-apply-return",world,extra);
        if(!entering)NATIVE_DEPTH.set(Math.max(0,NATIVE_DEPTH.get()-1));
    }
    public static void blockChanged(WorldChunk chunk,BlockPos pos,BlockState oldState,BlockState state) {
        if(!ENABLED||done||oldState==null||oldState.equals(state)||!(chunk.getWorld() instanceof ServerWorld world)||!target(world,chunk))return;
        boolean nativeApply=NATIVE_DEPTH.get()>0;
        if(!nativeApply)MUTATIONS.incrementAndGet();
        event("target-setBlockState",world,Map.of("position",pos.toShortString(),"old",oldState.toString(),"new",state.toString(),"insideNativeApply",nativeApply));
    }
    public static void entering(ServerWorld world,WorldChunk chunk) {
        if(!target(world,chunk))return;
        if(!CubicAvailability.onWorldThread(world)){wrongThread=Thread.currentThread().getName();event("ingest-off-owner-thread",world,Map.of());return;}
        var row=metadata(world);row.put("before",serverCell());row.put("kind","production-ingest");
        row.put("enterNanoTime",System.nanoTime());INGEST.push(row);
    }
    public static void selected(ServerWorld world,WorldChunk chunk,BlockPos pos) {
        if(!target(world,chunk)||!CubicAvailability.onWorldThread(world)||INGEST.isEmpty())return;
        var row=INGEST.peek();var access=WorldAccess.of(world);row.put("selected",pos.toShortString());
        row.put("selectedFull",access.isVolumeAvailable(D6ChunkPos.ofBlock(pos.getX(),pos.getY(),pos.getZ())));
        row.put("selectedStateIfLoaded",access.readIfLoaded(pos).map(Object::toString).orElse(null));
    }
    public static void returnedColour(ServerWorld world,WorldChunk chunk,int rgb) {
        if(target(world,chunk)&&CubicAvailability.onWorldThread(world)&&!INGEST.isEmpty())INGEST.peek().put("returnedColour",rgb);
    }
    public static void returned(ServerWorld world,WorldChunk chunk) {
        if(!target(world,chunk)||!CubicAvailability.onWorldThread(world)||INGEST.isEmpty())return;
        var row=INGEST.pop();row.put("after",serverCell());row.put("returnNanoTime",System.nanoTime());
        row.put("returnServerTick",world.getServer().getTicks());row.put("sequence",EVENT_SEQUENCE.incrementAndGet());append(row);
    }
    public static void invalidated(long key) {
        if(!ENABLED||done||key!=KEY||player==null)return;
        event("target-section-invalidated",player.getServerWorld(),Map.of("sectionKey",key));
    }
    /** Only real main-client accepted target payloads, never a fabricated packet. */
    public static void accepted(long key,byte[] bytes) {
        if(!ENABLED||done||key!=KEY)return;
        var section=SurfaceSection.fromBytes(bytes);if(section==null)return;
        packet=new Packet(++packetSequence,new Cell(section.height(0,0),section.colour(0,0)),sha(bytes));
        var row=new LinkedHashMap<String,Object>();row.put("sequence",packet.sequence());row.put("cell",packet.cell());
        row.put("payloadSha256",packet.payloadSha256());row.put("nanoTime",System.nanoTime());
        synchronized(PACKETS){if(PACKETS.size()<128)PACKETS.add(row);else DROPPED.incrementAndGet();}
        if(runtime!=null)try {
            var folder=runtime.resolve("surface-arrival-packets");Files.createDirectories(folder);
            Files.write(folder.resolve("packet-"+packet.sequence()+".bin"),bytes);
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static String sha(byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    private static Cell serverCell() {
        var store=SurfaceIngest.store();return store==null?new Cell(SurfaceSection.NO_HEIGHT,0):new Cell(store.heightAt(8,8,0),store.colourAt(8,8,0));
    }
    private static Cell diskCell() {
        if(SurfaceIngest.store()==null)return new Cell(SurfaceSection.NO_HEIGHT,0);
        SurfaceIngest.store().flush();var backend=new CompressedSectionStorage(new FileSectionStorage(player.getServerWorld().getServer().getSavePath(WorldSavePath.ROOT).resolve("drmd/surface")));
        try{var bytes=backend.read(KEY);var section=bytes==null?null:SurfaceSection.fromBytes(bytes);return section==null?new Cell(SurfaceSection.NO_HEIGHT,0):new Cell(section.height(0,0),section.colour(0,0));}
        finally{backend.close();}
    }
    private static Map<String,Object> snapshot() {
        var world=player.getServerWorld();var row=metadata(world);var access=WorldAccess.of(world);
        row.put("phase",phase);row.put("player",player.getPos().toString());row.put("client",clientSample);
        row.put("serverCell",serverCell());row.put("diskCell",diskCell());
        row.put("actualHighIfLoaded",access.readIfLoaded(HIGH).map(Object::toString).orElse(null));
        row.put("actualLowIfLoaded",access.readIfLoaded(LOW).map(Object::toString).orElse(null));
        row.put("mutationsOutsideNativeApply",MUTATIONS.get());return row;
    }
    private static void move(double y,Phase next) {
        player.teleport(player.getServerWorld(),8.5,y,3.5,Set.of(),0,45);phase=next;age=0;captureName=null;capturedName=null;
        event("ordinary-player-move",player.getServerWorld(),Map.of("feetY",y,"phase",next));
    }
    private static void close(String status,String reason) {
        if(phase==Phase.CLOSE)return;
        var row=new LinkedHashMap<String,Object>();row.put("status",status);row.put("reason",reason);
        row.put("scope","Actual saved black observation repair, not first-ever ingestion or generic migration");
        row.put("contextFramesNotRenderedSmoothingProof",true);
        row.put("realNativeBackend",CubicAvailability.backend(player.getServerWorld())==CubicAvailability.Backend.NATIVE_CUBIC);
        row.put("normalPlayerInterestOnly",true);row.put("fixtureBlockEdits",0);row.put("firstServerFullTick",firstServerFullTick);
        row.put("firstBothFullTick",firstBothFullTick);row.put("observationTicksAfterFull",firstServerFullTick<0?0:player.getServerWorld().getServer().getTicks()-firstServerFullTick);
        row.put("baselinePacket",baselinePacket);row.put("mutationsAfterBaseline",MUTATIONS.get()-baselineMutations);
        row.put("finalSnapshot",snapshot());row.put("droppedEventCount",DROPPED.get());outcome=row;
        phase=Phase.CLOSE;frames=0;captureName="final-"+status.toLowerCase(Locale.ROOT);capturedName=null;
    }
    private static boolean correctClient(Cell cell) {
        var c=clientSample;return c!=null&&cell.equals(c.cell())&&cell.equals(c.packet().cell())&&c.packet().sequence()>baselinePacket;
    }
    private static void pump() {
        var world=player.getServerWorld();var access=WorldAccess.of(world);var c=clientSample;age++;
        if(wrongThread!=null){close("OBSERVER_FAILURE","production callback off owner thread: "+wrongThread);return;}
        if(DROPPED.get()!=0){close("OBSERVER_FAILURE","bounded observation log overflow");return;}
        if(CubicAvailability.backend(world)!=CubicAvailability.Backend.NATIVE_CUBIC){close("OBSERVER_FAILURE","actual BCC backend required");return;}
        if(age%40==0)System.out.println("SURFACE_ARRIVAL "+phase+" age="+age+" tick="+world.getServer().getTicks()+" highFull="+access.isVolumeAvailable(HIGH_CUBE)+" server="+serverCell()+" client="+c);
        if(phase==Phase.LOW) {
            if(age>600){close("PREMISE_NOT_OBSERVED","low black-cache/unknown-high premise not established within600ticks");return;}
            if(!BLACK.equals(serverCell())){close("PREMISE_NOT_OBSERVED","saved black observation changed automatically before the stable low premise; retain actual startup timeline");return;}
            var column=CubicAvailability.loadedColumn(world,0,0);
            boolean ready=c!=null&&Math.abs(c.y()-1027)<1&&!c.highFull()&&c.lowFull()&&!access.isVolumeAvailable(HIGH_CUBE)&&access.isVolumeAvailable(LOW_CUBE)
                &&access.readIfLoaded(LOW).map(s->s.isOf(Blocks.CYAN_CONCRETE)).orElse(false)
                &&column!=null&&column.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,8,8)==2300
                &&BLACK.equals(c.cell())&&BLACK.equals(c.packet().cell())&&c.packet().sequence()>0;
            if(!ready){unavailableTicks=0;return;}
            if(++unavailableTicks<20)return;
            if(!BLACK.equals(diskCell())){close("OBSERVER_FAILURE","original saved black disk premise changed");return;}
            if(!"low-black-before-arrival".equals(captureName)){frames=0;captureName="low-black-before-arrival";}
            if(!captureName.equals(capturedName))return;
            event("confirmed-low-black-premise",world,snapshot());baselinePacket=packet.sequence();baselineMutations=MUTATIONS.get();
            move(2303,Phase.ASCEND);return;
        }
        if(MUTATIONS.get()!=baselineMutations){close("PREMISE_CONTAMINATED","target-column block mutation outside native apply during no-edit arrival; inspect original timeline");return;}
        boolean full=access.isVolumeAvailable(HIGH_CUBE);
        if(full&&firstServerFullTick<0) {
            firstServerFullTick=world.getServer().getTicks();event("first-server-high-full",world,Map.of());
        }
        if(firstServerFullTick<0) {
            if(age>600)close("OBSERVER_FAILURE","ordinary high player interest did not make the real high cube FULL within600ticks");return;
        }
        int elapsed=world.getServer().getTicks()-firstServerFullTick;
        if(!full){close("PREMISE_NOT_OBSERVED","high cube became unavailable during the fixed600tick no-edit observation");return;}
        if(!access.readIfLoaded(HIGH).map(s->s.isOf(Blocks.GOLD_BLOCK)).orElse(false)) {
            close("PREMISE_NOT_OBSERVED","actual saved FULL high block is not GOLD; no substitute marker write permitted");return;
        }
        var column=CubicAvailability.loadedColumn(world,0,0);
        if(column==null||column.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,8,8)!=2300) {
            close("PREMISE_NOT_OBSERVED","actual cached native winner no longer2300");return;
        }
        boolean clientReady=c!=null&&c.highFull()&&Math.abs(c.y()-2303)<1;
        if(clientReady&&firstBothFullTick<0){firstBothFullTick=world.getServer().getTicks();event("first-server-client-high-full",world,Map.of());phase=Phase.OBSERVE;}
        if(clientReady&&GOLD.equals(serverCell())&&correctClient(GOLD)) {
            lastDisk=diskCell();if(GOLD.equals(lastDisk)){close("PASS","Actual saved black cell repaired and freshly streamed after native arrival without target edits");return;}
        }
        if(elapsed<600)return;
        if(!clientReady){close("OBSERVER_FAILURE","client arrival/FULL readiness missing at the fixed600tick deadline");return;}
        lastDisk=diskCell();
        if(!GOLD.equals(serverCell()))close("FAIL_NO_AVAILABLE_REFRESH","Actual FULL GOLD remained incorrectly observed after600ticks with no target edits");
        else if(!GOLD.equals(lastDisk))close("FAIL_DISK_REFRESH","Available live GOLD was not persisted after the real flush");
        else close("FAIL_NO_FRESH_CLIENT_REFRESH","Available live/disk GOLD did not reach a fresh real client packet within600ticks");
    }
    private static void tick(MinecraftClient client) {
        if(done)return;DescentClient.markUserFlightChoice();
        if(++ticks>3600){throw new IllegalStateException("SURFACE_ARRIVAL observer timeout before closed outcome");}
        if(client.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)
            for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(client.player==null||client.world==null||client.getServer()==null)return;
        if(!requested) {
            requested=true;runtime=client.runDirectory.toPath();
            if(!client.runDirectory.getName().equals("cubic-surface-arrival-automation"))throw new IllegalStateException("dedicated disposable arrival runtime required");
            client.setScreen(null);client.options.hudHidden=true;DescentConfig.cockpit=false;DescentConfig.weaponView=false;
            var uuid=client.player.getUuid();client.getServer().execute(()->{
                player=client.getServer().getPlayerManager().getPlayer(uuid);player.stopRiding();FlightSystem.disable(player,DescentPlayerData.get(player));
                player.changeGameMode(GameMode.CREATIVE);player.getAbilities().allowFlying=true;player.getAbilities().flying=true;player.sendAbilitiesUpdate();modePrepared=true;
            });return;
        }
        if(modePrepared&&!lowRequested&&!DescentClientState.enabled&&!client.player.hasVehicle()){lowRequested=true;client.getServer().execute(()->move(1027,Phase.LOW));}
        var access=WorldAccess.of(client.world);clientSample=new ClientSample(client.player.getY(),access.isVolumeAvailable(HIGH_CUBE),access.isVolumeAvailable(LOW_CUBE),
            new Cell(PlanetClientState.INSTANCE.observedHeight(8,8,0),PlanetClientState.INSTANCE.observedColour(8,8,0)),packet);
        client.player.setYaw(0);client.player.setHeadYaw(0);client.player.setPitch(45);
    }
    public static void rendered() {
        if(!ENABLED||done||captureName==null||captureName.equals(capturedName))return;
        var client=MinecraftClient.getInstance();if(client.currentScreen!=null||++frames<3)return;
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            var dir=runtime.resolve("surface-arrival-frames");Files.createDirectories(dir);image.writeTo(dir.resolve(captureName+".png"));capturedName=captureName;
            if(phase==Phase.CLOSE) {
                var report=new LinkedHashMap<>(outcome);
                synchronized(TIMELINE){report.put("timeline",List.copyOf(TIMELINE));}
                synchronized(PACKETS){report.put("actualAcceptedPackets",List.copyOf(PACKETS));}
                Files.writeString(runtime.resolve("surface-arrival-outcome.json"),new com.google.gson.Gson().toJson(report));
                System.out.println("SURFACE_ARRIVAL_NATIVE_OUTCOME "+outcome.get("status")+" "+outcome.get("reason"));
                done=true;client.scheduleStop();
                if(String.valueOf(outcome.get("status")).startsWith("FAIL")||"OBSERVER_FAILURE".equals(outcome.get("status")))throw new IllegalStateException("SURFACE_ARRIVAL_NATIVE "+outcome.get("reason"));
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
