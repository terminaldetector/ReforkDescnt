package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.client.input.DescentKeybinds;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import java.nio.file.Files;
import java.util.*;

/** Disposable authored negative shaft, then genuine continuous held-forward flight. Not a cold reader. */
public final class NativeVerticalFlightCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.verticalFlight");
    private static final boolean SURVIVAL=Boolean.getBoolean("drmd.gate.verticalSurvival");
    private static final Vec3d START=new Vec3d(40.5,-80,40.5),F=new Vec3d(0,1,0),UP=new Vec3d(0,0,1);
    private static final int[] HEIGHTS={-64,1024,1640,2150};
    private static final List<Map<String,Object>> SERVER=new ArrayList<>(),CLIENT=new ArrayList<>();
    private static final Map<Integer,Map<String,Object>> SERVER_CROSSES=new LinkedHashMap<>(),CLIENT_CROSSES=new LinkedHashMap<>();
    private static final Set<BlockPos> AUTHORED=new HashSet<>();
    private record MoveStart(String side,Vec3d start,Vec3d requested,Vec3d velocity,String type,int tick){}
    private record Move(int serial,String side,List<Double> start,List<Double> end,List<Double> requested,List<Double> velocity,String type,int tick,boolean horizontalCollision,boolean verticalCollision){}
    private static final ThreadLocal<Deque<MoveStart>> MOVING=ThreadLocal.withInitial(ArrayDeque::new);
    private static final List<Move> CLIENT_MOVES=new ArrayList<>(),SERVER_MOVES=new ArrayList<>();
    private static Vec3d lastClientMoveEnd;private static int matchedClientMove=-1,matchedServerMove=-1;
    private static volatile int pendingEndpointRows;private static volatile String movementFailure;
    private static int afterburnerHeldTicks,measuredTicks,armClientTick;private static volatile int armServerTick=-1;
    private static volatile Map<String,Object> failedAvailability;
    private static volatile UUID playerId;private static volatile String failure;
    private static volatile int phase,materialWrites,initialTeleports,surfaceY=Integer.MIN_VALUE,authorTop,epoch;
    private static volatile boolean armed,done;private static int ticks,frames,serverRows,untouchedAirSamples;
    private static long boot;private static Vec3d previousServer,previousClient;private static boolean previousServerFull,previousClientFull,basis;
    private NativeVerticalFlightCheck(){}
    public static void register(){boot=System.nanoTime();ServerTickEvents.END_SERVER_TICK.register(NativeVerticalFlightCheck::server);ClientTickEvents.END_CLIENT_TICK.register(NativeVerticalFlightCheck::client);}
    private static List<Double> xyz(Vec3d p){return List.of(p.x,p.y,p.z);}
    private static boolean full(net.minecraft.world.World w,Vec3d p){return WorldAccess.of(w).readIfLoaded(BlockPos.ofFloored(p)).isPresent();}
    private static void server(MinecraftServer server){
        if(done||playerId==null||failure!=null)return;
        try{
            var p=server.getPlayerManager().getPlayer(playerId);if(p==null)return;var w=p.getServerWorld();var access=WorldAccess.of(w);var data=DescentPlayerData.get(p);
            if(phase==0){
                if(w.getBottomY()!=-16384||w.getHeight()!=32768||access.backend()!=CubicAvailability.Backend.NATIVE_CUBIC)throw new IllegalStateException("actual native preset required");
                // Normal spectator mode is staging only: no collision measurements are made until creative/6DoF is armed.
                FlightSystem.disable(p,data);p.changeGameMode(GameMode.SPECTATOR);p.teleport(w,START.x,START.y,START.z,Set.of(),0,0);initialTeleports++;phase=1;
                System.out.println("VERTICAL_FLIGHT_INITIAL_STAGING "+p.getPos()+" spectator=true");return;
            }
            if(surfaceY==Integer.MIN_VALUE){
                var column=CubicAvailability.loadedColumn(w,40>>4,40>>4);if(column==null)return;
                int sampled=column.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,40&15,40&15);
                var ground=access.readIfLoaded(new BlockPos(40,sampled,40));if(ground.isEmpty())return;
                if(ground.get().isAir()||sampled<-16||sampled>256)throw new IllegalStateException("surface premise not observed: "+sampled+" "+ground.get());
                surfaceY=sampled;authorTop=sampled+4;
            }
            // Real material authoring only in loaded cubes below surface+4, never on the upper route.
            int writes=0;int from=Math.max(-82,p.getBlockY()-3),to=Math.min(authorTop,p.getBlockY()+24);
            for(int y=from;y<=to;y++)for(int x=38;x<=42;x++)for(int z=38;z<=42;z++){
                var at=new BlockPos(x,y,z);if(AUTHORED.contains(at))continue;
                var old=access.readIfLoaded(at);if(old.isEmpty())continue;
                if(old.get().hasBlockEntity())throw new IllegalStateException("authoring refuses existing block entity at "+at);
                var desired=(x==38||x==42||z==38||z==42||y==-82)?Blocks.GLASS.getDefaultState():Blocks.AIR.getDefaultState();
                if(!old.get().equals(desired)){
                    if(writes>=128)continue;
                    if(materialWrites>=8192)throw new IllegalStateException("8192 material-write cap");
                    if(!w.setBlockState(at,desired,2)&&!access.readIfLoaded(at).filter(desired::equals).isPresent())throw new IllegalStateException("authoring write refused "+at);
                    writes++;materialWrites++;
                }
                if(AUTHORED.size()>=8192)throw new IllegalStateException("8192 authored-position cap");AUTHORED.add(at);
            }
            if(phase==1){
                if(p.getPos().distanceTo(START)>.5)throw new IllegalStateException("staging moved before flight");
                for(int y=-82;y<=-68;y++)for(int x=38;x<=42;x++)for(int z=38;z<=42;z++)if(!AUTHORED.contains(new BlockPos(x,y,z)))return;
                p.changeGameMode(SURVIVAL?GameMode.SURVIVAL:GameMode.CREATIVE);FlightSystem.enableWithoutMount(p,data);data.setFlightVelocity(Vec3d.ZERO);
                data.setShipAttitude(0,1,0,0,0,1);ModNetworking.syncPlayer(p,data);epoch=data.portalEpoch();phase=2;return;
            }
            if(!armed)return;if(armServerTick<0)armServerTick=server.getTicks();
            var pos=p.getPos();boolean loaded=full(w,pos);var velocity=data.getFlightVelocity();
            if(p.isSpectator()||p.noClip||!data.isEnabled())throw new IllegalStateException("measured actor lacks ordinary colliding 6DoF ownership");
            if(SURVIVAL&&(!p.isAlive()||p.getAbilities().invulnerable||p.getHealth()!=p.getMaxHealth()))throw new IllegalStateException("damage or invulnerability during native Survival path: "+p.getHealth());
            if(data.portalEpoch()!=epoch||Math.abs(pos.x-START.x)>1||Math.abs(pos.z-START.z)>1)throw new IllegalStateException("unexpected portal/lateral discontinuity");
            var row=new LinkedHashMap<String,Object>();row.put("tick",server.getTicks());row.put("p",xyz(pos));row.put("vMetresPerSecond",xyz(velocity));row.put("full",loaded);row.put("flightLeaseEpoch",com.terminaldetector.drmd.world.contraption.boarding.FlightBodySystem.epoch(p));row.put("flightAcknowledgment",com.terminaldetector.drmd.world.contraption.boarding.FlightBodySystem.acknowledgment(p));row.put("writes",materialWrites);row.put("seed",w.getSeed());row.put("health",p.getHealth());row.put("gameMode",p.interactionManager.getGameMode().getName());
            if(previousServer!=null&&pos.distanceTo(previousServer)>1e-7){
                int clientMatch=matching(CLIENT_MOVES,pos,matchedClientMove),serverMatch=matching(SERVER_MOVES,pos,matchedServerMove);
                row.put("matchingClientMove",clientMatch);row.put("matchingServerMove",serverMatch);
                if(clientMatch>=0){matchedClientMove=clientMatch;pendingEndpointRows=0;}
                else if(serverMatch>=0){matchedServerMove=serverMatch;pendingEndpointRows=0;}
                else {pendingEndpointRows++;synchronized(SERVER){SERVER.add(row);}throw new IllegalStateException("server endpoint lacks a genuine monotonic native move witness: "+row);}
            }
            if(++serverRows>2400)throw new IllegalStateException("server journal cap");synchronized(SERVER){SERVER.add(row);}
            if(pos.y>authorTop+2&&access.readIfLoaded(p.getBlockPos()).filter(s->s.isAir()).isPresent())untouchedAirSamples++;
            if(previousServer!=null)for(int height:HEIGHTS)if(!SERVER_CROSSES.containsKey(height)&&previousServer.y<height&&pos.y>=height){
                if(!previousServerFull||!loaded||velocity.y<=0)throw new IllegalStateException("server crossed unavailable/nonmoving plane "+height);
                synchronized(SERVER_CROSSES){SERVER_CROSSES.put(height,Map.of("before",xyz(previousServer),"after",xyz(pos),"tick",server.getTicks(),"v",xyz(velocity)));}
            }
            previousServer=pos;previousServerFull=loaded;
        }catch(Throwable e){failure=e.toString();e.printStackTrace();}
    }
    private static void client(MinecraftClient c){
        if(done)return;
        ticks++;if((!armed&&ticks>2400)||(armed&&++measuredTicks>2400)||System.nanoTime()-boot>180_000_000_000L){finish(c,"FAIL","180s overallwall /2400pre-admission /2400measuredtick deadline");return;}
        if(failure!=null||movementFailure!=null){finish(c,"FAIL",failure!=null?failure:movementFailure);return;}
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(c.player==null||c.world==null||c.getServer()==null)return;
        if(playerId==null){
            if(!c.runDirectory.toPath().getFileName().toString().equals("cubic-client-automation")||!FabricLoader.getInstance().isModLoaded("bettercubicchunks")){finish(c,"FAIL","disposable native runtime required");return;}
            playerId=c.player.getUuid();c.setScreen(null);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);c.options.setPerspective(Perspective.FIRST_PERSON);
        }
        DescentClient.markUserFlightChoice();
        if(phase<2||!DescentClientState.enabled||c.player.isSpectator())return;
        if(!full(c.world,c.player.getPos())){if(armed){
            var at=c.player.getPos();var access=WorldAccess.of(c.world);var block=BlockPos.ofFloored(at);var cube=access.cubeAt(block);
            var row=new LinkedHashMap<String,Object>();row.put("tick",ticks);row.put("worldTime",c.world.getTime());row.put("p",xyz(at));row.put("block",List.of(block.getX(),block.getY(),block.getZ()));row.put("cube",List.of(cube.x(),cube.y(),cube.z()));row.put("actualCubeState",access.cubeState(cube));row.put("backend",access.backend());
            row.put("lastActualNativeMoveEnd",lastClientMoveEnd==null?List.of():xyz(lastClientMoveEnd));row.put("unchangedSinceLastNativeMove",lastClientMoveEnd!=null&&at.squaredDistanceTo(lastClientMoveEnd)<1e-14);
            row.put("flightAuthorityActive",com.terminaldetector.drmd.client.flight.FlightBodyClient.active());row.put("pendingPredictionCommands",com.terminaldetector.drmd.client.flight.FlightBodyClient.pending());row.put("nextPredictionSequence",com.terminaldetector.drmd.client.flight.FlightBodyClient.sequence());
            synchronized(SERVER){if(!SERVER.isEmpty())row.put("latestActualServerRow",SERVER.getLast());}failedAvailability=Collections.unmodifiableMap(row);
            finish(c,"FAIL","client entered an unavailable cube during measured flight");}return;}
        if(!basis){ShipAttitudeClient.acceptPortalFrame(c.player,F,UP);basis=true;return;}
        if(!armed){previousClient=c.player.getPos();lastClientMoveEnd=previousClient;previousClientFull=true;armClientTick=ticks;armed=true;}
        c.options.forwardKey.setPressed(true);
        var pos=c.player.getPos();boolean loaded=full(c.world,pos);
        boolean burn=phase==2&&pos.y>authorTop+4&&afterburnerHeldTicks<800;
        DescentKeybinds.alwaysRun.setPressed(burn);if(burn)afterburnerHeldTicks++;
        if(lastClientMoveEnd!=null&&pos.distanceTo(lastClientMoveEnd)>1e-7){finish(c,"FAIL","client position changed outside actual Entity.move: "+lastClientMoveEnd+" -> "+pos);return;}
        if(CLIENT.size()>=2400){finish(c,"FAIL","client journal cap");return;}
        CLIENT.add(Map.of("tick",ticks,"p",xyz(pos),"full",loaded,"held",c.options.forwardKey.isPressed(),"forward",xyz(ShipAttitudeClient.get().forward()),"afterburnerKey",burn,"actualAfterburner",DescentClientState.alwaysRun,"configuredMaxSourceUnits",DescentClientState.maxSpeed));
        if(previousClient!=null)for(int height:HEIGHTS)if(!CLIENT_CROSSES.containsKey(height)&&previousClient.y<height&&pos.y>=height){
            if(!previousClientFull||!loaded){finish(c,"FAIL","client crossed unavailable plane "+height);return;}
            CLIENT_CROSSES.put(height,Map.of("before",xyz(previousClient),"after",xyz(pos),"tick",ticks));
        }
        previousClient=pos;previousClientFull=loaded;
        synchronized(SERVER_CROSSES){if(SERVER_CROSSES.size()==4&&CLIENT_CROSSES.size()==4&&pendingEndpointRows==0){c.options.forwardKey.setPressed(false);DescentKeybinds.alwaysRun.setPressed(false);phase=3;}}
    }
    private static int matching(List<Move> moves,Vec3d position,int minimum){
        synchronized(moves){for(int i=moves.size()-1;i>=Math.max(0,minimum);i--){var move=moves.get(i);for(var point:List.of(move.start(),move.end()))if(position.squaredDistanceTo(new Vec3d(point.get(0),point.get(1),point.get(2)))<1e-14)return i;}}
        return -1;
    }
    /** Passive actual collision-call observations. No movement, tick or velocity write is made here. */
    public static void beforeMove(Entity entity,MovementType type,Vec3d requested){
        if(!ENABLED||!armed||done||playerId==null||!playerId.equals(entity.getUuid()))return;
        String side=entity.getWorld().isClient?"client":"server";
        if(side.equals("client")&&entity!=MinecraftClient.getInstance().player)return;
        var pending=MOVING.get();if(pending.size()>=4){movementFailure="native move nesting cap";return;}
        var position=entity.getPos();
        if(side.equals("client")&&lastClientMoveEnd!=null&&position.distanceTo(lastClientMoveEnd)>1e-7)movementFailure="client native move starts after unobserved position discontinuity";
        if(!Double.isFinite(position.lengthSquared())||!Double.isFinite(requested.lengthSquared()))movementFailure="nonfinite actual native move";
        pending.addLast(new MoveStart(side,position,requested,entity.getVelocity(),type.name(),side.equals("client")?ticks:((net.minecraft.server.world.ServerWorld)entity.getWorld()).getServer().getTicks()));
    }
    public static void afterMove(Entity entity){
        if(!ENABLED||!armed||done||playerId==null||!playerId.equals(entity.getUuid()))return;
        // The integrated server may be inside a staging move when the client arms the case.
        var pending=MOVING.get();if(pending.isEmpty())return;var start=pending.removeLast();
        var rows=start.side().equals("client")?CLIENT_MOVES:SERVER_MOVES;var end=entity.getPos();
        synchronized(rows){if(rows.size()>=4096){movementFailure="4096 actual native move receipt cap";return;}rows.add(new Move(rows.size(),start.side(),xyz(start.start()),xyz(end),xyz(start.requested()),xyz(start.velocity()),start.type(),start.tick(),entity.horizontalCollision,entity.verticalCollision));}
        if(start.side().equals("client"))lastClientMoveEnd=end;
    }
    /** Genuine render RETURN hook only: no camera override, teleport-follow or world-step loop. */
    public static void rendered(){
        if(!ENABLED||done||phase!=3||++frames<20)return;var c=MinecraftClient.getInstance();
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){
            if(c.world==null||c.player==null||c.currentScreen!=null)throw new IllegalStateException("real world frame unavailable");
            var folder=c.runDirectory.toPath().resolve("vertical-flight-frames");Files.createDirectories(folder);image.writeTo(folder.resolve("after-four-planes.png"));
            finish(c,"PASS","actual continuous input flight across -64/1024/1640/2150; cold evidence is separate");
        }catch(Throwable e){finish(c,"FAIL","render capture: "+e);}
    }
    private static void finish(MinecraftClient c,String status,String reason){
        if(done)return;done=true;c.options.forwardKey.setPressed(false);if(DescentKeybinds.alwaysRun!=null)DescentKeybinds.alwaysRun.setPressed(false);
        try{
            var out=new LinkedHashMap<String,Object>();out.put("status",status);out.put("reason",reason);out.put("initialTeleports",initialTeleports);out.put("measuredGameMode",SURVIVAL?"survival":"creative");out.put("preAdmissionClientTicks",armClientTick);out.put("measuredClientTicks",measuredTicks);out.put("armServerTick",armServerTick);out.put("materialWrites",materialWrites);out.put("initialSpectatorStagingOnly",true);out.put("surfaceY",surfaceY);out.put("authoredThroughY",authorTop);out.put("untouchedAboveY",authorTop+1);out.put("untouchedAirSamples",untouchedAirSamples);out.put("observerTickOrSolverCalls",0);out.put("coldReaderInThisRun",false);
            synchronized(SERVER_CROSSES){out.put("serverCrossings",new LinkedHashMap<>(SERVER_CROSSES));}out.put("clientCrossings",CLIENT_CROSSES);synchronized(SERVER){out.put("server",List.copyOf(SERVER));}out.put("client",CLIENT);
            synchronized(CLIENT_MOVES){out.put("actualClientMoves",List.copyOf(CLIENT_MOVES));}synchronized(SERVER_MOVES){out.put("actualServerMoves",List.copyOf(SERVER_MOVES));}out.put("unmatchedEndpointRows",pendingEndpointRows);out.put("movementFailure",movementFailure);out.put("failedAvailability",failedAvailability);
            out.put("afterburnerHeldTicks",afterburnerHeldTicks);out.put("afterburnerPolicy","ordinary R only above authored shaft+4, max800ticks, no energy refill; 180s overallwall unchanged;2400pre-admission and2400measuredticks start at real input arm");
            Files.writeString(c.runDirectory.toPath().resolve("vertical-flight-outcome.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out)+"\n");System.out.println("VERTICAL_FLIGHT_OUTCOME "+status+" "+reason);
        }catch(Exception e){e.printStackTrace();}c.scheduleStop();
    }
}
