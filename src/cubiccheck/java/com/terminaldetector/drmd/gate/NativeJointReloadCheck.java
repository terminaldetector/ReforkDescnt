package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.flight.BoardingClient;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.nbt.*;
import net.minecraft.util.math.Vec3d;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Same closed world in a second JVM. Observes actual serialization, never inserts NBT bodies. */
public final class NativeJointReloadCheck {
    public static final boolean WRITE=Boolean.getBoolean("drmd.gate.jointsStateWrite"),READ=Boolean.getBoolean("drmd.gate.jointsStateRead");
    /** Extra cold material witness; the healthy historical eight-body path stays unchanged. */
    public static final boolean EDITED_MATERIAL=Boolean.getBoolean("drmd.gate.jointsStateEditedMaterial");
    private static final net.minecraft.util.math.BlockPos EDITED_CELL=new net.minecraft.util.math.BlockPos(1,1,0);
    private static volatile boolean materialEdited,editedBeforeFirstTick;
    private static volatile List<UUID> ids=List.of();
    private static final Map<UUID,BlockBodyEntity> LOADED=new ConcurrentHashMap<>();
    private static final Set<UUID> SAVED=ConcurrentHashMap.newKeySet(),DRAWN=new HashSet<>();
    private static volatile boolean registryRestored,ready,walkRequested,walkObserved,done;
    private static volatile int stable;private static int ticks,walkTicks,frames;
    private static volatile Vec3d walkFrom;private static volatile double walked,maxAnchorError;
    private static boolean visualReady;
    private static net.minecraft.entity.Entity entity(BlockBodyEntity body){return body;}
    private static Path file(String name){return MinecraftClient.getInstance().runDirectory.toPath().resolve(name);}
    private static void require(boolean ok,String why){if(!ok){done=true;MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException(why);}}
    public static void watch(BlockBodyEntity[] bodies){
        if(!WRITE)return;ids=Arrays.stream(bodies).map(b->entity(b).getUuid()).toList();require(ids.size()==8,"eight actual joint fixture bodies");
        try{Files.createDirectories(file("joint-state-nbt"));Files.writeString(file("joint-state-ids.json"),new com.google.gson.Gson().toJson(ids.stream().map(UUID::toString).toList()));}catch(Exception e){throw new IllegalStateException(e);}
    }
    public static void saved(BlockBodyEntity body,NbtCompound n){
        if(!WRITE||entity(body).getWorld().isClient||!ids.contains(entity(body).getUuid()))return;
        try{
            if(EDITED_MATERIAL&&ids.indexOf(entity(body).getUuid())==3&&materialEdited){
                var grid=BodyShapeCodec.read(n.getCompound("shape"));
                require(body.cells().size()==7&&grid.size()==7&&grid.stream().noneMatch(cell->cell.pos().equals(EDITED_CELL)),"actual shutdown serialization retains the removed arbitrary module cell");
                Files.writeString(file("joint-state-material-serialized.json"),"{\"actualSerializedEditedGrid\":true,\"remainingCells\":7,\"body\":\""+entity(body).getUuid()+"\"}\n");
            }
            Files.writeString(file("joint-state-nbt/"+entity(body).getUuid()+".snbt"),n.toString());SAVED.add(entity(body).getUuid());if(SAVED.size()==8)Files.writeString(file("joint-state-saved.json"),"{\"actualSerializedBodies\":8}\n");
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    public static void loaded(BlockBodyEntity body,NbtCompound saved){
        if(!READ||entity(body).getWorld().isClient||!ids.contains(entity(body).getUuid()))return;
        try{
            var expected=StringNbtReader.parse(Files.readString(file("joint-state-nbt/"+entity(body).getUuid()+".snbt")));
            require(!body.hasSimulatedPhysics(),"state observed before the first restored physical tick");
            require(expected.getCompound("body").equals(saved.getCompound("body")),"world storage retains the exact serialized physical data");
            var actual=entity(body).writeNbt(new NbtCompound());var before=expected.getCompound("body");var after=actual.getCompound("body");
            for(String key:new String[]{"qx","qy","qz","qw","vx","vy","vz","lx","ly","lz","passengerGravityx","passengerGravityy","passengerGravityz"})
                require(Double.isFinite(after.getDouble(key))&&Math.abs(before.getDouble(key)-after.getDouble(key))<=1e-10,"restored module component differs: "+key+" uuid="+entity(body).getUuid());
            require(before.getBoolean("gravity")==after.getBoolean("gravity"),"module gravity mode restored");
            for(String key:new String[]{"shape","materialDamage","Pos"})require(Objects.equals(expected.get(key),actual.get(key)),"actual restored module data differs: "+key);
            if(ids.indexOf(entity(body).getUuid())>=6)require(body.physics().linearVelocity().length()>.1,"saved boarded assembly must really be moving");
            if(EDITED_MATERIAL&&ids.indexOf(entity(body).getUuid())==3){
                require(body.cells().size()==7&&body.getLocalBlock(EDITED_CELL).isAir()&&body.getLocalBlock(new net.minecraft.util.math.BlockPos(0,0,1)).isOf(net.minecraft.block.Blocks.IRON_BLOCK),"actual new-JVM arbitrary module keeps its missing corner and original joint-bearing material before simulation");
                editedBeforeFirstTick=true;
            }
            var prior=LOADED.putIfAbsent(entity(body).getUuid(),body);require(prior==null||prior==body,"a saved body cannot be instantiated twice");
            System.out.println("JOINT_RELOAD_NBT PASS uuid="+entity(body).getUuid()+" cells="+body.cells().size());
        }catch(Exception e){throw new IllegalStateException("actual module restore failed",e);}
    }
    public static void registerWriter(){
        if(!WRITE)return;
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{
            if(ids.size()!=8)return;var w=server.getOverworld();
            if(EDITED_MATERIAL)editWriterMaterial(w);
            var n=((net.minecraft.world.PersistentState)BodyJointRegistry.get(w)).writeNbt(new NbtCompound(),w.getRegistryManager());
            require(n.getList("joints",NbtElement.COMPOUND_TYPE).size()==3,"three surviving joints and one physically broken connection");
            try{Files.writeString(file("joint-state-registry.snbt"),n.toString());}catch(Exception e){throw new IllegalStateException(e);}
        });
    }
    /** Actual public material transaction on the ordinary unoccupied motor child, never an NBT edit. */
    private static void editWriterMaterial(net.minecraft.server.world.ServerWorld world){
        require(world.getServer().isOnThread()&&!materialEdited,"edited cold writer runs once on actual server shutdown");
        var all=BodySpatialIndex.loaded(world);var module=all.stream().filter(b->entity(b).getUuid().equals(ids.get(3))).findFirst().orElseThrow();
        var identities=all.stream().map(b->entity(b).getUuid()).collect(java.util.stream.Collectors.toSet());
        var registry=BodyJointRegistry.get(world);var links=((net.minecraft.world.PersistentState)registry).writeNbt(new NbtCompound(),world.getRegistryManager());
        require(module.cells().size()==8&&module.getLocalBlock(EDITED_CELL).isOf(net.minecraft.block.Blocks.IRON_BLOCK),"ordinary motor child has its actual eight-cell original grid");
        var moduleLinks=registry.forBody(entity(module).getUuid());
        require(moduleLinks.size()==1&&moduleLinks.getFirst().kind()==com.terminaldetector.drmd.physics.collision.BodyJointConstraint.Kind.MOTOR
            &&moduleLinks.getFirst().b().body().equals(entity(module).getUuid()),"chosen material belongs to actual one-motor child");
        require(BoardingSystem.players(world).stream().noneMatch(player->{var contact=BoardingSystem.state(player);return contact!=null&&(contact.support()==entity(module).getId()||contact.gravityOwner()==entity(module).getId());}),"edited child is unoccupied; saved joined-deck passenger remains untouched");
        var anchor=BoardingSystem.mc(moduleLinks.getFirst().b().point());var point=module.localToWorld(anchor);var velocity=module.surfaceVelocityAt(point);var rotation=module.rotation();long revision=module.shapeRevision();
        require(module.setLocalBlock(EDITED_CELL,net.minecraft.block.Blocks.AIR.getDefaultState()),"actual ordinary module corner removal commits before normal world save");
        require(module.cells().size()==7&&module.getLocalBlock(EDITED_CELL).isAir()&&module.shapeRevision()>revision,"actual removed grid cell cannot be silently replaced or re-generated");
        require(module.getLocalBlock(new net.minecraft.util.math.BlockPos(0,0,1)).isOf(net.minecraft.block.Blocks.IRON_BLOCK),"motor joint keeps its bearing material");
        var updated=((net.minecraft.world.PersistentState)registry).writeNbt(new NbtCompound(),world.getRegistryManager());
        require(links.get("joints").equals(updated.get("joints")),"all original joint identities, endpoints, limits and drive activation remain unchanged");
        var beforeLocations=links.getList("locations",NbtElement.COMPOUND_TYPE);
        var afterLocations=updated.getList("locations",NbtElement.COMPOUND_TYPE);
        require(beforeLocations.size()==afterLocations.size(),"no endpoint location added or removed by a corner edit");
        for(int i=0;i<beforeLocations.size();i++){
            var before=beforeLocations.getCompound(i);var after=afterLocations.getCompound(i);
            var id=before.getUuid("body");
            require(id.equals(after.getUuid("body")),"all endpoint location identities stay fixed");
            if(!id.equals(entity(module).getUuid()))require(before.equals(after),"every unrelated endpoint keeps its exact location");
        }
        require(registry.lastBounds(entity(module).getUuid()).filter(module.loadingBounds()::equals).isPresent(),"edited endpoint refreshes its true full-volume loading bounds");
        require(identities.equals(BodySpatialIndex.loaded(world).stream().map(b->entity(b).getUuid()).collect(java.util.stream.Collectors.toSet())),"connected corner removal never duplicates or invents a fragment body");
        require(point.distanceTo(module.localToWorld(anchor))<1e-8&&velocity.distanceTo(module.surfaceVelocityAt(point))<1e-8&&rotation.equals(module.rotation()),"remaining material point, surface velocity and actual orientation survive COM rebase");
        materialEdited=true;
        try{Files.writeString(file("joint-state-material-edit.json"),new com.google.gson.Gson().toJson(Map.of("actualLocalMaterialEdit",true,"body",entity(module).getUuid().toString(),"localCell",List.of(1,1,0),"remainingCells",7,"unchangedBodyIdentities",true,"unchangedJointDefinitions",true,"actualEndpointLocation",true,"unoccupiedChild",true))+"\n");}catch(Exception e){throw new IllegalStateException(e);}
    }
    private static BlockBodyEntity body(int i){return LOADED.get(ids.get(i));}
    private static Vec3d anchor(BlockBodyEntity b,BodyJointLink.Endpoint e){return b.localToWorld(new Vec3d(e.point().x(),e.point().y(),e.point().z()));}
    public static void register(){
        require(READ&&file("").getFileName().toString().equals("cubic-client-automation"),"disposable joint reader only");
        if(EDITED_MATERIAL)require(Files.isRegularFile(file("joint-state-material-edit.json"))&&Files.isRegularFile(file("joint-state-material-serialized.json")),"edited reader requires genuine writer edit and actual normal serialized-grid receipts");
        try{var a=com.google.gson.JsonParser.parseString(Files.readString(file("joint-state-ids.json"))).getAsJsonArray();ids=a.asList().stream().map(v->UUID.fromString(v.getAsString())).toList();require(ids.size()==8,"eight saved UUIDs");}catch(Exception e){throw new IllegalStateException(e);}
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            server.getPlayerManager().setViewDistance(6);var w=server.getOverworld();var actual=((net.minecraft.world.PersistentState)BodyJointRegistry.get(w)).writeNbt(new NbtCompound(),w.getRegistryManager());
            try{var expected=StringNbtReader.parse(Files.readString(file("joint-state-registry.snbt")));require(expected.equals(actual)&&BodyJointRegistry.get(w).links().size()==3,"actual world restores precisely the surviving joint registry, configurations and activation times");registryRestored=true;}catch(Exception e){throw new IllegalStateException(e);}
        });
        ServerTickEvents.START_WORLD_TICK.register(w->{
            if(w!=w.getServer().getOverworld()||done||LOADED.size()!=8)return;
            // Same bounded test thrusters as the writer stand, after exact before-first-tick
            // state observation. No pose, speed or passenger state is assigned here.
            for(int i:new int[]{0,4,6}){
                var b=body(i);var desired=i==0?new Vec3(0,0,0):new Vec3(1.5,0,0);var dv=desired.minus(b.physics().linearVelocity()).scaled(.4);if(dv.length()>.25)dv=dv.scaled(.25/dv.length());
                b.applyPhysicsImpulse(b.physicsCentre(),BoardingSystem.mc(dv.scaled(b.physicsMass())));
                var q=b.rotation();if(q.w()<0)q=new Quat(-q.x(),-q.y(),-q.z(),-q.w());var axis=new Vec3(q.x(),q.y(),q.z());double angle=2*Math.acos(Math.max(-1,Math.min(1,q.w())));
                var acceleration=axis.normalized().scaled(-36*angle).minus(b.physics().angularVelocity().scaled(12));var torque=b.physics().worldInertia().transform(acceleration);double cap=b.physicsMass()*16;if(torque.length()>cap)torque=torque.scaled(cap/torque.length());b.applyPhysicsTorque(BoardingSystem.mc(torque));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(done||!registryRestored||LOADED.size()!=8||server.getPlayerManager().getPlayerList().isEmpty())return;
            var w=server.getOverworld();var p=server.getPlayerManager().getPlayerList().getFirst();
            require(!DescentPlayerData.get(p).isEnabled()&&!p.getAbilities().flying,"saved boarding mode must restore without test overrides");
            var active=BodySpatialIndex.loaded(w);for(var id:ids)require(active.stream().filter(b->entity(b).getUuid().equals(id)).count()==1,"one actual registered body per saved UUID");
            for(var link:BodyJointRegistry.get(w).links()){
                var a=LOADED.get(link.a().body());var b=LOADED.get(link.b().body());require(a!=null&&b!=null,"all saved endpoints loaded");
                var delta=anchor(b,link.b()).subtract(anchor(a,link.a()));var axis=a.rotation().rotate(new Vec3(1,0,0));
                if(link.kind()==com.terminaldetector.drmd.physics.collision.BodyJointConstraint.Kind.SLIDER){
                    double along=delta.dotProduct(BoardingSystem.mc(axis));double across=delta.subtract(BoardingSystem.mc(axis.scaled(along))).length();
                    require(along>=link.lower()-.02&&along<=link.upper()+.02&&across<.02,"restored slider retains its actual limits and lateral constraint");
                }else{maxAnchorError=Math.max(maxAnchorError,delta.length());require(delta.length()<.02,"restored joint anchor remains physically connected");}
                if(link.kind()==com.terminaldetector.drmd.physics.collision.BodyJointConstraint.Kind.MOTOR)
                    require(Math.abs(b.physics().angularVelocity().minus(a.physics().angularVelocity()).dot(axis)-link.speed())<.03,"saved hinge motor resumes its configured physical speed");
            }
            var state=BoardingSystem.state(p);boolean supported=BoardingSystem.drives(p)&&state!=null&&state.grounded()&&state.support()==entity(body(7)).getId();
            if(!ready){stable=supported?stable+1:0;if(stable>=30){walkFrom=body(7).worldToLocal(p.getPos());ready=true;}}else{
                require(supported,"restarted player retains physical walking on the saved module");
                var local=body(7).worldToLocal(p.getPos());walked=Math.hypot(local.x-walkFrom.x,local.z-walkFrom.z);if(walkRequested&&walked>.3)walkObserved=true;
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(done)return;require(++ticks<2400,"joint restart timeout restored="+LOADED.size()+" stable="+stable+" walked="+walked);
            com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
            if(!visualReady){visualReady=true;c.options.getViewDistance().setValue(6);c.options.hudHidden=true;c.options.setPerspective(Perspective.THIRD_PERSON_BACK);com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;com.terminaldetector.drmd.client.config.DescentConfig.planetFloor=false;com.terminaldetector.drmd.client.config.DescentConfig.levelSky=false;}
            if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
            if(c.player!=null&&c.world!=null&&ready){require(BoardingClient.drives(c.player),"client resumes the saved physical contact");if(!walkRequested){walkRequested=true;c.options.forwardKey.setPressed(true);}else if(++walkTicks>=8)c.options.forwardKey.setPressed(false);}
            if(ticks%100==0)System.out.println("JOINT_RELOAD restored="+LOADED.size()+" registry="+registryRestored+" stable="+stable+" ready="+ready+" walked="+walked);
        });
    }
    public static void drew(BlockBodyEntity b){if(READ&&!done&&ids.contains(entity(b).getUuid()))DRAWN.add(entity(b).getUuid());}
    public static void rendered(){
        var seen=Set.copyOf(DRAWN);DRAWN.clear();if(!READ||done||!ready||!walkObserved||walkTicks<12)return;
        var c=MinecraftClient.getInstance();if(c.player==null||c.world==null||c.currentScreen!=null||!seen.containsAll(List.of(ids.get(6),ids.get(7)))){frames=0;return;}
        for(var id:ids)require(java.util.stream.StreamSupport.stream(c.world.getEntities().spliterator(),false).filter(e->e instanceof BlockBodyEntity&&e.getUuid().equals(id)).count()==1,"one replicated module per saved identity");
        if(++frames<6)return;
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){
            if(EDITED_MATERIAL){
                require(editedBeforeFirstTick&&body(3).cells().size()==7&&body(3).getLocalBlock(EDITED_CELL).isAir(),"edited arbitrary assembly retained exact missing material through genuine cold load and all original physical walking checks");
                Files.writeString(file("joint-state-material-reload-ok.json"),new com.google.gson.Gson().toJson(Map.of("actualNewJvm",true,"actualEditedGridBeforeFirstPhysicsTick",true,"missingCellStillAbsent",true,"remainingCells",7,"allOriginalEightBodyColdPredicatesRequired",true))+"\n");
            }
            image.writeTo(file("joint-state-reloaded.png"));Files.writeString(file("joint-state-reload-ok.json"),new com.google.gson.Gson().toJson(Map.of("actualProcessRestart",true,"beforeFirstTickBodies",8,"exactJointRegistry",true,"survivingJoints",3,"brokenJointAbsent",true,"stableContactTicks",stable,"ordinaryKeyboardWalkDistance",walked,"freshFrames",frames,"maxAnchorError",maxAnchorError,"noReplacementBodies",true))+"\n");done=true;c.options.forwardKey.setPressed(false);c.scheduleStop();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
