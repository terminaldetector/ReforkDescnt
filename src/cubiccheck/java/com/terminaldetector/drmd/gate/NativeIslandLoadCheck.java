package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.flight.BoardingClient;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.physics.collision.BodyJointConstraint;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Real warm/cold native loading of a connected assembly longer than the activity halo.
 * The damaged last body's saved entity pivot is outside its remaining material. */
public final class NativeIslandLoadCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.island");
    private static final int Y=2156;
    private static final List<BlockBodyEntity> ORIGINAL=new ArrayList<>();
    private static volatile List<UUID> ids=List.of();
    private static final Map<UUID,BlockBodyEntity> RESTORED=new ConcurrentHashMap<>();
    private static final Set<UUID> SAVED=ConcurrentHashMap.newKeySet(),DRAWN=new HashSet<>();
    private static final List<String> SCENES=new ArrayList<>();
    private static volatile int phase,age,stable;private static int ticks,frames,keys;
    private static volatile float frameDelta;
    public static void beforeFrame(float delta){frameDelta=delta;}
    private static boolean started,prepared;private static volatile boolean ready,done,placeRequested,walking,walked;
    private static volatile int unloadedTicks,maxFarColumns;private static volatile double walkDistance,maxAnchorError;
    private static Vec3d walkFrom;private static volatile Vec3 launch;
    private static net.minecraft.entity.Entity entity(BlockBodyEntity b){return b;}
    private static Path file(String name){return MinecraftClient.getInstance().runDirectory.toPath().resolve(name);}
    private static void require(boolean ok,String why){if(!ok){done=true;MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException(why);}}
    public static void saved(BlockBodyEntity b,NbtCompound tag){
        if(!ENABLED||phase!=2||entity(b).getWorld().isClient||!ids.contains(entity(b).getUuid()))return;
        try{Files.createDirectories(file("island-nbt"));Files.writeString(file("island-nbt/"+entity(b).getUuid()+".snbt"),tag.toString());SAVED.add(entity(b).getUuid());}catch(Exception e){throw new IllegalStateException(e);}
    }
    public static void loaded(BlockBodyEntity b,NbtCompound tag){
        if(!ENABLED||phase!=3||entity(b).getWorld().isClient||!ids.contains(entity(b).getUuid()))return;
        try{
            var before=StringNbtReader.parse(Files.readString(file("island-nbt/"+entity(b).getUuid()+".snbt")));var actual=entity(b).writeNbt(new NbtCompound());
            require(!b.hasSimulatedPhysics(),"cold body checked before first restored tick");
            require(before.getCompound("body").equals(tag.getCompound("body")),"actual cold storage retains exact physical state");
            for(String key:List.of("Pos","shape","materialDamage"))require(Objects.equals(before.get(key),actual.get(key)),"cold stored component differs: "+key);
            for(String key:List.of("qx","qy","qz","qw","vx","vy","vz","lx","ly","lz"))require(Math.abs(before.getCompound("body").getDouble(key)-actual.getCompound("body").getDouble(key))<1e-10,"cold restored physical component differs: "+key);
            require(ORIGINAL.stream().noneMatch(old->old==b),"world loader must create the persisted identity, not reuse a removed object");
            require(RESTORED.putIfAbsent(entity(b).getUuid(),b)==null,"one cold entity per saved UUID");System.out.println("ISLAND_COLD_BODY PASS "+entity(b).getUuid());
        }catch(Exception e){throw new IllegalStateException("cold island restore verification",e);}
    }
    public static void register(){
        require(file("").getFileName().toString().equals("cubic-client-automation"),"disposable island stand only");
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(done)return;require(++ticks<3600,"island timeout phase="+phase+" age="+age+" restored="+RESTORED.size()+" stable="+stable);
            com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
            if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
            if(c.player==null||c.world==null||c.getServer()==null)return;
            if(ticks%60==0&&phase==3)System.out.println("ISLAND_CLIENT at="+c.player.getPos()+" flying="+c.player.getAbilities().flying+" drives="+BoardingClient.drives(c.player)+" epoch="+BoardingClient.epoch()+" seq="+BoardingClient.sequence()+" state="+BoardingClient.state());
            if(!started){started=true;c.setScreen(null);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);c.inGameHud.getChatHud().clear(false);c.options.hudHidden=true;c.options.setPerspective(Perspective.THIRD_PERSON_BACK);com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;com.terminaldetector.drmd.client.config.DescentConfig.planetFloor=false;com.terminaldetector.drmd.client.config.DescentConfig.levelSky=false;
                c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());com.terminaldetector.drmd.flight.FlightSystem.disable(p,DescentPlayerData.get(p));p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.setInvulnerable(true);p.sendAbilitiesUpdate();p.teleport(p.getServerWorld(),10,Y+5,17.5,Set.of(),0,15);});return;
            }
            if(!prepared&&c.player.getY()>Y-20&&WorldAccess.of(c.world).isVolumeAvailable(new D6ChunkPos(0,Y>>4,1))){prepared=true;c.getServer().execute(()->setup(c));}
            if(ready&&!walked){if(!walking){walking=true;keys=0;c.options.leftKey.setPressed(true);}else if(++keys>=8)c.options.leftKey.setPressed(false);}
        });
        ServerTickEvents.START_WORLD_TICK.register(w->{
            if(!started||done||w!=w.getServer().getOverworld()||phase==0||phase==2)return;
            var bodies=current(w);if(bodies.size()!=3)return;var b=bodies.get(0);
            var dv=new Vec3(0,0,1.5).minus(b.physics().linearVelocity()).scaled(.25);if(dv.length()>.12)dv=dv.scaled(.12/dv.length());b.applyPhysicsImpulse(b.physicsCentre(),BoardingSystem.mc(dv.scaled(b.physicsMass())));
            var q=b.rotation();if(q.w()<0)q=new Quat(-q.x(),-q.y(),-q.z(),-q.w());var a=new Vec3(q.x(),q.y(),q.z()).normalized().scaled(-30*2*Math.acos(Math.max(-1,Math.min(1,q.w())))).minus(b.physics().angularVelocity().scaled(10));var torque=b.physics().worldInertia().transform(a);double cap=b.physicsMass()*16;if(torque.length()>cap)torque=torque.scaled(cap/torque.length());b.applyPhysicsTorque(BoardingSystem.mc(torque));
        });
        ServerTickEvents.END_WORLD_TICK.register(w->{
            if(!prepared||done||phase==0||w!=w.getServer().getOverworld())return;age++;
            var p=w.getServer().getPlayerManager().getPlayerList().getFirst();var bodies=current(w);
            if(phase==3&&(age%20==0||age<15))System.out.println("ISLAND_RETURN age="+age+" player="+p.getPos()+" v="+p.getVelocity()+" flying="+p.getAbilities().flying+" tracked="+w.getPlayers().contains(p)+" ready="+BoardingSystem.ready(p)+" drives="+BoardingSystem.drives(p)+" state="+BoardingSystem.state(p)+" bodies="+bodies.stream().map(b->entity(b).getUuid()+":"+b.massPosition()+":held="+BodyVolumeLeases.heldColumns(w,entity(b).getUuid())).toList());
            if(phase==2){
                if(age<3||p.getX()<1000)return;
                require(ids.stream().allMatch(id->BodyVolumeLeases.heldColumns(w,id)==0),"leaving all components releases every volume lease");
                if(bodies.isEmpty()&&SAVED.size()==3)unloadedTicks++;else unloadedTicks=0;
                if(unloadedTicks>=20){
                    var bounds=BodyJointRegistry.get(w).lastBounds(ids.get(0)).orElseThrow();phase=3;age=stable=0;placeRequested=false;walking=walked=ready=false;
                    p.teleport(w,bounds.min().x()+2,bounds.max().y()+3,bounds.centre().z(),Set.of(),0,15);System.out.println("ISLAND_ALL_UNLOADED PASS");
                }
                require(age<700,"native entities did not actually unload after lease release");return;
            }
            if(age%60==0)System.out.println("ISLAND_WAIT phase="+phase+" bodies="+bodies.size()+" restored="+RESTORED.size()+" paused="+bodies.stream().map(BlockBodyEntity::boardingMotionPaused).toList());
            if(bodies.size()!=3)return;
            var a=bodies.get(0);var far=bodies.get(2);maxFarColumns=Math.max(maxFarColumns,BodyVolumeLeases.heldColumns(w,entity(far).getUuid()));
            require(p.getPos().squaredDistanceTo(far.physicsCentre())>Math.pow(far.radius()+128,2),"last module must stay beyond the old direct-player halo");
            require(bodies.stream().allMatch(b->BodySpatialIndex.loaded(w).stream().filter(other->entity(other).getUuid().equals(entity(b).getUuid())).count()==1),"no duplicate registered physical identity");
            if(bodies.stream().anyMatch(BlockBodyEntity::boardingMotionPaused)){stable=0;return;}
            for(var link:BodyJointRegistry.get(w).links())if(ids.contains(link.a().body())){
                var first=bodies.stream().filter(b->entity(b).getUuid().equals(link.a().body())).findFirst().orElseThrow();var second=bodies.stream().filter(b->entity(b).getUuid().equals(link.b().body())).findFirst().orElseThrow();
                double error=first.localToWorld(BoardingSystem.mc(link.a().point())).distanceTo(second.localToWorld(BoardingSystem.mc(link.b().point())));maxAnchorError=Math.max(maxAnchorError,error);require(error<.02,"connected material anchors must retain physical continuity");
            }
            if(!placeRequested){
                if(++stable<10)return;placeRequested=true;stable=0;launch=a.massPosition();var at=a.localToWorld(new Vec3d(2,1.05,1.5));p.getAbilities().flying=false;p.getAbilities().allowFlying=false;p.sendAbilitiesUpdate();p.teleport(w,at.x,at.y,at.z,Set.of(),0,15);p.setVelocity(BoardingSystem.mc(a.physics().linearVelocity().scaled(.05)));System.out.println("ISLAND_LANDING phase="+phase+" age="+age+" at="+at+" body="+a.massPosition()+" rotation="+a.rotation());return;
            }
            var state=BoardingSystem.state(p);boolean supported=BoardingSystem.drives(p)&&state!=null&&state.grounded()&&state.support()==entity(a).getId();
            if(!ready){if(supported)stable++;else stable=0;if(stable>=20){walkFrom=a.worldToLocal(p.getPos());ready=true;}}else{
                require(supported,"occupied root must keep real contact while remote modules simulate");var local=a.worldToLocal(p.getPos());walkDistance=Math.hypot(local.x-walkFrom.x,local.z-walkFrom.z);if(walking&&walkDistance>.3&&a.massPosition().minus(launch).length()>.5)walked=true;
            }
            if(age%60==0)System.out.println("ISLAND_STATE phase="+phase+" bodies="+bodies.size()+" farColumns="+maxFarColumns+" stable="+stable+" walked="+walkDistance);
        });
    }
    private static List<BlockBodyEntity> current(ServerWorld w){
        var out=new ArrayList<BlockBodyEntity>();for(var id:ids)for(var b:BodySpatialIndex.loaded(w))if(entity(b).getUuid().equals(id))out.add(b);return out;
    }
    private static void setup(MinecraftClient c){
        var w=c.getServer().getOverworld();for(int x:new int[]{4,12,20})w.getChunk(x,1);
        var cells=new ArrayList<BlockBodyEntity.Cell>();for(int x=0;x<128;x++)for(int z=0;z<3;z++)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,0,z),Blocks.IRON_BLOCK.getDefaultState()));
        for(int i=0;i<3;i++)ORIGINAL.add(BlockBodyEntity.createLocal(w,cells,new Vec3d(8+i*128,Y,16)));
        ids=ORIGINAL.stream().map(b->entity(b).getUuid()).toList();var registry=BodyJointRegistry.get(w);
        for(int i=0;i<2;i++){var a=ORIGINAL.get(i);var b=ORIGINAL.get(i+1);registry.add(w,a,b,new BodyJointLink(UUID.randomUUID(),new BodyJointLink.Endpoint(entity(a).getUuid(),new BlockPos(127,0,1),new Vec3(128,.5,1.5),Quat.IDENTITY),new BodyJointLink.Endpoint(entity(b).getUuid(),new BlockPos(0,0,1),new Vec3(0,.5,1.5),Quat.IDENTITY),BodyJointConstraint.Kind.FIXED,0,0,0,0,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY));}
        var cuts=new HashMap<BlockPos,net.minecraft.block.BlockState>();for(int x=1;x<128;x++)for(int z=0;z<3;z++)cuts.put(new BlockPos(x,0,z),Blocks.AIR.getDefaultState());require(ORIGINAL.get(2).setLocalBlocks(cuts),"real remote hull damage accepted");require(ORIGINAL.get(2).cells().size()==3,"damaged last hull has three surviving material cells");
        try{Files.writeString(file("island-ids.json"),new com.google.gson.Gson().toJson(ids.stream().map(UUID::toString).toList()));}catch(Exception e){throw new IllegalStateException(e);}phase=1;age=stable=0;w.setTimeOfDay(6000);
    }
    public static void drew(BlockBodyEntity b){if(ENABLED&&!done&&ids.contains(entity(b).getUuid()))DRAWN.add(entity(b).getUuid());}
    public static void rendered(){
        var drawn=Set.copyOf(DRAWN);DRAWN.clear();if(done||!ready||!walked||(phase!=1&&phase!=3)||!drawn.contains(ids.get(0)))return;
        var c=MinecraftClient.getInstance();if(c.currentScreen!=null||++frames<6)return;
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){
            var root=java.util.stream.StreamSupport.stream(c.world.getEntities().spliterator(),false).filter(e->e instanceof BlockBodyEntity&&e.getUuid().equals(ids.get(0))).map(e->(BlockBodyEntity)e).findFirst().orElseThrow();
            var view=BoardingClient.bodyView(root,frameDelta);var q=view==null?root.renderRotation(frameDelta):view.rotation();
            var pivot=view==null?BoardingSystem.pure(entity(root).getLerpedPos(frameDelta)):view.position().plus(q.rotate(root.localPivot().minus(root.localCentre())));
            var local=q.inverse().rotate(BoardingSystem.pure(c.player.getLerpedPos(frameDelta)).minus(pivot)).plus(root.localPivot());
            Files.writeString(file(phase==1?"island-view-occupied.json":"island-view-restored.json"),new com.google.gson.Gson().toJson(Map.of("renderedLocalFeet",local,"predictedBodyView",view!=null,"clientLocalFeet",root.worldToLocal(c.player.getPos()),"delta",frameDelta))+"\n");
            require(Math.abs(local.y()-1)<.2&&local.x()>0&&local.x()<128&&local.z()>0&&local.z()<3,"rendered player feet must lie on the actual visible deck: "+local);
            image.writeTo(file(phase==1?"island-occupied.png":"island-restored.png"));SCENES.add(phase==1?"occupied-chain":"cold-return");frames=0;
            if(phase==1){ready=false;phase=2;age=0;c.options.leftKey.setPressed(false);c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.sendAbilitiesUpdate();p.teleport(p.getServerWorld(),1200,Y+5,16,Set.of(),0,15);});}
            else{require(RESTORED.size()==3&&maxFarColumns>0&&unloadedTicks>=20,"actual native cold-return proof incomplete");Files.writeString(file("island-load-ok.json"),new com.google.gson.Gson().toJson(Map.of("actualUnload",true,"actualColdRestore",RESTORED.size(),"savedBodies",SAVED.size(),"farModuleBeyondPlayerHalo",true,"maxFarColumns",maxFarColumns,"maxAnchorError",maxAnchorError,"postLoadWalkDistance",walkDistance,"stableContactTicks",stable,"unloadedTicks",unloadedTicks,"scenes",SCENES))+"\n");done=true;c.options.leftKey.setPressed(false);c.scheduleStop();}
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
