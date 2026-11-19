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
public final class NativeOrbitalMissionCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.orbitalMission");
    public static final boolean FIELD_VIEWS=Boolean.getBoolean("drmd.gate.orbitalFieldViews");
    private static volatile int viewLeg=-1,viewServerSamples,viewReadCursor;
    private static volatile boolean viewReady;
    private static long viewStarted,missionCompletedWall;private static int viewSettledSince=-1;private static int viewTicks,viewHeldTicks,viewFlightEnable=-1;
    private static Vec3d viewPrevious,viewServerPrevious;private static double viewPath;
    private static List<CityBlueprint.Cell> viewCells=List.of();
    private static final List<Map<String,Object>> VIEW_ROWS=new ArrayList<>();
    private static CityBlueprint.Point ANCHOR=new CityBlueprint.Point(256,1408,256);
    private static BlockPos herald;private static UUID siteId;private static net.minecraft.item.ItemStack tool;
    private static volatile int missionLeg,heraldUses,mined,observedRep,actualPermission;private static long mineStart;private static int flightRequestLeg=-1,footRequestLeg=-1;private static int initialUses,claimUses,firstClaimUse=-1;private static String claimedReceipt;private static Vec3d initialPose;
    private static final String CONTRACT="drmd:orbital_field_salvage";
    private record Cover(BodyVolumePlan.Column column,Object owner,boolean forcedBefore){}
    private record MoveStart(String side,Vec3d position,Vec3d requested,int tick){}
    private record Move(int serial,String side,List<Double> before,List<Double> after,List<Double> requested,int tick){}
    private static final List<Cover> COVER=new ArrayList<>();
    private static final List<D6ChunkPos> HALO=new ArrayList<>();
    private static final List<Map<String,Object>> EVENTS=new ArrayList<>(),STATE_ROWS=new ArrayList<>();
    private static final ArrayDeque<Map<String,Object>> MINE_CLIENT_STATE=new ArrayDeque<>();
    private static final List<Move> CLIENT_MOVES=new ArrayList<>(),SERVER_MOVES=new ArrayList<>();
    private static final ThreadLocal<Deque<MoveStart>> MOVING=ThreadLocal.withInitial(ArrayDeque::new);
    private static CityBlueprint.Plan plan;private static CityRuntimeSession session;private static OrbitalRouteMemory.Context routeContext;
    private static UUID playerId;private static ServerWorld heldWorld;
    private static volatile int phase,passed,verified,requested,readyCubes,maxCursorDelta,maxCasDelta;
    private static volatile boolean done,armed;private static volatile String failure,pendingFrame,blockedCube;
    private static long boot,armedAt;private static int ticks,flightTicks,frames,initialTeleports,heldTicks,serverSamples,lastServerTick=-1;
    private static int heraldDepartureWaypoints,miningAttackEdges,miningAimTicks;
    private static long cursor,cas;private static Vec3d previousServer,previousClient,lastClientMoveEnd;
    private static net.minecraft.server.OperatorEntry operatorBefore,assignedOperator;
    private static com.mojang.authlib.GameProfile authorProfile;
    private static boolean operatorProvisioned,operatorRestored;
    private static int permissionBefore=-1,permissionAfter=-1;
    private static double travelled,maxStep;private static int matchedServer=-1,matchedClient=-1;
    private NativeOrbitalMissionCheck(){}
    public static void register(){
        boot=System.nanoTime();
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(!world.isClient&&playerId!=null&&playerId.equals(player.getUuid())&&herald!=null&&herald.equals(hit.getBlockPos())&&hand==net.minecraft.util.Hand.MAIN_HAND)heraldUses++;
            return net.minecraft.util.ActionResult.PASS;
        });
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((world,player,pos,state,entity)->{
            if(playerId!=null&&playerId.equals(player.getUuid())&&state.isOf(net.minecraft.block.Blocks.STONE)&&(pos.equals(panel(0))||pos.equals(panel(1))))mined++;
        });
        ServerTickEvents.END_SERVER_TICK.register(NativeOrbitalMissionCheck::server);
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{try{release();}finally{restoreOperator(server);}});ClientTickEvents.END_CLIENT_TICK.register(NativeOrbitalMissionCheck::client);
    }
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    private static List<Double> xyz(Vec3d v){return List.of(v.x,v.y,v.z);}
    private static Vec3d home(){return new Vec3d(herald.getX()+.5,herald.getY(),herald.getZ()-2+.5);}
    private static BlockPos panel(int index){return index==0?new BlockPos(ANCHOR.x()-51,ANCHOR.y()+25,ANCHOR.z()+24):new BlockPos(ANCHOR.x()+39,ANCHOR.y()+35,ANCHOR.z()-36);}
    private static Vec3d stance(int index){var p=panel(index);return new Vec3d(p.getX()+.5,p.getY()+1,p.getZ()-2+.5);}
    private static Vec3d goal(){return switch(missionLeg){case 0,6,7->home();case 1->new Vec3d(ANCHOR.x()+.5,ANCHOR.y()+6,ANCHOR.z()-10+.5);case 2,3->stance(0);case 4,5->stance(1);default->home();};}
    private static double minimumPath(){return 180;}
    private static void command(ServerPlayerEntity p,String value){p.getServerWorld().getServer().getCommandManager().executeWithPrefix(p.getCommandSource(),value);}
    private static void event(String name,ServerPlayerEntity p){
        var row=new LinkedHashMap<String,Object>();row.put("event",name);row.put("worldTime",p.getWorld().getTime());row.put("serverTick",p.getServerWorld().getServer().getTicks());row.put("p",xyz(p.getPos()));row.put("phase",phase);row.put("passed",passed);row.put("verifiedCells",verified);row.put("requestedColumns",requested);row.put("fullHaloCubes",readyCubes);row.put("planHash",plan==null?"":plan.hash());
        synchronized(EVENTS){require(EVENTS.size()<64,"native orbital event cap64");EVENTS.add(row);}System.out.println("NATIVE_ORBITAL_MISSION="+new com.google.gson.Gson().toJson(row));
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
        try{Files.writeString(MinecraftClient.getInstance().runDirectory.toPath().resolve("orbital-mission-operator-cleanup.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(receipt));}
        catch(Exception problem){problem.printStackTrace();}
        System.out.println("NATIVE_ORBITAL_MISSION_OPERATOR_CLEANUP="+new com.google.gson.Gson().toJson(receipt));
    }
    private static void prepare(ServerPlayerEntity p){
        heldWorld=p.getServerWorld();require(WorldAccess.of(heldWorld).backend()==CubicAvailability.Backend.NATIVE_CUBIC,"actual native cubic backend required, no column fallback");
        FlightSystem.disable(p,DescentPlayerData.get(p));p.changeGameMode(GameMode.CREATIVE);admitAuthor(p);require(p.hasPermissionLevel(2),"real connected Creative author already needs permission2");
        int cell=com.terminaldetector.drmd.world.enclave.EnclaveSite.CELL;BlockPos selected=null;
        for(int x=-5;x<=5&&selected==null;x++)for(int z=-5;z<=5;z++){
            var candidate=new BlockPos((x+2)*cell+40,1413,(z+2)*cell+40);
            if(com.terminaldetector.drmd.world.enclave.EnclaveSite.generate(heldWorld.getSeed(),candidate).origin==com.terminaldetector.drmd.world.enclave.EnclaveOrigin.MINERS){selected=candidate;break;}
        }
        require(selected!=null,"bounded121metadata search finds genuine MINERS origin, never rewrites faction/seed");herald=selected;ANCHOR=new CityBlueprint.Point(selected.getX(),selected.getY()-5,selected.getZ()+14);
        var context=CityWorldStorage.capture(p);CityWorldStorage.requireHaloEnvelope(context,ANCHOR);plan=OrbitalFieldBlueprint.create(2,ANCHOR,context.bounds());
        var cubes=new TreeSet<D6ChunkPos>(Comparator.comparingInt(D6ChunkPos::x).thenComparingInt(D6ChunkPos::z).thenComparingInt(D6ChunkPos::y));
        for(var plannedCell:plan.cells()){var q=D6ChunkPos.ofBlock(plannedCell.position().x(),plannedCell.position().y(),plannedCell.position().z());for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)cubes.add(new D6ChunkPos(q.x()+x,q.y()+y,q.z()+z));}
        require(cubes.size()<=196*8,"bounded native plan/halo cube budget");HALO.addAll(cubes);var columns=new LinkedHashMap<Long,int[]>();
        for(var q:cubes){long key=ChunkPos.toLong(q.x(),q.z());var range=columns.computeIfAbsent(key,k->new int[]{q.x(),q.z(),q.y(),q.y()});range[2]=Math.min(range[2],q.y());range[3]=Math.max(range[3],q.y());}
        require(columns.size()<=196,"native authored footprint196column cap");for(var r:columns.values()){require(r[3]-r[2]<8,"native authored range8cube cap");COVER.add(new Cover(new BodyVolumePlan.Column(r[0],r[1],r[2],r[3]),new Object(),heldWorld.getForcedChunks().contains(ChunkPos.toLong(r[0],r[1]))));}
        p.getAbilities().flying=true;p.sendAbilitiesUpdate();var start=home();p.teleport(heldWorld,start.x,start.y,start.z,Set.of(),0,0);initialTeleports++;
        command(p,"ungravitycity planfield 2 "+ANCHOR.x()+" "+ANCHOR.y()+" "+ANCHOR.z());session=CityAuthoringSystem.active(p).orElseThrow();phase=1;event("EXPLICIT_INERT_PLAN_AND_SINGLE_INITIAL_STAGING",p);
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
                require(status.manifest().orElseThrow().kind()==CityManifest.Kind.ORBITAL_FIELD,"real inert plan has original typed template");command(p,"ungravitycity apply");phase=2;event("ACTUAL_NATIVE_FULL_HALO_AND_APPLY",p);return;
            }
            if(phase==2){
                var job=status.job().orElseThrow();long actualCursor=job.cursor(),actualCas=status.storage().orElseThrow().casCalls();maxCursorDelta=Math.max(maxCursorDelta,(int)(actualCursor-cursor));maxCasDelta=Math.max(maxCasDelta,(int)(actualCas-cas));
                require(actualCursor>=cursor&&actualCursor-cursor<=64&&actualCas>=cas&&actualCas-cas<=1,"original64cell/oneCAS actualtick bound");cursor=actualCursor;cas=actualCas;
                if(job.state()!=CityAuthoringJob.State.COMPLETED){require(job.state()==CityAuthoringJob.State.RUNNING,"native authoring paused: "+status.detail());return;}
                require(job.cursor()==plan.cells().size()&&job.pending().isEmpty(),"all actual native geometry durably committed");phase=3;event("ALL11712_NATIVE_FIELD_CELLS_PLACED",p);return;
            }
            if(phase==3){
                var access=WorldAccess.of(heldWorld);int end=Math.min(plan.cells().size(),verified+64);
                while(verified<end){var cell=plan.cells().get(verified);var q=cell.position();var actual=access.readIfLoaded(new BlockPos(q.x(),q.y(),q.z()));require(actual.isPresent()&&!actual.orElseThrow().hasBlockEntity()&&Registries.BLOCK.getId(actual.orElseThrow().getBlock()).toString().equals(cell.state()),"actual loaded native cell differs at "+q);verified++;}
                if(verified<plan.cells().size())return;
                if(com.terminaldetector.drmd.world.planet.AuthoredOrbitalCatalogue.visible(heldWorld).size()!=3)return;
                require(p.getPos().distanceTo(home())<.75,"initial native staging stays at original contact");beginMission(p);phase=4;event("NATIVE_FIELD_READBACK_CERT3_AND_SURVIVAL_CONTACT_READY",p);return;
            }
            if(phase==6){serverFieldView(p,server);return;}
            if(phase!=4||!armed)return;
            var position=p.getPos();require(!p.isCreative()&&!p.isSpectator()&&!p.noClip&&!p.hasVehicle(),"ordinary colliding Survival actor required");require(WorldAccess.of(heldWorld).readIfLoaded(p.getBlockPos()).isPresent(),"actual server pilot entered unavailable native cube");
            require(heldWorld.isSpaceEmpty(p,p.getBoundingBox()),"actual server pilot intersects authored solid material");
            if(previousServer!=null){double step=position.distanceTo(previousServer);require(step<=2,"native server per-tick movement discontinuity>2m");if(step>1e-7)require(matchesMove(position),"server endpoint lacks actual native client/server Entity.move receipt");}previousServer=position;
            require(server.getTicks()!=lastServerTick,"one sample per actual server tick");lastServerTick=server.getTicks();serverSamples++;
            if(serverSamples%20==0)stateRow("server",server.getTicks(),position,DescentPlayerData.get(p).getFlightVelocity(),false);
            passed=cursor();observedRep=rep();actualPermission=p.hasPermissionLevel(2)?2:0;require(rep()==0||missionLeg==7&&rep()==3,"no early mission reputation grant");
            if(missionLeg==0){
                if(heraldUses<3)return;expect(0,0,"three actual early normal contact interactions cannot finish or grant");initialUses=heraldUses;missionLeg=1;event("REAL_NATIVE_ACCEPTS_CURSOR0_REP0",p);return;
            }
            if(missionLeg==1&&near(p)){expect(1,0,"actual station outside-to-inside event");missionLeg=2;pendingFrame="station-entry";event("REAL_NATIVE_STATION_ENTRY",p);return;}
            if(missionLeg==2&&near(p)){expect(2,0,"actual inner approach event");missionLeg=3;mineStart=server.getTicks();event("REAL_NATIVE_INNER_APPROACH",p);return;}
            if(missionLeg==3){require(server.getTicks()-mineStart<=200,"real native Survival stone mining200tick bound");if(cursor()==3){require(mined==1&&WorldAccess.of(heldWorld).readIfLoaded(panel(0)).orElseThrow().isAir(),"actual first AFTERbreak+AIR, never injected event");missionLeg=4;event("REAL_NATIVE_INNER_STONE_MINED",p);}return;}
            if(missionLeg==4&&near(p)){expect(4,0,"actual outer approach event");missionLeg=5;mineStart=server.getTicks();event("REAL_NATIVE_OUTER_APPROACH",p);return;}
            if(missionLeg==5){require(server.getTicks()-mineStart<=200,"second real native Survival stone mining200tick bound");if(cursor()==5){require(mined==2&&WorldAccess.of(heldWorld).readIfLoaded(panel(1)).orElseThrow().isAir(),"actual second AFTERbreak+AIR, never injected event");missionLeg=6;pendingFrame="both-rocks-mined";event("REAL_NATIVE_READY_NO_REWARD",p);}return;}
            if(missionLeg==6&&near(p)){
                if(heraldUses<initialUses+1)return;expect(5,0,"ordinary status use after genuine flightback cannot claim");missionLeg=7;claimUses=heraldUses;event("REAL_NATIVE_RETURN_INSPECT_NO_REWARD",p);return;
            }
            if(missionLeg==7){
                if(rep()==0)return;expect(5,3,"actual ordinary extraction exactlyonce+3");
                if(firstClaimUse<0){firstClaimUse=heraldUses;claimedReceipt=instance().toString();}
                if(heraldUses<firstClaimUse+3)return;require(instance().toString().equals(claimedReceipt)&&instance().getString("status").equals("CLAIMED"),"repeated actual use cannot reset claimed receipt");require(mined==2&&tool.getDamage()==2,"same original tool has exactlyboth real uses, no repair/replacement");require(com.terminaldetector.drmd.world.planet.AuthoredOrbitalCatalogue.visible(heldWorld).size()==1,"actual mined rocks do not retain pristine far proxies");phase=5;pendingFrame="mission-claimed-once";event("REAL_NATIVE_MISSION_CLAIMED_ONCE_AFTER_REPEATS",p);
            }
        }catch(Throwable bad){failure=bad.toString();bad.printStackTrace();}
    }
    private static net.minecraft.nbt.NbtCompound data() {
        net.minecraft.world.PersistentState memory = com.terminaldetector.drmd.world.enclave.FactionMemory.of(heldWorld);
        return memory.writeNbt(new net.minecraft.nbt.NbtCompound(), heldWorld.getRegistryManager());
    }
    private static net.minecraft.nbt.NbtCompound instance(){var list=data().getCompound("missions").getList("instances",net.minecraft.nbt.NbtElement.COMPOUND_TYPE);for(int i=0;i<list.size();i++){var n=list.getCompound(i);if(n.getUuid("owner").equals(playerId)&&n.getUuid("site").equals(siteId))return n;}return new net.minecraft.nbt.NbtCompound();}
    private static int cursor(){var n=instance();return n.isEmpty()?-1:n.getInt("cursor");}
    private static int rep(){return com.terminaldetector.drmd.world.enclave.FactionMemory.of(heldWorld).getRep(playerId,com.terminaldetector.drmd.world.enclave.EnclaveOrigin.MINERS);}
    private static void expect(int cursor,int rep,String reason){require(cursor()==cursor&&rep()==rep,reason+" actual="+instance()+" rep="+rep());}
    private static boolean near(ServerPlayerEntity p){
        double speedLimit=missionLeg==2||missionLeg==4?.8:3;
        return p.getPos().distanceTo(goal())<=.8&&DescentPlayerData.get(p).getFlightVelocity().length()<=speedLimit;
    }
    private static String xyz(BlockPos p){return p.getX()+" "+p.getY()+" "+p.getZ();}
    private static void beginMission(ServerPlayerEntity p){
        require(com.terminaldetector.drmd.world.enclave.EnclaveMissionCatalog.find(CONTRACT).isPresent(),"real additive resource loaded by normal catalog");
        for(var at:List.of(herald,herald.down(),herald.north(2).down()))require(WorldAccess.of(heldWorld).readIfLoaded(at).filter(old->old.isAir()&&!old.hasBlockEntity()).isPresent(),"explicit contact fixture refuses foreign/unavailable material");
        heldWorld.setBlockState(herald.down(),net.minecraft.block.Blocks.IRON_BLOCK.getDefaultState(),2);heldWorld.setBlockState(herald.north(2).down(),net.minecraft.block.Blocks.IRON_BLOCK.getDefaultState(),2);heldWorld.setBlockState(herald,com.terminaldetector.drmd.entity.ModWorldBlocks.ENCLAVE_HERALD.getDefaultState(),2);
        command(p,"ungravitymission bind "+CONTRACT+" "+xyz(herald)+" "+ANCHOR.x()+" "+ANCHOR.y()+" "+ANCHOR.z());
        var sites=data().getCompound("missions").getList("sites",net.minecraft.nbt.NbtElement.COMPOUND_TYPE);for(int i=0;i<sites.size();i++){var n=sites.getCompound(i);var h=n.getCompound("herald");if(h.getInt("x")==herald.getX()&&h.getInt("y")==herald.getY()&&h.getInt("z")==herald.getZ())siteId=n.getUuid("id");}
        require(siteId!=null&&rep()==0,"ordinary author bind created actual site without reward");
        // Same genuine singleplayer peer changes ordinary game mode. Its real permission level is recorded, not forged.
        p.changeGameMode(GameMode.SURVIVAL);tool=new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_PICKAXE);p.getInventory().setStack(1,tool);p.getInventory().setStack(0,net.minecraft.item.ItemStack.EMPTY);p.getInventory().setStack(2,net.minecraft.item.ItemStack.EMPTY);p.setStackInHand(net.minecraft.util.Hand.OFF_HAND,net.minecraft.item.ItemStack.EMPTY);p.getInventory().markDirty();p.currentScreenHandler.sendContentUpdates();
        require(!com.terminaldetector.drmd.inventory.DroneArsenal.supported(tool),"normal held pickaxe retains real vanilla mining/Herald input while6DoF");ModNetworking.syncPlayer(p,DescentPlayerData.get(p));
    }
    private static void client(MinecraftClient c){
        if(done)return;ticks++;
        if((missionLeg==3||missionLeg==5)&&ticks%8==0)observeMiningClient(c);
        if(failure!=null){finish(c,"FAIL",failure);return;}
        boolean fieldViewStage=FIELD_VIEWS&&phase>=6;
        if(!fieldViewStage&&(System.nanoTime()-boot>420_000_000_000L||ticks>6000)){finish(c,"FAIL","420wall/6000totalclienttick native route bound");return;}
        if(fieldViewStage&&(System.nanoTime()-viewStarted>420_000_000_000L||viewTicks>8000)){finish(c,"FAIL","separate post-success field-view420wall/8000clienttick bound");return;}
        if(!armed&&System.nanoTime()-boot>120_000_000_000L){finish(c,"FAIL","120wall setup/authoring/nativeFULL admission bound");return;}
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget button&&button.getMessage().getString().equals("I know what I'm doing!"))button.onPress();
        if(c.player==null||c.world==null||c.getServer()==null)return;
        DescentClient.markUserFlightChoice();
        if(playerId==null){
            if(!c.runDirectory.toPath().getFileName().toString().equals("cubic-client-automation")||!FabricLoader.getInstance().isModLoaded("bettercubicchunks")){finish(c,"FAIL","actual isolated BCC client required");return;}
            playerId=c.player.getUuid();clearKeys(c);c.setScreen(null);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);return;
        }
        if(phase<4){clearKeys(c);return;}
        if(phase==6){clientFieldView(c);return;}
        if(phase==5||phase==7){clearKeys(c);return;}
        if(c.currentScreen!=null||c.player.isCreative())return;
        try{
            require(!c.player.isSpectator()&&!c.player.noClip&&!c.player.hasVehicle(),"actual native client uses ordinary colliding suit");
            require(WorldAccess.of(c.world).backend()==CubicAvailability.Backend.NATIVE_CUBIC&&WorldAccess.of(c.world).readIfLoaded(c.player.getBlockPos()).isPresent(),"actual client pilot volume must be nativeFULL");
            require(c.world.isSpaceEmpty(c.player,c.player.getBoundingBox()),"actual client intersects solid authored geometry");
            if(!armed){
                require(c.player.getPos().distanceTo(home())<.75,"initial native teleport/client admission must reach actualfirstgate before measuring");previousClient=c.player.getPos();lastClientMoveEnd=previousClient;armedAt=System.nanoTime();armed=true;
            }
            require(++flightTicks<=3600,"original3600measuredclienttick physical route bound");
            var at=c.player.getPos();double step=at.distanceTo(previousClient);maxStep=Math.max(maxStep,step);travelled+=step;require(step<=2,"actual client per-tick flight discontinuity>2m");
            require(lastClientMoveEnd==null||at.distanceTo(lastClientMoveEnd)<1e-7,"actual client position changed outside genuine Entity.move");previousClient=at;
            if(flightTicks%20==0)stateRow("client",ticks,at,c.player.getVelocity().multiply(20),c.options.forwardKey.isPressed());
            if(missionLeg==0||missionLeg==7||missionLeg==6&&at.distanceTo(home())<.65){
                clearKeys(c);
                if(DescentClientState.enabled){if(footRequestLeg!=missionLeg){net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new ModNetworking.ActionPayload("toggle"));footRequestLeg=missionLeg;}return;}
                // Empty hands + ordinary H-off avoid both 6DoF combat RMB ownership and vanilla sneak-item bypass.
                int empty=-1;for(int slot=0;slot<9;slot++)if(c.player.getInventory().getStack(slot).isEmpty()){empty=slot;break;}require(empty>=0,"real actor retains an empty contact slot without deleting collected loot");c.player.getInventory().selectedSlot=empty;if(!c.player.getMainHandStack().isEmpty()||!c.player.getOffHandStack().isEmpty())return;
                aim(c,Vec3d.ofCenter(herald).add(0,0,-.5));c.options.sneakKey.setPressed(missionLeg!=6);
                if(c.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit&&hit.getBlockPos().equals(herald))c.options.useKey.setPressed(true);return;
            }
            if(!DescentClientState.enabled){clearKeys(c);if(flightRequestLeg!=missionLeg){net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new ModNetworking.ActionPayload("enable"));flightRequestLeg=missionLeg;}return;}
            c.player.getInventory().selectedSlot=1;if(!c.player.getMainHandStack().isOf(net.minecraft.item.Items.DIAMOND_PICKAXE))return;
            if(missionLeg==3||missionLeg==5){
                var target=panel(missionLeg==3?0:1);
                boolean alreadyPressed=c.options.attackKey.isPressed();
                clearKeys(c);
                var velocity=c.player.getVelocity().multiply(20);
                if(at.distanceTo(goal())>1.1||velocity.length()>1.2){steer(c,at,goal(),.4);return;}
                aim(c,Vec3d.ofCenter(target).add(0,0,-.375));miningAimTicks++;
                if(c.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit&&hit.getBlockPos().equals(target)){
                    if(!alreadyPressed){
                        var key=net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(c.options.attackKey);
                        net.minecraft.client.option.KeyBinding.onKeyPressed(key);miningAttackEdges++;
                    }
                    c.options.attackKey.setPressed(true);
                }
                return;
            }
            clearKeys(c);
            steer(c,at,steeringGoal(at),missionLeg==2||missionLeg==4?.6:1.2);
        }catch(Throwable bad){failure=bad.toString();bad.printStackTrace();}
    }
    private static void observeMiningClient(MinecraftClient c){
        var row=new LinkedHashMap<String,Object>();
        row.put("tick",ticks);row.put("leg",missionLeg);row.put("screen",c.currentScreen==null?"none":c.currentScreen.getClass().getName());
        row.put("flight",DescentClientState.enabled);row.put("flightClockActive",com.terminaldetector.drmd.client.flight.FlightBodyClient.active());
        row.put("clockPending",com.terminaldetector.drmd.client.flight.FlightBodyClient.pending());
        row.put("attackPressed",c.options.attackKey.isPressed());
        row.put("actualCrosshair",c.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit?hit.getBlockPos().toShortString():String.valueOf(c.crosshairTarget));
        if(c.player!=null){
            row.put("position",xyz(c.player.getPos()));row.put("velocityMps",xyz(c.player.getVelocity().multiply(20)));
            row.put("heldItem",Registries.ITEM.getId(c.player.getMainHandStack().getItem()).toString());
            row.put("creative",c.player.isCreative());row.put("alive",c.player.isAlive());
        }
        if(MINE_CLIENT_STATE.size()==48)MINE_CLIENT_STATE.removeFirst();MINE_CLIENT_STATE.addLast(row);
    }
    private static void steer(MinecraftClient c,Vec3d at,Vec3d target,double deadBand){
        var error=target.subtract(at);var velocity=c.player.getVelocity().multiply(20);
        double speed=Math.min(7,error.length()*1.8);
        var wanted=error.lengthSquared()<1e-12?Vec3d.ZERO:error.normalize().multiply(speed);
        var correction=wanted.subtract(velocity);
        if(correction.length()>deadBand){
            var direction=correction.normalize();var up=Math.abs(direction.y)>.94?new Vec3d(0,0,1):new Vec3d(0,1,0);
            ShipAttitudeClient.acceptPortalFrame(c.player,direction,up);c.options.forwardKey.setPressed(true);heldTicks++;
        }
    }
    private static Vec3d steeringGoal(Vec3d at) {
        if(missionLeg==1&&heraldDepartureWaypoints<2) {
            // The full herald block stands between the starting stance and station gate.
            // Move to its side and pass its far face before lining up with the entrance.
            var outsideHerald=home().add(1.65,1,0);
            if(heraldDepartureWaypoints==0) {
                if(at.x<outsideHerald.x-.35) return outsideHerald;
                heraldDepartureWaypoints++;
            }
            var beyondHerald=outsideHerald.add(0,0,3.65);
            if(at.z<beyondHerald.z-.35) return beyondHerald;
            heraldDepartureWaypoints++;
        }
        return goal();
    }
    public static int fieldViewCase(){return FIELD_VIEWS&&phase==6&&viewReady?viewLeg:-1;}
    public static Vec3d fieldViewStation(){return new Vec3d(ANCHOR.x(),ANCHOR.y()+8,ANCHOR.z());}
    private static Vec3d fieldViewGoal(){return new Vec3d(ANCHOR.x()+.5,ANCHOR.y()+8,
        ANCHOR.z()+.5-switch(viewLeg){case 1->512;case 2->3072;default->24;});}
    private static double fieldViewMinimumPath(){
        var near=new Vec3d(ANCHOR.x()+.5,ANCHOR.y()+8,ANCHOR.z()-23.5);
        var mid=new Vec3d(near.x,near.y,ANCHOR.z()-511.5);var far=new Vec3d(near.x,near.y,ANCHOR.z()-3071.5);
        return near.distanceTo(mid)+mid.distanceTo(far)+far.distanceTo(near)-2.3*3;
    }
    private static void beginFieldViews(MinecraftClient c){
        require(NativeOrbitalMaskCheck.ENABLED,"field views require actual GPU/pixel observer flag");
        require(c.options.getClampedViewDistance()>=2&&c.options.getClampedViewDistance()<=8,"finite field view scene requires actual32..128m ordinary view radius");
        viewCells=plan.cells().stream().filter(cell->{var p=cell.position();return p.x()>=ANCHOR.x()-12&&p.x()<ANCHOR.x()+12
            &&p.y()>=ANCHOR.y()&&p.y()<ANCHOR.y()+16&&p.z()>=ANCHOR.z()-10&&p.z()<ANCHOR.z()+10;}).toList();
        require(viewCells.size()==7680,"same original complete station AIR+solid footprint");
        missionCompletedWall=System.nanoTime()-armedAt;viewStarted=System.nanoTime();viewPrevious=c.player.getPos();viewLeg=0;phase=6;frames=0;
    }
    private static void serverFieldView(ServerPlayerEntity p,MinecraftServer server){
        require(!p.isCreative()&&!p.isSpectator()&&!p.noClip&&!p.hasVehicle(),"field views retain same ordinary Survival actor");
        var at=p.getPos();require(WorldAccess.of(heldWorld).readIfLoaded(p.getBlockPos()).isPresent()&&heldWorld.isSpaceEmpty(p,p.getBoundingBox()),"field view pilot stays in actual loaded collision-free native space");
        if(viewServerPrevious!=null){double step=at.distanceTo(viewServerPrevious);require(step<=2,"field view server movement discontinuity>2m");if(step>1e-7)require(matchesMove(at),"field view server endpoint lacks actual Entity.move receipt");}viewServerPrevious=at;
        viewServerSamples++;if(viewServerSamples%200==0)stateRow("field-server",server.getTicks(),at,DescentPlayerData.get(p).getFlightVelocity(),false);
        boolean settled=DescentPlayerData.get(p).isEnabled()&&at.distanceTo(fieldViewGoal())<=1.15&&DescentPlayerData.get(p).getFlightVelocity().length()<=2;
        viewReady=false;if(!settled){viewSettledSince=-1;return;}
        if(viewSettledSince<0)viewSettledSince=server.getTicks();
        require(server.getTicks()-viewSettledSince<=320,"actual loaded/pixel view must settle within320 real server ticks");
        if(viewLeg==0||viewLeg==3){
            var access=WorldAccess.of(heldWorld);int end=Math.min(viewCells.size(),viewReadCursor+64);
            while(viewReadCursor<end){var cell=viewCells.get(viewReadCursor);var q=cell.position();var actual=access.readIfLoaded(new BlockPos(q.x(),q.y(),q.z()));require(actual.isPresent()&&!actual.orElseThrow().hasBlockEntity()&&Registries.BLOCK.getId(actual.orElseThrow().getBlock()).toString().equals(cell.state()),"actual close station loaded-only AIR+solid readback "+q);viewReadCursor++;}
            if(viewReadCursor<viewCells.size())return;
        }
        require(rep()==3&&instance().toString().equals(claimedReceipt)&&tool.getDamage()==2,"view flight preserves actual claimed mission and original tool state");
        require(com.terminaldetector.drmd.world.planet.AuthoredOrbitalCatalogue.visible(heldWorld).size()==1,"view flight cannot resurrect mined asteroid proxies");
        viewReady=true;if(!NativeOrbitalMaskCheck.fieldViewObserved(viewLeg))return;
        synchronized(VIEW_ROWS){VIEW_ROWS.add(Map.of("leg",viewLeg,"worldTime",heldWorld.getTime(),"position",xyz(at),"closeReadback",viewReadCursor,"sameClaimedReceipt",true));}
        event("ACTUAL_NATIVE_STATION_VIEW_"+viewLeg,p);
        if(viewLeg==3){phase=7;pendingFrame="field-near-mid-far-near-complete";viewReady=false;return;}
        viewLeg++;viewReadCursor=0;viewSettledSince=-1;viewReady=false;
    }
    private static void clientFieldView(MinecraftClient c){
        try{
            ++viewTicks;require(NativeOrbitalMaskCheck.fieldViewFailure()==null,"actual field view observer failure "+NativeOrbitalMaskCheck.fieldViewFailure());
            require(!c.player.isCreative()&&!c.player.isSpectator()&&!c.player.noClip&&!c.player.hasVehicle(),"field view client remains colliding Survival");
            require(WorldAccess.of(c.world).readIfLoaded(c.player.getBlockPos()).isPresent()&&c.world.isSpaceEmpty(c.player,c.player.getBoundingBox()),"actual field view client volume is loaded and empty");
            var at=c.player.getPos();double step=at.distanceTo(viewPrevious);viewPath+=step;require(step<=2,"field view client movement discontinuity>2m");require(lastClientMoveEnd!=null&&at.distanceTo(lastClientMoveEnd)<1e-7,"field view position changed outside actual Entity.move");viewPrevious=at;
            clearKeys(c);
            if(!DescentClientState.enabled){if(viewFlightEnable!=viewLeg){net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new ModNetworking.ActionPayload("enable"));viewFlightEnable=viewLeg;}return;}
            var velocity=c.player.getVelocity().multiply(20);var error=fieldViewGoal().subtract(at);
            if(viewReady&&error.length()<=1.15&&velocity.length()<=2){aim(c,new Vec3d(ANCHOR.x()+3.5,ANCHOR.y()+6.5,ANCHOR.z()-3.5));return;}
            double speed=Math.min(20,error.length()*1.8);var wanted=error.lengthSquared()<1e-12?Vec3d.ZERO:error.normalize().multiply(speed);var correction=wanted.subtract(velocity);
            if(correction.length()>1.2){var direction=correction.normalize();ShipAttitudeClient.acceptPortalFrame(c.player,direction,Math.abs(direction.y)>.94?new Vec3d(0,0,1):new Vec3d(0,1,0));c.options.forwardKey.setPressed(true);viewHeldTicks++;}
            if(viewTicks%200==0)stateRow("field-client",ticks,at,velocity,c.options.forwardKey.isPressed());
        }catch(Throwable bad){failure=bad.toString();bad.printStackTrace();}
    }
    private static void stateRow(String side,int tick,Vec3d position,Vec3d velocity,boolean held){
        synchronized(STATE_ROWS){require(STATE_ROWS.size()<512,"bounded native state journal512");STATE_ROWS.add(Map.of("side",side,"tick",tick,"position",xyz(position),"velocityMps",xyz(velocity),"passed",passed,"heldForward",held));}
    }
    private static void aim(MinecraftClient c,Vec3d target){var delta=target.subtract(c.player.getEyePos()).normalize();ShipAttitudeClient.acceptPortalFrame(c.player,delta,Math.abs(delta.y)>.94?new Vec3d(0,0,1):new Vec3d(0,1,0));}
    private static void clearKeys(MinecraftClient c){
        c.options.attackKey.setPressed(false);c.options.useKey.setPressed(false);
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
            if(pendingFrame!=null){String name=pendingFrame;pendingFrame=null;var folder=c.runDirectory.toPath().resolve("orbital-mission-frames");Files.createDirectories(folder);try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){image.writeTo(folder.resolve(name+".png"));}}
            if(phase==5&&++frames>=20){require(missionLeg==7&&passed==5&&verified==11712&&travelled>=minimumPath()&&heldTicks>=8&&initialTeleports==1&&mined==2&&heraldUses>=7,"full genuine input route criteria");require(!CLIENT_MOVES.isEmpty()&&serverSamples>100,"actual native motion and server authority receipts");if(FIELD_VIEWS){beginFieldViews(c);return;}finish(c,"PASS","native original field Survival mission via real keyboard/mining/rightclick/extraction; normal save follows, not a cold reader");}
            if(phase==7&&++frames>=20){require(viewPath>=fieldViewMinimumPath()&&viewHeldTicks>=100&&viewServerSamples>=100&&NativeOrbitalMaskCheck.allFieldViewsObserved(),"all four actual loaded-close/unloaded-proxy field views and physical return");finish(c,"PASS","original native Survival mission plus actual near512mid3072far-near station flight and normal GPU/pixel handoff; not a cold reader");}
        }catch(Throwable bad){finish(c,"FAIL",bad.toString());}
    }
    private static void release(){
        if(heldWorld==null)return;for(int i=0;i<requested;i++){var held=COVER.get(i);BodyVolumeLeases.releaseNativeInterest(heldWorld,held.owner());if(!held.forcedBefore())heldWorld.setChunkForced(held.column().x(),held.column().z(),false);}heldWorld=null;
    }
    private static void finish(MinecraftClient c,String status,String reason){
        if(done)return;done=true;if(c.player!=null)clearKeys(c);
        var report=new LinkedHashMap<String,Object>();report.put("status",status);report.put("extendedFieldViews",FIELD_VIEWS);report.put("fieldViewLeg",viewLeg);report.put("fieldViewTicks",viewTicks);report.put("fieldViewLastAdmission",NativeOrbitalMaskCheck.fieldViewDiagnostic());report.put("fieldViewWallNanos",viewStarted==0?0:System.nanoTime()-viewStarted);report.put("originalMissionCompletedWallNanos",missionCompletedWall);report.put("fieldViewPathMetres",viewPath);report.put("fieldViewMinimumPathMetres",fieldViewMinimumPath());report.put("fieldViewHeldTicks",viewHeldTicks);report.put("fieldViewServerSamples",viewServerSamples);synchronized(VIEW_ROWS){report.put("fieldViewRows",List.copyOf(VIEW_ROWS));}report.put("reason",reason);report.put("blockedCube",blockedCube);report.put("nativeCubic",true);report.put("permissionBefore",permissionBefore);report.put("permissionAfterAdmission",permissionAfter);report.put("operatorEntryLevel",assignedOperator==null?null:assignedOperator.getPermissionLevel());report.put("authorProfile",playerId==null?null:playerId.toString());report.put("coldReader",false);report.put("phase",phase);report.put("passed",passed);report.put("missionLeg",missionLeg);report.put("actualHeraldUses",heraldUses);report.put("earlyHeraldUses",initialUses);report.put("firstObservedClaimUse",firstClaimUse);report.put("actualStoneBreakEvents",mined);report.put("contract",CONTRACT);report.put("actualReputation",observedRep);report.put("survivalPeerStillHasPermission2",actualPermission>=2);report.put("verifiedCells",verified);report.put("requestedColumns",requested);report.put("fullHaloCubes",readyCubes);report.put("initialTeleports",initialTeleports);report.put("heldKeyboardTicks",heldTicks);report.put("measuredClientTicks",flightTicks);report.put("measuredWallNanos",armedAt==0?0:System.nanoTime()-armedAt);report.put("actualPathMetres",travelled);report.put("minimumGeometricPathMetres",minimumPath());report.put("maxClientStep",maxStep);report.put("maxCursorDelta",maxCursorDelta);report.put("maxCasDelta",maxCasDelta);report.put("actualServerSamples",serverSamples);report.put("planHash",plan==null?"":plan.hash());
        report.put("heraldDepartureWaypoints",heraldDepartureWaypoints);
        report.put("miningAimTicks",miningAimTicks);report.put("miningAttackEdges",miningAttackEdges);
        report.put("lastMiningClientState",List.copyOf(MINE_CLIENT_STATE));
        synchronized(EVENTS){report.put("events",List.copyOf(EVENTS));}synchronized(STATE_ROWS){report.put("stateRows",List.copyOf(STATE_ROWS));}synchronized(CLIENT_MOVES){report.put("actualClientMoves",List.copyOf(CLIENT_MOVES));}synchronized(SERVER_MOVES){report.put("actualServerMoves",List.copyOf(SERVER_MOVES));}
        try{Files.writeString(c.runDirectory.toPath().resolve("orbital-native-mission.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception bad){bad.printStackTrace();}
        System.out.println("NATIVE_ORBITAL_MISSION_"+status+" "+reason);c.scheduleStop();
    }
}
