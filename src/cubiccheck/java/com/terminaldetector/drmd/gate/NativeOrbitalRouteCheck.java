package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.client.input.DescentKeybinds;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.contraption.BodyVolumeLeases;
import com.terminaldetector.drmd.world.contraption.BodyVolumePlan;
import com.terminaldetector.drmd.world.fixture.city.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import java.nio.file.Files;
import java.util.*;

/** Actual client/normal controls. No movement integrator, direct travel or post-staging pose writes. */
public final class NativeOrbitalRouteCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.orbitalRoute");
    private static final CityBlueprint.Point ANCHOR=new CityBlueprint.Point(256,1408,256);
    private record Cover(BodyVolumePlan.Column column,Object owner,boolean forcedBefore){}
    private record MoveStart(String side,Vec3d position,Vec3d requested,int tick){}
    private record Move(int serial,String side,List<Double> before,List<Double> after,List<Double> requested,int tick){}
    private static final List<Cover> COVER=new ArrayList<>();
    private static final List<D6ChunkPos> HALO=new ArrayList<>();
    private static final List<Map<String,Object>> EVENTS=new ArrayList<>(),STATE_ROWS=new ArrayList<>();
    private static final List<Move> CLIENT_MOVES=new ArrayList<>(),SERVER_MOVES=new ArrayList<>();
    private static final ThreadLocal<Deque<MoveStart>> MOVING=ThreadLocal.withInitial(ArrayDeque::new);
    private static CityBlueprint.Plan plan;private static CityRuntimeSession session;private static OrbitalRouteMemory.Context routeContext;
    private static UUID playerId;private static ServerWorld heldWorld;
    private static volatile int phase,passed,verified,requested,readyCubes,maxCursorDelta,maxCasDelta;
    private static volatile boolean done,armed;private static volatile String failure,pendingFrame,blockedCube;
    private static long boot,armedAt;private static int ticks,flightTicks,frames,initialTeleports,heldTicks,serverSamples,lastServerTick=-1;
    private static long cursor,cas;private static Vec3d previousServer,previousClient,lastClientMoveEnd;
    private static net.minecraft.server.OperatorEntry operatorBefore,assignedOperator;
    private static com.mojang.authlib.GameProfile authorProfile;
    private static boolean operatorProvisioned,operatorRestored;
    private static int permissionBefore=-1,permissionAfter=-1;
    private static double travelled,maxStep;private static int matchedServer=-1,matchedClient=-1;
    private NativeOrbitalRouteCheck(){}
    public static void register(){
        boot=System.nanoTime();ServerTickEvents.END_SERVER_TICK.register(NativeOrbitalRouteCheck::server);
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{try{release();}finally{restoreOperator(server);}});ClientTickEvents.END_CLIENT_TICK.register(NativeOrbitalRouteCheck::client);
    }
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    private static List<Double> xyz(Vec3d v){return List.of(v.x,v.y,v.z);}
    private static Vec3d gate(int index){var p=OrbitalRouteBlueprint.gates(ANCHOR).get(index).position();return new Vec3d(p.x()+.5,p.y(),p.z()+.5);}
    private static double minimumPath(){double length=0;for(int i=1;i<9;i++)length+=gate(i).distanceTo(gate(i-1));return length-3*8;}
    private static void command(ServerPlayerEntity p,String value){p.getServerWorld().getServer().getCommandManager().executeWithPrefix(p.getCommandSource(),value);}
    private static void event(String name,ServerPlayerEntity p){
        var row=new LinkedHashMap<String,Object>();row.put("event",name);row.put("worldTime",p.getWorld().getTime());row.put("serverTick",p.getServerWorld().getServer().getTicks());row.put("p",xyz(p.getPos()));row.put("phase",phase);row.put("passed",passed);row.put("verifiedCells",verified);row.put("requestedColumns",requested);row.put("fullHaloCubes",readyCubes);row.put("planHash",plan==null?"":plan.hash());
        synchronized(EVENTS){require(EVENTS.size()<64,"native orbital event cap64");EVENTS.add(row);}System.out.println("NATIVE_ORBITAL_ROUTE="+new com.google.gson.Gson().toJson(row));
    }
    /** Test-owned real authority list, never a permission-method override or forged command source. */
    private static void admitAuthor(ServerPlayerEntity p){
        var manager=p.getServerWorld().getServer().getPlayerManager();
        require(ENABLED&&playerId!=null&&playerId.equals(p.getUuid())&&manager.getPlayer(playerId)==p
            &&p.networkHandler!=null,"only exact connected native pilot may receive fixture author rights");
        require(!operatorProvisioned,"fixture author grant occurs once");
        authorProfile=p.getGameProfile();operatorBefore=manager.getOpList().get(authorProfile);permissionBefore=permission(p);
        assignedOperator=new net.minecraft.server.OperatorEntry(p.getGameProfile(),2,false);operatorProvisioned=true;
        manager.getOpList().add(assignedOperator);manager.sendCommandTree(p);permissionAfter=permission(p);
        require(manager.getOpList().get(p.getGameProfile())==assignedOperator&&assignedOperator.getPermissionLevel()==2
            &&p.hasPermissionLevel(2),"real native OperatorEntry2 must satisfy original author permission");
        event("ACTUAL_CONNECTED_AUTHOR_OP2_ADMITTED",p);
    }
    private static int permission(ServerPlayerEntity p){int level=0;for(int next=1;next<=4;next++)if(p.hasPermissionLevel(next))level=next;return level;}
    private static void restoreOperator(MinecraftServer server){
        if(!operatorProvisioned||operatorRestored)return;
        var manager=server.getPlayerManager();var p=manager.getPlayer(playerId);
        var profile=authorProfile;
        boolean owned=manager.getOpList().get(profile)==assignedOperator;
        if(owned){manager.getOpList().remove(profile);if(operatorBefore!=null)manager.getOpList().add(operatorBefore);operatorRestored=true;}
        var receipt=new LinkedHashMap<String,Object>();receipt.put("owner",playerId.toString());receipt.put("permissionBefore",permissionBefore);
        receipt.put("permissionAfterAdmission",permissionAfter);receipt.put("assignedEntryLevel",assignedOperator.getPermissionLevel());
        receipt.put("previousEntryLevel",operatorBefore==null?null:operatorBefore.getPermissionLevel());receipt.put("ownedAssignedEntryAtCleanup",owned);
        receipt.put("restored",operatorRestored);receipt.put("currentEntryMatchesOriginal",manager.getOpList().get(profile)==operatorBefore);
        receipt.put("playerStillConnected",p!=null&&p.networkHandler!=null);
        try{Files.writeString(MinecraftClient.getInstance().runDirectory.toPath().resolve("orbital-route-operator-cleanup.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(receipt));}
        catch(Exception problem){problem.printStackTrace();}
        System.out.println("NATIVE_ORBITAL_ROUTE_OPERATOR_CLEANUP="+new com.google.gson.Gson().toJson(receipt));
    }
    private static void prepare(ServerPlayerEntity p){
        heldWorld=p.getServerWorld();require(WorldAccess.of(heldWorld).backend()==CubicAvailability.Backend.NATIVE_CUBIC,"actual native cubic backend required, no column fallback");
        FlightSystem.disable(p,DescentPlayerData.get(p));p.changeGameMode(GameMode.CREATIVE);admitAuthor(p);require(p.hasPermissionLevel(2),"real connected Creative author already needs permission2");
        var context=CityWorldStorage.capture(p);CityWorldStorage.requireHaloEnvelope(context,ANCHOR);plan=OrbitalRouteBlueprint.create(2,ANCHOR,context.bounds());
        var cubes=new TreeSet<D6ChunkPos>(Comparator.comparingInt(D6ChunkPos::x).thenComparingInt(D6ChunkPos::z).thenComparingInt(D6ChunkPos::y));
        for(var cell:plan.cells()){var q=D6ChunkPos.ofBlock(cell.position().x(),cell.position().y(),cell.position().z());for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)cubes.add(new D6ChunkPos(q.x()+x,q.y()+y,q.z()+z));}
        require(cubes.size()<=196*8,"bounded native plan/halo cube budget");HALO.addAll(cubes);var columns=new LinkedHashMap<Long,int[]>();
        for(var q:cubes){long key=ChunkPos.toLong(q.x(),q.z());var range=columns.computeIfAbsent(key,k->new int[]{q.x(),q.z(),q.y(),q.y()});range[2]=Math.min(range[2],q.y());range[3]=Math.max(range[3],q.y());}
        require(columns.size()<=196,"native authored footprint196column cap");for(var r:columns.values()){require(r[3]-r[2]<8,"native authored range8cube cap");COVER.add(new Cover(new BodyVolumePlan.Column(r[0],r[1],r[2],r[3]),new Object(),heldWorld.getForcedChunks().contains(ChunkPos.toLong(r[0],r[1]))));}
        p.getAbilities().flying=true;p.sendAbilitiesUpdate();var start=gate(0);p.teleport(heldWorld,start.x,start.y,start.z,Set.of(),0,0);initialTeleports++;
        command(p,"ungravitycity planorbital 2 "+ANCHOR.x()+" "+ANCHOR.y()+" "+ANCHOR.z());session=CityAuthoringSystem.active(p).orElseThrow();phase=1;event("EXPLICIT_INERT_PLAN_AND_SINGLE_INITIAL_STAGING",p);
    }
    private static void server(MinecraftServer server){
        if(done||playerId==null||failure!=null)return;
        try{
            var p=server.getPlayerManager().getPlayer(playerId);if(p==null)return;
            if(phase==0){prepare(p);return;}
            require(p.getWorld()==heldWorld&&p.isAlive(),"same real alive owner/world required");
            // Test-only native demand, never production authoring fallback or unbounded synchronous load.
            for(int count=0;requested<COVER.size()&&count<4;count++){
                var next=COVER.get(requested);require(BodyVolumeLeases.requestNativeInterest(heldWorld,next.owner(),next.column()),"native authored test interest refused");
                if(!next.forcedBefore())heldWorld.setChunkForced(next.column().x(),next.column().z(),true);requested++;
            }
            var status=session.inspect();require(status.state()!=CityRuntimeSession.State.FAILED,"actual authoring failed: "+status.detail());
            if(phase==1){
                int count=0;var access=WorldAccess.of(heldWorld);while(readyCubes<HALO.size()&&count++<64){if(!access.isVolumeAvailable(HALO.get(readyCubes))){blockedCube=HALO.get(readyCubes)+":"+access.cubeState(HALO.get(readyCubes));return;}readyCubes++;}
                if(requested<COVER.size()||readyCubes<HALO.size()||status.state()!=CityRuntimeSession.State.READY)return;
                require(status.manifest().orElseThrow().kind()==CityManifest.Kind.ORBITAL_PILOT,"real inert plan has original typed template");command(p,"ungravitycity apply");phase=2;event("ACTUAL_NATIVE_FULL_HALO_AND_APPLY",p);return;
            }
            if(phase==2){
                var job=status.job().orElseThrow();long actualCursor=job.cursor(),actualCas=status.storage().orElseThrow().casCalls();maxCursorDelta=Math.max(maxCursorDelta,(int)(actualCursor-cursor));maxCasDelta=Math.max(maxCasDelta,(int)(actualCas-cas));
                require(actualCursor>=cursor&&actualCursor-cursor<=64&&actualCas>=cas&&actualCas-cas<=1,"original64cell/oneCAS actualtick bound");cursor=actualCursor;cas=actualCas;
                if(job.state()!=CityAuthoringJob.State.COMPLETED){require(job.state()==CityAuthoringJob.State.RUNNING,"native authoring paused: "+status.detail());return;}
                require(job.cursor()==plan.cells().size()&&job.pending().isEmpty(),"all actual native geometry durably committed");phase=3;event("ALL7331_NATIVE_CELLS_PLACED",p);return;
            }
            if(phase==3){
                var access=WorldAccess.of(heldWorld);int end=Math.min(plan.cells().size(),verified+64);
                while(verified<end){var cell=plan.cells().get(verified);var q=cell.position();var actual=access.readIfLoaded(new BlockPos(q.x(),q.y(),q.z()));require(actual.isPresent()&&!actual.orElseThrow().hasBlockEntity()&&Registries.BLOCK.getId(actual.orElseThrow().getBlock()).toString().equals(cell.state()),"actual loaded native cell differs at "+q);verified++;}
                if(verified<plan.cells().size())return;
                require(p.getPos().distanceTo(gate(0))<.75,"initial staging stays at first clear gate before measurement");routeContext=OrbitalRouteMemory.Context.of(status.manifest().orElseThrow());
                FlightSystem.enableWithoutMount(p,DescentPlayerData.get(p));ModNetworking.syncPlayer(p,DescentPlayerData.get(p));phase=4;event("NATIVE_READBACK_DONE_READY_FOR_REAL_CLIENT_INPUT",p);return;
            }
            if(phase!=4||!armed)return;
            var position=p.getPos();require(!p.isSpectator()&&!p.noClip&&!p.hasVehicle()&&DescentPlayerData.get(p).isEnabled(),"ordinary colliding suit pilot required");require(WorldAccess.of(heldWorld).readIfLoaded(p.getBlockPos()).isPresent(),"actual server pilot entered unavailable native cube");
            require(heldWorld.isSpaceEmpty(p,p.getBoundingBox()),"actual server pilot intersects authored solid material");
            if(previousServer!=null){double step=position.distanceTo(previousServer);require(step<=2,"native server per-tick movement discontinuity>2m");if(step>1e-7)require(matchesMove(position),"server endpoint lacks actual native client/server Entity.move receipt");}previousServer=position;
            require(server.getTicks()!=lastServerTick,"one sample per actual server tick");lastServerTick=server.getTicks();serverSamples++;
            if(serverSamples%20==0)stateRow("server",server.getTicks(),position,DescentPlayerData.get(p).getFlightVelocity(),false);
            var model=OrbitalRouteMemory.of(heldWorld).progress(routeContext,OrbitalRouteBlueprint.gates(ANCHOR));passed=model.state().passed();
            if(passed==9){phase=5;pendingFrame="route-complete";event("ALL_NINE_NATIVE_GATES_COMPLETE_SAVED",p);return;}
            if(position.distanceTo(gate(passed))<=1.15&&DescentPlayerData.get(p).getFlightVelocity().length()<=4){
                command(p,"ungravitycity route");var after=OrbitalRouteMemory.of(heldWorld).progress(routeContext,OrbitalRouteBlueprint.gates(ANCHOR)).state().passed();require(after==passed+1,"ordinary route command advances exactly next actual gate");passed=after;pendingFrame="gate-"+passed;event("ACTUAL_NATIVE_GATE_REACHED",p);
            }
        }catch(Throwable bad){failure=bad.toString();bad.printStackTrace();}
    }
    private static void client(MinecraftClient c){
        if(done)return;ticks++;
        if(failure!=null){finish(c,"FAIL",failure);return;}
        if(System.nanoTime()-boot>420_000_000_000L||ticks>6000){finish(c,"FAIL","420wall/6000totalclienttick native route bound");return;}
        if(!armed&&System.nanoTime()-boot>120_000_000_000L){finish(c,"FAIL","120wall setup/authoring/nativeFULL admission bound");return;}
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget button&&button.getMessage().getString().equals("I know what I'm doing!"))button.onPress();
        if(c.player==null||c.world==null||c.getServer()==null)return;
        DescentClient.markUserFlightChoice();
        if(playerId==null){
            if(!c.runDirectory.toPath().getFileName().toString().equals("cubic-client-automation")||!FabricLoader.getInstance().isModLoaded("bettercubicchunks")){finish(c,"FAIL","actual isolated BCC client required");return;}
            playerId=c.player.getUuid();clearKeys(c);c.setScreen(null);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);return;
        }
        if(phase<4){clearKeys(c);return;}
        if(phase==5){clearKeys(c);return;}
        if(c.currentScreen!=null||!DescentClientState.enabled)return;
        try{
            require(!c.player.isSpectator()&&!c.player.noClip&&!c.player.hasVehicle(),"actual native client uses ordinary colliding suit");
            require(WorldAccess.of(c.world).backend()==CubicAvailability.Backend.NATIVE_CUBIC&&WorldAccess.of(c.world).readIfLoaded(c.player.getBlockPos()).isPresent(),"actual client pilot volume must be nativeFULL");
            require(c.world.isSpaceEmpty(c.player,c.player.getBoundingBox()),"actual client intersects solid authored geometry");
            if(!armed){
                require(c.player.getPos().distanceTo(gate(0))<.75,"initial native teleport/client admission must reach actualfirstgate before measuring");previousClient=c.player.getPos();lastClientMoveEnd=previousClient;armedAt=System.nanoTime();armed=true;
            }
            require(++flightTicks<=3600,"original3600measuredclienttick physical route bound");
            var at=c.player.getPos();double step=at.distanceTo(previousClient);maxStep=Math.max(maxStep,step);travelled+=step;require(step<=2,"actual client per-tick flight discontinuity>2m");
            require(lastClientMoveEnd==null||at.distanceTo(lastClientMoveEnd)<1e-7,"actual client position changed outside genuine Entity.move");previousClient=at;
            if(flightTicks%20==0)stateRow("client",ticks,at,c.player.getVelocity().multiply(20),c.options.forwardKey.isPressed());
            if(passed>=9){clearKeys(c);return;}
            var error=gate(passed).subtract(at);var velocity=c.player.getVelocity().multiply(20);double speed=Math.min(7,error.length()*1.8);var wanted=error.lengthSquared()<1e-12?Vec3d.ZERO:error.normalize().multiply(speed);var correction=wanted.subtract(velocity);
            clearKeys(c);
            if(correction.length()>1.2){
                var direction=correction.normalize();var up=Math.abs(direction.y)>.94?new Vec3d(0,0,1):new Vec3d(0,1,0);
                // Actual client-owned attitude and real keyboard sampling send the normal production packet.
                ShipAttitudeClient.acceptPortalFrame(c.player,direction,up);c.options.forwardKey.setPressed(true);heldTicks++;
            }
        }catch(Throwable bad){failure=bad.toString();bad.printStackTrace();}
    }
    private static void stateRow(String side,int tick,Vec3d position,Vec3d velocity,boolean held){
        synchronized(STATE_ROWS){require(STATE_ROWS.size()<512,"bounded native state journal512");STATE_ROWS.add(Map.of("side",side,"tick",tick,"position",xyz(position),"velocityMps",xyz(velocity),"passed",passed,"heldForward",held));}
    }
    private static void clearKeys(MinecraftClient c){
        c.options.forwardKey.setPressed(false);c.options.backKey.setPressed(false);c.options.leftKey.setPressed(false);c.options.rightKey.setPressed(false);c.options.jumpKey.setPressed(false);c.options.sneakKey.setPressed(false);
        DescentKeybinds.ascend.setPressed(false);DescentKeybinds.descend.setPressed(false);DescentKeybinds.alwaysRun.setPressed(false);DescentKeybinds.rollLeft.setPressed(false);DescentKeybinds.rollRight.setPressed(false);
    }
    private static boolean matchesMove(Vec3d at){
        synchronized(CLIENT_MOVES){for(int i=CLIENT_MOVES.size()-1;i>=Math.max(0,matchedClient);i--){var row=CLIENT_MOVES.get(i);if(equal(at,row.before())||equal(at,row.after())){matchedClient=i;return true;}}}
        synchronized(SERVER_MOVES){for(int i=SERVER_MOVES.size()-1;i>=Math.max(0,matchedServer);i--){var row=SERVER_MOVES.get(i);if(equal(at,row.before())||equal(at,row.after())){matchedServer=i;return true;}}}return false;
    }
    private static boolean equal(Vec3d v,List<Double> p){return v.squaredDistanceTo(new Vec3d(p.get(0),p.get(1),p.get(2)))<1e-14;}
    /** Existing Entity.move HEAD/RETURN probe only observes, never moves or changes a tick. */
    public static void beforeMove(Entity e,MovementType type,Vec3d requestedMove){
        if(!ENABLED||done||!armed||playerId==null||!playerId.equals(e.getUuid()))return;
        var pending=MOVING.get();if(pending.size()>=4){failure="native orbital move nesting cap4";return;}
        String side=e.getWorld().isClient?"client":"server";
        if(side.equals("client")&&lastClientMoveEnd!=null&&e.getPos().distanceTo(lastClientMoveEnd)>1e-7)failure="native client move starts after unobserved position change";
        pending.addLast(new MoveStart(side,e.getPos(),requestedMove,side.equals("client")?ticks:((ServerWorld)e.getWorld()).getServer().getTicks()));
    }
    public static void afterMove(Entity e){
        if(!ENABLED||done||!armed||playerId==null||!playerId.equals(e.getUuid()))return;
        var pending=MOVING.get();if(pending.isEmpty())return;var start=pending.removeLast();var rows=start.side().equals("client")?CLIENT_MOVES:SERVER_MOVES;
        synchronized(rows){if(rows.size()>=16_384){failure="native move journal cap16384 per side";return;}rows.add(new Move(rows.size(),start.side(),xyz(start.position()),xyz(e.getPos()),xyz(start.requested()),start.tick()));}
        if(start.side().equals("client"))lastClientMoveEnd=e.getPos();
    }
    /** Real GameRenderer RETURN only. */
    public static void rendered(){
        if(!ENABLED||done||!armed)return;var c=MinecraftClient.getInstance();if(c.player==null||c.world==null||c.currentScreen!=null)return;
        try{
            if(pendingFrame!=null){String name=pendingFrame;pendingFrame=null;var folder=c.runDirectory.toPath().resolve("orbital-route-frames");Files.createDirectories(folder);try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){image.writeTo(folder.resolve(name+".png"));}}
            if(phase==5&&++frames>=20){require(passed==9&&verified==7331&&travelled>=minimumPath()&&heldTicks>=8&&initialTeleports==1,"full genuine input route criteria");require(!CLIENT_MOVES.isEmpty()&&serverSamples>100,"actual native motion and server authority receipts");finish(c,"PASS","native original nine-gate route via real keyboard/attitude/Entity.move; normal save follows, not a cold reader");}
        }catch(Throwable bad){finish(c,"FAIL",bad.toString());}
    }
    private static void release(){
        if(heldWorld==null)return;for(int i=0;i<requested;i++){var held=COVER.get(i);BodyVolumeLeases.releaseNativeInterest(heldWorld,held.owner());if(!held.forcedBefore())heldWorld.setChunkForced(held.column().x(),held.column().z(),false);}heldWorld=null;
    }
    private static void finish(MinecraftClient c,String status,String reason){
        if(done)return;done=true;if(c.player!=null)clearKeys(c);
        var report=new LinkedHashMap<String,Object>();report.put("status",status);report.put("reason",reason);report.put("blockedCube",blockedCube);report.put("nativeCubic",true);report.put("permissionBefore",permissionBefore);report.put("permissionAfterAdmission",permissionAfter);report.put("operatorEntryLevel",assignedOperator==null?null:assignedOperator.getPermissionLevel());report.put("authorProfile",playerId==null?null:playerId.toString());report.put("coldReader",false);report.put("phase",phase);report.put("passed",passed);report.put("verifiedCells",verified);report.put("requestedColumns",requested);report.put("fullHaloCubes",readyCubes);report.put("initialTeleports",initialTeleports);report.put("heldKeyboardTicks",heldTicks);report.put("measuredClientTicks",flightTicks);report.put("measuredWallNanos",armedAt==0?0:System.nanoTime()-armedAt);report.put("actualPathMetres",travelled);report.put("minimumGeometricPathMetres",minimumPath());report.put("maxClientStep",maxStep);report.put("maxCursorDelta",maxCursorDelta);report.put("maxCasDelta",maxCasDelta);report.put("actualServerSamples",serverSamples);report.put("planHash",plan==null?"":plan.hash());
        synchronized(EVENTS){report.put("events",List.copyOf(EVENTS));}synchronized(STATE_ROWS){report.put("stateRows",List.copyOf(STATE_ROWS));}synchronized(CLIENT_MOVES){report.put("actualClientMoves",List.copyOf(CLIENT_MOVES));}synchronized(SERVER_MOVES){report.put("actualServerMoves",List.copyOf(SERVER_MOVES));}
        try{Files.writeString(c.runDirectory.toPath().resolve("orbital-native-route.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception bad){bad.printStackTrace();}
        System.out.println("NATIVE_ORBITAL_ROUTE_"+status+" "+reason);c.scheduleStop();
    }
}
