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
    private static volatile List<UUID> ids=List.of();
    private static final Map<UUID,BlockBodyEntity> LOADED=new ConcurrentHashMap<>();
    private static final Set<UUID> SAVED=ConcurrentHashMap.newKeySet(),DRAWN=new HashSet<>();
    private static volatile boolean registryRestored,ready,walkRequested,walkObserved,done;
    private static volatile int stable;private static int ticks,walkTicks,frames;
    private static volatile Vec3d walkFrom;private static volatile double walked,maxAnchorError;
    private static boolean visualReady;
    private static Path file(String name){return MinecraftClient.getInstance().runDirectory.toPath().resolve(name);}
    private static void require(boolean ok,String why){if(!ok){done=true;MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException(why);}}
    public static void watch(BlockBodyEntity[] bodies){
        if(!WRITE)return;ids=Arrays.stream(bodies).map(BlockBodyEntity::getUuid).toList();require(ids.size()==8,"eight actual joint fixture bodies");
        try{Files.createDirectories(file("joint-state-nbt"));Files.writeString(file("joint-state-ids.json"),new com.google.gson.Gson().toJson(ids.stream().map(UUID::toString).toList()));}catch(Exception e){throw new IllegalStateException(e);}
    }
    public static void saved(BlockBodyEntity body,NbtCompound n){
        if(!WRITE||body.getWorld().isClient||!ids.contains(body.getUuid()))return;
        try{Files.writeString(file("joint-state-nbt/"+body.getUuid()+".snbt"),n.toString());SAVED.add(body.getUuid());if(SAVED.size()==8)Files.writeString(file("joint-state-saved.json"),"{\"actualSerializedBodies\":8}\n");}catch(Exception e){throw new IllegalStateException(e);}
    }
    public static void loaded(BlockBodyEntity body,NbtCompound saved){
        if(!READ||body.getWorld().isClient||!ids.contains(body.getUuid()))return;
        try{
            var expected=StringNbtReader.parse(Files.readString(file("joint-state-nbt/"+body.getUuid()+".snbt")));
            require(!body.hasSimulatedPhysics(),"state observed before the first restored physical tick");
            require(expected.getCompound("body").equals(saved.getCompound("body")),"world storage retains the exact serialized physical data");
            var actual=body.writeNbt(new NbtCompound());var before=expected.getCompound("body");var after=actual.getCompound("body");
            for(String key:new String[]{"qx","qy","qz","qw","vx","vy","vz","lx","ly","lz","passengerGravityx","passengerGravityy","passengerGravityz"})
                require(Double.isFinite(after.getDouble(key))&&Math.abs(before.getDouble(key)-after.getDouble(key))<=1e-10,"restored module component differs: "+key+" uuid="+body.getUuid());
            require(before.getBoolean("gravity")==after.getBoolean("gravity"),"module gravity mode restored");
            for(String key:new String[]{"shape","materialDamage","Pos"})require(Objects.equals(expected.get(key),actual.get(key)),"actual restored module data differs: "+key);
            if(ids.indexOf(body.getUuid())>=6)require(body.physics().linearVelocity().length()>.1,"saved boarded assembly must really be moving");
            var prior=LOADED.putIfAbsent(body.getUuid(),body);require(prior==null||prior==body,"a saved body cannot be instantiated twice");
            System.out.println("JOINT_RELOAD_NBT PASS uuid="+body.getUuid()+" cells="+body.cells().size());
        }catch(Exception e){throw new IllegalStateException("actual module restore failed",e);}
    }
    public static void registerWriter(){
        if(!WRITE)return;
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{
            if(ids.size()!=8)return;var w=server.getOverworld();var n=BodyJointRegistry.get(w).writeNbt(new NbtCompound(),w.getRegistryManager());
            require(n.getList("joints",NbtElement.COMPOUND_TYPE).size()==3,"three surviving joints and one physically broken connection");
            try{Files.writeString(file("joint-state-registry.snbt"),n.toString());}catch(Exception e){throw new IllegalStateException(e);}
        });
    }
    private static BlockBodyEntity body(int i){return LOADED.get(ids.get(i));}
    private static Vec3d anchor(BlockBodyEntity b,BodyJointLink.Endpoint e){return b.localToWorld(new Vec3d(e.point().x(),e.point().y(),e.point().z()));}
    public static void register(){
        require(READ&&file("").getFileName().toString().equals("cubic-client-automation"),"disposable joint reader only");
        try{var a=com.google.gson.JsonParser.parseString(Files.readString(file("joint-state-ids.json"))).getAsJsonArray();ids=a.asList().stream().map(v->UUID.fromString(v.getAsString())).toList();require(ids.size()==8,"eight saved UUIDs");}catch(Exception e){throw new IllegalStateException(e);}
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            server.getPlayerManager().setViewDistance(6);var w=server.getOverworld();var actual=BodyJointRegistry.get(w).writeNbt(new NbtCompound(),w.getRegistryManager());
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
            var active=BodySpatialIndex.loaded(w);for(var id:ids)require(active.stream().filter(b->b.getUuid().equals(id)).count()==1,"one actual registered body per saved UUID");
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
            var state=BoardingSystem.state(p);boolean supported=BoardingSystem.drives(p)&&state!=null&&state.grounded()&&state.support()==body(7).getId();
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
    public static void drew(BlockBodyEntity b){if(READ&&!done&&ids.contains(b.getUuid()))DRAWN.add(b.getUuid());}
    public static void rendered(){
        var seen=Set.copyOf(DRAWN);DRAWN.clear();if(!READ||done||!ready||!walkObserved||walkTicks<12)return;
        var c=MinecraftClient.getInstance();if(c.player==null||c.world==null||c.currentScreen!=null||!seen.containsAll(List.of(ids.get(6),ids.get(7)))){frames=0;return;}
        for(var id:ids)require(java.util.stream.StreamSupport.stream(c.world.getEntities().spliterator(),false).filter(e->e instanceof BlockBodyEntity&&e.getUuid().equals(id)).count()==1,"one replicated module per saved identity");
        if(++frames<6)return;
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){
            image.writeTo(file("joint-state-reloaded.png"));Files.writeString(file("joint-state-reload-ok.json"),new com.google.gson.Gson().toJson(Map.of("actualProcessRestart",true,"beforeFirstTickBodies",8,"exactJointRegistry",true,"survivingJoints",3,"brokenJointAbsent",true,"stableContactTicks",stable,"ordinaryKeyboardWalkDistance",walked,"freshFrames",frames,"maxAnchorError",maxAnchorError,"noReplacementBodies",true))+"\n");done=true;c.options.forwardKey.setPressed(false);c.scheduleStop();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
