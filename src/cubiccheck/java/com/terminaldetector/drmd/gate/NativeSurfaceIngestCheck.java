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
import com.terminaldetector.drmd.world.store.CompressedSectionStorage;
import com.terminaldetector.drmd.world.store.FileSectionStorage;
import com.terminaldetector.drmd.world.store.SectionKey;
import com.terminaldetector.drmd.world.store.SurfaceIngest;
import com.terminaldetector.drmd.world.store.SurfaceSection;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.world.chunk.WorldChunk;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Actual native partial-Y surface ingestion. Observers never write heightmaps,
 * cube availability, surface storage, packets or native metadata and own no tickets. */
public final class NativeSurfaceIngestCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.surfaceIngest");
    private static final BlockPos HIGH=new BlockPos(8,2300,8),UNDER_HIGH=HIGH.down(2),LOW=new BlockPos(8,1024,8),LOW_EDIT=LOW.east(2);
    private static final D6ChunkPos HIGH_CUBE=D6ChunkPos.ofBlock(8,2300,8),LOW_CUBE=D6ChunkPos.ofBlock(8,1024,8);
    private static final long KEY=SectionKey.of(0,0,0);
    private static final List<Map<String,Object>> SCENES=new ArrayList<>(),EVENTS=new ArrayList<>(),PACKETS=new ArrayList<>();
    private static final ArrayDeque<IngestEvent> ENTERED=new ArrayDeque<>();
    private static volatile ServerPlayerEntity player;
    private static volatile boolean modePrepared,done;
    private static boolean requested,lowRequested;
    private static volatile Phase phase=Phase.BOOT;
    private static volatile ClientSample clientSample;
    private static volatile Packet packet=new Packet(0,new Cell(SurfaceSection.NO_HEIGHT,0),"");
    private static volatile String captureName,capturedName,closingStatus,closingReason;
    private static volatile String wrongThread;
    private static volatile Map<String,Object> finalSnapshot;
    private static volatile Path runtime;
    private static int ticks,age,frames,unavailableTicks,packetSequence,ingestSequence,requiredIngest,requiredPacket,baselineColumn,droppedEvents;
    private static boolean returnEdited;
    private static Cell gold,lime;
    private static IngestEvent firstViolation,lastIngest;
    private enum Phase { BOOT,LOW,GOLD,LIME,RESTORED,UNLOAD,PROBE,RETURN_HIGH,CLOSE }
    private record Cell(int height,int colour) {}
    private record Packet(int sequence,Cell cell,String payloadSha256) {}
    private record ClientSample(double y,boolean highFull,boolean lowFull,Cell cell,Packet packet) {}
    private static final class IngestEvent {
        final int sequence;final Cell before;final boolean highBefore,lowBefore;final int metadataHeight;
        BlockPos actualWinner;String actualWinnerState;boolean winnerFull;Integer returnedColour;Cell after;boolean highAfter;
        IngestEvent(ServerWorld world,WorldChunk chunk){sequence=++ingestSequence;before=serverCell();var access=WorldAccess.of(world);highBefore=access.isVolumeAvailable(HIGH_CUBE);lowBefore=access.isVolumeAvailable(LOW_CUBE);metadataHeight=chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,8,8);}
        Map<String,Object> json(){var m=new LinkedHashMap<String,Object>();m.put("sequence",sequence);m.put("before",before);m.put("after",after);m.put("highFullBefore",highBefore);m.put("highFullAfter",highAfter);m.put("lowFull",lowBefore);m.put("metadataHeightAtAuthoredHigh",metadataHeight);m.put("actualWinner",actualWinner==null?null:actualWinner.toShortString());m.put("actualWinnerFull",winnerFull);m.put("actualWinnerStateIfLoaded",actualWinnerState);m.put("returnedColour",returnedColour);return m;}
    }
    private NativeSurfaceIngestCheck(){}
    public static void register(){
        ClientTickEvents.END_CLIENT_TICK.register(NativeSurfaceIngestCheck::tick);
        ServerTickEvents.END_SERVER_TICK.register(server->{if(player!=null&&!done&&phase!=Phase.BOOT&&phase!=Phase.CLOSE)pump();});
    }
    /** Called only by a non-mutating test probe of the real production callback. */
    public static void entering(ServerWorld world,WorldChunk chunk){
        if(!ENABLED||done||phase==Phase.CLOSE||player==null||world!=player.getServerWorld()||chunk.getPos().x!=0||chunk.getPos().z!=0)return;
        if(!CubicAvailability.onWorldThread(world)){wrongThread=Thread.currentThread().getName();return;}
        ENTERED.push(new IngestEvent(world,chunk));
    }
    public static void colourEntering(ServerWorld world,WorldChunk chunk,BlockPos pos){
        if(!ENABLED||!CubicAvailability.onWorldThread(world)||ENTERED.isEmpty()||world!=player.getServerWorld()||chunk.getPos().x!=0||chunk.getPos().z!=0)return;
        var e=ENTERED.peek();e.actualWinner=pos.toImmutable();var access=WorldAccess.of(world);e.winnerFull=access.isVolumeAvailable(D6ChunkPos.ofBlock(pos.getX(),pos.getY(),pos.getZ()));e.actualWinnerState=access.readIfLoaded(pos).map(Object::toString).orElse(null);
    }
    public static void colourReturned(ServerWorld world,WorldChunk chunk,int rgb){
        if(ENABLED&&CubicAvailability.onWorldThread(world)&&!ENTERED.isEmpty()&&world==player.getServerWorld()&&chunk.getPos().x==0&&chunk.getPos().z==0)ENTERED.peek().returnedColour=rgb;
    }
    public static void returned(ServerWorld world,WorldChunk chunk){
        if(!ENABLED||done||!CubicAvailability.onWorldThread(world)||player==null||world!=player.getServerWorld()||chunk.getPos().x!=0||chunk.getPos().z!=0||ENTERED.isEmpty())return;
        var e=ENTERED.pop();e.after=serverCell();e.highAfter=WorldAccess.of(world).isVolumeAvailable(HIGH_CUBE);lastIngest=e;
        if(EVENTS.size()==256){EVENTS.removeFirst();droppedEvents++;}EVENTS.add(e.json());
        if(gold!=null&&phase.ordinal()>=Phase.UNLOAD.ordinal()&&HIGH.equals(e.actualWinner)&&!e.winnerFull&&gold.equals(e.before)&&!gold.equals(e.after)&&firstViolation==null)firstViolation=e;
    }
    /** Receives only the real main client's already-accepted SurfacePayload data. */
    public static void acceptedSection(long key,byte[] bytes){
        if(!ENABLED||done||key!=KEY)return;var section=SurfaceSection.fromBytes(bytes);if(section==null)return;
        packet=new Packet(++packetSequence,new Cell(section.height(0,0),section.colour(0,0)),sha(bytes));
        if(runtime!=null&&packetSequence<=128)try{var folder=runtime.resolve("surface-ingest-packets");Files.createDirectories(folder);Files.write(folder.resolve("packet-"+packetSequence+".bin"),bytes);}catch(java.io.IOException e){throw new IllegalStateException(e);}
        if(PACKETS.size()==128)PACKETS.removeFirst();PACKETS.add(Map.of("sequence",packet.sequence(),"cell",packet.cell(),"sha256",packet.payloadSha256()));
    }
    private static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static Cell serverCell(){var store=SurfaceIngest.store();return store==null?new Cell(SurfaceSection.NO_HEIGHT,0):new Cell(store.heightAt(8,8,0),store.colourAt(8,8,0));}
    private static Cell diskCell(){
        var world=player.getServerWorld();var root=world.getServer().getSavePath(WorldSavePath.ROOT).resolve("drmd/surface");
        SurfaceIngest.store().flush();var backend=new CompressedSectionStorage(new FileSectionStorage(root));
        try{var bytes=backend.read(KEY);var section=bytes==null?null:SurfaceSection.fromBytes(bytes);return section==null?new Cell(SurfaceSection.NO_HEIGHT,0):new Cell(section.height(0,0),section.colour(0,0));}finally{backend.close();}
    }
    private static boolean full(BlockPos at){return WorldAccess.of(player.getServerWorld()).isVolumeAvailable(D6ChunkPos.ofBlock(at.getX(),at.getY(),at.getZ()));}
    private static boolean is(BlockPos at,net.minecraft.block.Block block){return WorldAccess.of(player.getServerWorld()).readIfLoaded(at).map(s->s.isOf(block)).orElse(false);}
    private static void place(BlockPos at,net.minecraft.block.Block block){if(!full(at))throw new IllegalStateException("Fixture write outside actual FULL cube: "+at);player.getServerWorld().setBlockState(at,block.getDefaultState(),3);}
    private static void touch(BlockPos at){var state=WorldAccess.of(player.getServerWorld()).readIfLoaded(at).orElseThrow();place(at,state.isOf(Blocks.RED_CONCRETE)?Blocks.BLACK_CONCRETE:Blocks.RED_CONCRETE);}
    private static void move(double feetY,Phase next){player.teleport(player.getServerWorld(),8.5,feetY,3.5,Set.of(),0,45);phase=next;age=0;unavailableTicks=0;captureName=null;capturedName=null;}
    private static boolean synchronizedCell(Cell expected){var c=clientSample;return c!=null&&expected.equals(serverCell())&&expected.equals(c.cell())&&expected.equals(c.packet().cell())&&c.packet().sequence()>requiredPacket;}
    private static boolean captured(String name){return name.equals(capturedName);}
    private static void capture(String name){if(!name.equals(captureName)){frames=0;captureName=name;}}
    private static Map<String,Object> snapshot(String scene){
        var world=player.getServerWorld();var access=WorldAccess.of(world);var column=CubicAvailability.loadedColumn(world,0,0);
        var row=new LinkedHashMap<String,Object>();row.put("scene",scene);row.put("phase",phase);row.put("serverPlayer",player.getPos().toString());row.put("client",clientSample);row.put("serverCell",serverCell());row.put("diskCell",diskCell());row.put("authoredHighBlockY",HIGH.getY());row.put("actualHighStateIfLoaded",access.readIfLoaded(HIGH).map(Object::toString).orElse(null));row.put("expectedGoldSurface",gold);row.put("highFull",access.isVolumeAvailable(HIGH_CUBE));row.put("lowFull",access.isVolumeAvailable(LOW_CUBE));row.put("retainedLowMarker",is(LOW,Blocks.CYAN_CONCRETE));row.put("parentColumnPresent",column!=null);row.put("parentColumnIdentity",column==null?0:System.identityHashCode(column));row.put("metadataHeightAtHigh",column==null?null:column.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,8,8));row.put("lastIngest",lastIngest==null?null:lastIngest.json());row.put("firstViolation",firstViolation==null?null:firstViolation.json());return row;
    }
    private static void finish(String status,String why){
        if(phase==Phase.CLOSE)return;finalSnapshot=snapshot(status);closingStatus=status;closingReason=why;phase=Phase.CLOSE;frames=0;captureName=switch(status){case "PASS"->"returned-high-known";case "PREMISE_NOT_OBSERVED"->"premise-not-observed";case "CONTROL_FAILURE"->"loaded-contract-failure";default->"failed-unknown-winner";};
    }
    private static void pump(){
        int budget=phase==Phase.UNLOAD?1800:600;
        if(++age>budget){finish("FAIL","stage timeout "+phase+" after "+budget+" ticks; fixture/loading readiness must not be treated as a production defect");return;}
        var world=player.getServerWorld();var c=clientSample;var access=WorldAccess.of(world);
        if(wrongThread!=null){finish("FAIL","production callback observed off owner thread: "+wrongThread+"; targeted winner premise not established");return;}
        if(age%40==0){var col=CubicAvailability.loadedColumn(world,0,0);System.out.println("SURFACE_INGEST_PHASE "+phase+" age="+age+" full="+full(HIGH)+"/"+full(LOW)+" cachedHigh="+(col==null?null:col.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,8,8))+" server="+serverCell()+" client="+c);}
        if(CubicAvailability.backend(world)!=CubicAvailability.Backend.NATIVE_CUBIC){finish("FAIL","actual native BCC backend required");return;}
        if(phase==Phase.LOW){
            if(c==null||Math.abs(c.y()-1027)>1||!c.lowFull()||!full(LOW))return;
            place(LOW,Blocks.CYAN_CONCRETE);if(!is(LOW,Blocks.CYAN_CONCRETE)){finish("FAIL","retained low witness write failed");return;}
            SCENES.add(snapshot("authored-low-cube64"));move(2303,Phase.GOLD);return;
        }
        if(phase==Phase.GOLD){
            if(c==null||Math.abs(c.y()-2303)>1||!c.highFull()||!full(HIGH))return;
            if(gold==null){
                requiredPacket=packet.sequence();requiredIngest=ingestSequence;place(UNDER_HIGH,Blocks.LIME_CONCRETE);place(HIGH,Blocks.GOLD_BLOCK);
                gold=new Cell(HIGH.getY()+1,world.getBlockState(HIGH).getMapColor(world,HIGH).color);
                lime=new Cell(UNDER_HIGH.getY()+1,world.getBlockState(UNDER_HIGH).getMapColor(world,UNDER_HIGH).color);return;
            }
            // Diagnose the real loaded contract before any unavailable-Y assertion.
            // The actual native sampled number is recorded, never calibrated to make
            // an incorrect selected block or palette value pass.
            if(is(HIGH,Blocks.GOLD_BLOCK)&&lastIngest!=null&&lastIngest.sequence>requiredIngest&&lastIngest.actualWinner!=null&&c.packet().sequence()>requiredPacket&&serverCell().equals(c.cell())&&serverCell().equals(c.packet().cell())){
                var selected=lastIngest.actualWinner;
                if(selected.getX()!=HIGH.getX()||selected.getZ()!=HIGH.getZ()||selected.getY()>HIGH.getY()){finish("PREMISE_NOT_OBSERVED","authored GOLD is not the actual production-selected highest sample; unavailable-winner test not established");return;}
                if(!HIGH.equals(selected)||!Objects.equals(lastIngest.returnedColour,gold.colour())||!gold.equals(serverCell())){finish("CONTROL_FAILURE","loaded FULL GOLD source contract mismatch: actual sampled height/winner/state/colour and stored/received cell preserved; unavailable-Y premise not reached");return;}
            }
            if(!synchronizedCell(gold))return;
            capture("loaded-known-gold");if(!captured("loaded-known-gold"))return;
            SCENES.add(snapshot("loaded-known-gold"));baselineColumn=System.identityHashCode(CubicAvailability.loadedColumn(world,0,0));requiredPacket=packet.sequence();place(HIGH,Blocks.AIR);phase=Phase.LIME;age=0;return;
        }
        if(phase==Phase.LIME){
            if(!full(HIGH)||!synchronizedCell(lime))return;
            capture("loaded-demolition-lime");if(!captured("loaded-demolition-lime"))return;
            SCENES.add(snapshot("loaded-demolition-lime"));requiredPacket=packet.sequence();place(HIGH,Blocks.GOLD_BLOCK);phase=Phase.RESTORED;age=0;return;
        }
        if(phase==Phase.RESTORED){
            if(!full(HIGH)||!synchronizedCell(gold))return;
            capture("loaded-restored-gold");if(!captured("loaded-restored-gold"))return;
            SCENES.add(snapshot("loaded-restored-gold"));move(1027,Phase.UNLOAD);return;
        }
        if(phase==Phase.UNLOAD){
            var column=CubicAvailability.loadedColumn(world,0,0);
            boolean ready=c!=null&&Math.abs(c.y()-1027)<1&&!c.highFull()&&c.lowFull()&&!full(HIGH)&&full(LOW)&&is(LOW,Blocks.CYAN_CONCRETE)&&column!=null;
            if(!ready){unavailableTicks=0;return;}if(++unavailableTicks<20)return;
            SCENES.add(snapshot("actual-partial-y-unload"));
            if(System.identityHashCode(column)!=baselineColumn||column.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,8,8)!=HIGH.getY()+1){finish("PREMISE_NOT_OBSERVED","native retained parent/winner premise not observed; no fake metadata or justified production fix");return;}
            requiredIngest=ingestSequence;requiredPacket=packet.sequence();touch(LOW_EDIT);phase=Phase.PROBE;age=0;return;
        }
        if(phase==Phase.PROBE){
            if(lastIngest==null||lastIngest.sequence<=requiredIngest||c==null)return;
            if(lastIngest.actualWinner!=null&&!HIGH.equals(lastIngest.actualWinner)){finish("PREMISE_NOT_OBSERVED","actual production-selected winner differs from authored high marker");return;}
            if(firstViolation!=null||!gold.equals(serverCell())){
                // Wait for the normal invalidation/pump to expose the actual changed cell,
                // without injecting a packet or replacing the client's observed store.
                if(c.packet().sequence()<=requiredPacket)return;
                finish("FAIL","known GOLD surface was overwritten while its actual selected winner cube was unavailable");return;
            }
            if(full(HIGH)){finish("FAIL","production resample made the distant high cube FULL; observer owns no tickets or unknown block reads");return;}
            if(!synchronizedCell(gold)||!gold.equals(diskCell()))return;
            capture("unknown-winner-preserved");if(!captured("unknown-winner-preserved"))return;
            SCENES.add(snapshot("unknown-winner-preserved"));move(2303,Phase.RETURN_HIGH);return;
        }
        if(phase==Phase.RETURN_HIGH){
            if(c==null||Math.abs(c.y()-2303)>1||!c.highFull()||!full(HIGH))return;
            if(!is(HIGH,Blocks.GOLD_BLOCK)){finish("FAIL","actual authored high block did not survive native unload/return");return;}
            if(!returnEdited){returnEdited=true;requiredPacket=packet.sequence();touch(HIGH.add(1,-1,1));return;}
            if(!synchronizedCell(gold))return;
            finish("PASS","actual unavailable-winner surface retained, loaded demolition/restore and cold return functional; no production change justified");
        }
    }
    private static void tick(MinecraftClient client){
        if(done)return;DescentClient.markUserFlightChoice();
        if(++ticks>4800){fail("overall observer timeout before a complete outcome");return;}
        if(client.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(client.player==null||client.world==null||client.getServer()==null)return;
        if(!requested){
            requested=true;runtime=client.runDirectory.toPath();if(!client.runDirectory.getName().equals("cubic-surface-automation"))throw new IllegalStateException("disposable surface runtime only");
            client.setScreen(null);client.options.hudHidden=true;DescentConfig.cockpit=false;DescentConfig.weaponView=false;
            var uuid=client.player.getUuid();client.getServer().execute(()->{
                player=client.getServer().getPlayerManager().getPlayer(uuid);player.stopRiding();FlightSystem.disable(player,DescentPlayerData.get(player));player.changeGameMode(GameMode.CREATIVE);player.getAbilities().allowFlying=true;player.getAbilities().flying=true;player.sendAbilitiesUpdate();player.getServerWorld().setTimeOfDay(6000);modePrepared=true;
            });return;
        }
        if(modePrepared&&!lowRequested&&!DescentClientState.enabled&&!client.player.hasVehicle()){lowRequested=true;client.getServer().execute(()->move(1027,Phase.LOW));}
        var access=WorldAccess.of(client.world);clientSample=new ClientSample(client.player.getY(),access.isVolumeAvailable(HIGH_CUBE),access.isVolumeAvailable(LOW_CUBE),new Cell(PlanetClientState.INSTANCE.observedHeight(8,8,0),PlanetClientState.INSTANCE.observedColour(8,8,0)),packet);
        client.player.setYaw(0);client.player.setHeadYaw(0);client.player.setPitch(45);
    }
    /** Fresh actual GameRenderer frame, context evidence only, no smoothing/pixel claim. */
    public static void rendered(){
        if(!ENABLED||done||captureName==null||captureName.equals(capturedName))return;
        var client=MinecraftClient.getInstance();if(client.currentScreen!=null||++frames<3)return;
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){
            var folder=runtime.resolve("surface-ingest-frames");Files.createDirectories(folder);image.writeTo(folder.resolve(captureName+".png"));capturedName=captureName;
            if(phase==Phase.CLOSE){writeOutcome();if("FAIL".equals(closingStatus)||"CONTROL_FAILURE".equals(closingStatus))fail(closingReason);else{done=true;client.scheduleStop();}}
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static void writeOutcome()throws java.io.IOException{
        var report=new LinkedHashMap<String,Object>();report.put("status",closingStatus);report.put("reason",closingReason);report.put("realNativeBackend",true);report.put("normalPlayerInterestOnly",true);report.put("authoredHigh",HIGH.toShortString());report.put("authoredRetainedLow",LOW.toShortString());report.put("loadedDemolitionControl",UNDER_HIGH.toShortString());report.put("scenes",SCENES);report.put("finalSnapshot",finalSnapshot);report.put("productionIngestEvents",EVENTS);report.put("droppedEventCount",droppedEvents);report.put("actualAcceptedPackets",PACKETS);report.put("contextFramesNotRenderedSmoothingProof",true);Files.writeString(runtime.resolve("surface-ingest-outcome.json"),new com.google.gson.Gson().toJson(report));
        System.out.println("SURFACE_INGEST_NATIVE_OUTCOME "+closingStatus+" "+closingReason);
    }
    private static void fail(String reason){
        done=true;var client=MinecraftClient.getInstance();
        try{var report=new LinkedHashMap<String,Object>();report.put("reason",reason);report.put("phase",phase);report.put("stageAge",age);report.put("client",clientSample);report.put("modePrepared",modePrepared);report.put("initialLowTeleportRequested",lowRequested);report.put("finalSnapshot",finalSnapshot);Files.writeString(client.runDirectory.toPath().resolve("surface-ingest-failure.json"),new com.google.gson.Gson().toJson(report));}
        catch(java.io.IOException e){throw new IllegalStateException(e);}finally{client.scheduleStop();}
        throw new IllegalStateException("SURFACE_INGEST_NATIVE "+reason);
    }
}
