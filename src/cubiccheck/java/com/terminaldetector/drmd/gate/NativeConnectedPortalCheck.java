package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.flight.BoardingClient;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.physics.collision.BodyJointConstraint;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import com.terminaldetector.drmd.world.portal.mirror.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import java.nio.file.*;
import java.util.*;

/** ONE occupied, physically connected island. Only fixture staging and ONE launch pulse are authored. */
public final class NativeConnectedPortalCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.connectedPortal");
    private static final BlockPos A=new BlockPos(6,2156,5),B=new BlockPos(24,2156,12),MARK=B.east(16).south(6);
    private static final Vec3d START=new Vec3d(6,2156.04,-.1);
    private static final Vec3 ZERO=new Vec3(0,0,0),DELTA=new Vec3(0,0,4),OMEGA=new Vec3(.02,.025,.015);
    private record Hull(UUID uuid,int id,Vec3 p,Quat q,Vec3 v,Vec3 l,Vec3 force,Vec3 torque,long revision,boolean full){}
    private record State(long tick,Hull root,Hull child,Vec3 player,DynamicBodyAttachment.State attachment,int epoch,boolean flight,boolean flying,double anchorError){}
    private record Before(long tick,D6PortalTransform transform,Hull hull,Vec3 player,DynamicBodyAttachment.State attachment){}
    private record Crossing(Before before,Hull after){}
    private static final Map<UUID,Before> OPEN=new LinkedHashMap<>();
    private static final List<Crossing> CROSSED=new ArrayList<>();
    private static final List<State> SERVER=new ArrayList<>();
    private static final List<Map<String,Object>> CLIENT=new ArrayList<>(),FRAMES=new ArrayList<>();
    private static volatile UUID playerId;private static volatile int rootId=-1,childId=-1;
    private static BlockBodyEntity root,child;private static BodyJointLink link;
    private static volatile State live,afterCross;private static volatile String failure;
    private static volatile boolean staged,placed,launchRequested,launched,finished;
    private static boolean beforeCaptured,rootDrawn,childDrawn,jumped;private static int ticks,phase,age,captureFrames,stagingTeleports,launchPulses,heldTicks;
    private static long boot,spawnedTick;private static boolean admitted;private static String capture;private static Vec3d lastClient;
    private static final List<Map<String,Object>> DRAW=new ArrayList<>();
    private NativeConnectedPortalCheck(){}
    public static void register(){boot=System.nanoTime();ServerTickEvents.END_SERVER_TICK.register(NativeConnectedPortalCheck::server);ClientTickEvents.END_CLIENT_TICK.register(c->{try{tick(c);}catch(Throwable error){finish(c,"FAIL","client observer: "+error);}});}
    /** Explicit Minecraft owner keeps inherited Entity members remappable in this optional source set. */
    private static Entity entity(BlockBodyEntity body){return body;}
    private static Vec3 p(Vec3d v){return new Vec3(v.x,v.y,v.z);}private static Vec3d mc(Vec3 v){return new Vec3d(v.x(),v.y(),v.z());}
    private static void require(boolean condition,String reason){if(!condition)throw new IllegalStateException(reason);}
    private static boolean loaded(World w){var access=WorldAccess.of(w);if(access.backend()!=CubicAvailability.Backend.NATIVE_CUBIC)return false;
        for(int x=0;x<=2;x++)for(int y=134;y<=135;y++)for(int z=-1;z<=1;z++)if(!access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;return true;}
    private static Hull hull(BlockBodyEntity b){return new Hull(entity(b).getUuid(),entity(b).getId(),b.massPosition(),b.rotation(),b.physics().linearVelocity(),b.physics().angularMomentum(),b.physics().accumulatedForce(),b.physics().accumulatedTorque(),b.shapeRevision(),loaded(entity(b).getWorld()));}
    private static void server(MinecraftServer server){
        if(!ENABLED||finished||playerId==null||failure!=null)return;
        try{
            var player=server.getPlayerManager().getPlayer(playerId);if(player==null)return;var w=player.getServerWorld();
            if(!staged){require(w.getBottomY()==-16384&&w.getHeight()==32768&&WorldAccess.of(w).backend()==CubicAvailability.Backend.NATIVE_CUBIC,"actual native dimension required");
                FlightSystem.disable(player,DescentPlayerData.get(player));player.changeGameMode(GameMode.CREATIVE);player.getAbilities().flying=true;player.sendAbilitiesUpdate();
                player.teleport(w,START.x,START.y,START.z,Set.of(),0,25);stagingTeleports++;w.setTimeOfDay(6000);staged=true;
                System.out.println("CONNECTED_PORTAL_INITIAL_STAGING teleports="+stagingTeleports+" p="+player.getPos());return;}
            if(!placed){if(!loaded(w))return;
                for(var pos:List.of(A,B))require(WorldAccess.of(w).readIfLoaded(pos).orElseThrow().isAir(),"loaded AIR required for panel "+pos);
                for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)require(WorldAccess.of(w).readIfLoaded(MARK.add(0,y,z)).orElseThrow().isAir(),"loaded AIR required for marker");
                w.setBlockState(A,ModWorldBlocks.PORTAL_PANEL.getDefaultState().with(PortalPanelBlock.FACING,Direction.NORTH).with(PortalPanelBlock.LINKED,true),2);
                w.setBlockState(B,ModWorldBlocks.PORTAL_PANEL.getDefaultState().with(PortalPanelBlock.FACING,Direction.EAST).with(PortalPanelBlock.LINKED,true),2);
                ((PortalPanelBlockEntity)w.getBlockEntity(A)).setLink(null,B,w.getRegistryKey());((PortalPanelBlockEntity)w.getBlockEntity(B)).setLink(null,A,w.getRegistryKey());
                for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)w.setBlockState(MARK.add(0,y,z),Blocks.EMERALD_BLOCK.getDefaultState(),2);
                var cells=new ArrayList<BlockBodyEntity.Cell>();for(int x=0;x<2;x++)for(int z=0;z<2;z++)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,0,z),Blocks.IRON_BLOCK.getDefaultState()));
                root=BlockBodyEntity.createLocal(w,cells,new Vec3d(5,2155,-1));
                child=BlockBodyEntity.createLocal(w,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.GOLD_BLOCK.getDefaultState()),new BlockBodyEntity.Cell(new BlockPos(0,0,1),Blocks.GOLD_BLOCK.getDefaultState())),new Vec3d(7,2155,-1));
                root.setPhysicsGravity(false);child.setPhysicsGravity(false);root.setPassengerGravity(new Vec3(0,-.5,0));rootId=entity(root).getId();childId=entity(child).getId();
                link=new BodyJointLink(UUID.randomUUID(),new BodyJointLink.Endpoint(entity(root).getUuid(),new BlockPos(1,0,0),new Vec3(2,.5,.5),Quat.IDENTITY),
                    new BodyJointLink.Endpoint(entity(child).getUuid(),BlockPos.ORIGIN,new Vec3(0,.5,.5),Quat.IDENTITY),BodyJointConstraint.Kind.FIXED,0,0,0,0,10000,10000,false);
                BodyJointRegistry.get(w).add(w,root,child,link);spawnedTick=server.getTicks();placed=true;
                System.out.println("CONNECTED_PORTAL_AUTHORED worldBlocks=11 localCells=6 actualJoint="+link+" localGravity=0.5");
            }
            require(!entity(root).isRemoved()&&!entity(child).isRemoved(),"authored hull actually removed before/after native entity admission");
            if(!admitted){
                boolean ready=true;for(var body:List.of(root,child)){var uuidEntry=w.getEntity(entity(body).getUuid());var idEntry=w.getEntityById(entity(body).getId());
                    require((uuidEntry==null||uuidEntry==body)&&(idEntry==null||idEntry==body),"native entity index contains a different identity");ready&=uuidEntry==body&&idEntry==body;}
                require(server.getTicks()-spawnedTick<=120,"observer entity-index admission premise absent after120ordinary server ticks; material FULL alone is insufficient");
                if(!ready)return;admitted=true;player.getAbilities().flying=false;player.sendAbilitiesUpdate();ModNetworking.syncPlayer(player,DescentPlayerData.get(player));
                System.out.println("CONNECTED_PORTAL_ENTITY_ADMITTED elapsedTicks="+(server.getTicks()-spawnedTick)+" UUIDandID=true viewerFlying=false");
            }
            require(w.getEntity(entity(root).getUuid())==root&&w.getEntityById(rootId)==root&&w.getEntity(entity(child).getUuid())==child&&w.getEntityById(childId)==child,"original admitted hull index identities required");
            require(BodyJointRegistry.get(w).find(link.id()).filter(link::equals).isPresent(),"original material joint disappeared or changed");
            var attachment=BoardingSystem.state(player);
            if(launchRequested&&!launched){require(attachment!=null&&attachment.grounded()&&attachment.support()==rootId&&!DescentPlayerData.get(player).isEnabled()&&!player.getAbilities().flying,"launch requires real occupied walking contact");
                Vec3 pivot=root.massPosition();for(var body:List.of(root,child)){Vec3 delta=DELTA.plus(OMEGA.cross(body.massPosition().minus(pivot)));body.physics().applyImpulse(delta.scaled(body.physics().mass()),ZERO);body.physics().withAngularVelocity(OMEGA);body.publishPhysicsState();}
                player.setVelocity(player.getVelocity().add(mc(DELTA.plus(OMEGA.cross(p(player.getPos()).minus(pivot)))).multiply(.05)));player.velocityModified=true;launchPulses++;launched=true;
                System.out.println("CONNECTED_PORTAL_SINGLE_LAUNCH tick="+server.getTicks()+" player="+player.getPos()+" support="+attachment.support());
            }
            double error=root.localToWorld(new Vec3d(2,.5,.5)).distanceTo(child.localToWorld(new Vec3d(0,.5,.5)));
            require(error<.08,"physical joint anchor separation "+error);
            var now=new State(server.getTicks(),hull(root),hull(child),p(player.getPos()),BoardingSystem.state(player),BoardingSystem.epoch(player),DescentPlayerData.get(player).isEnabled(),player.getAbilities().flying,error);live=now;
            synchronized(SERVER){require(SERVER.size()<2048,"bounded server journal exhausted");SERVER.add(now);}
            if(CROSSED.size()==2&&afterCross==null){
                require(CROSSED.get(0).before.tick==CROSSED.get(1).before.tick,"connected bodies did not cross in one server transaction tick");
                require(CROSSED.get(0).before.transform.equals(CROSSED.get(1).before.transform),"connected bodies used different rigid transforms");
                var first=CROSSED.getFirst().before;require(first.tick==now.tick,"group closure was not observed at the actual transfer tick");
                require(now.player.minus(first.transform.transformPoint(first.player)).length()<1e-7,"occupant did not share the exact hull rigid map");
                require(first.attachment!=null&&now.attachment!=null&&now.attachment.support()==first.attachment.support()&&now.attachment.gravityOwner()==first.attachment.gravityOwner(),"actual attachment owners lost at transfer");
                require(nearQuat(now.attachment.frame(),first.transform.transformOrientation(first.attachment.frame()),1e-10),"occupant contact frame was not transformed once");
                require(now.attachment.velocity().minus(first.transform.transformVelocity(first.attachment.velocity())).length()<1e-7,"occupant world momentum was not transformed once");
                require(!now.flight&&!now.flying&&now.root.full&&now.child.full&&launchPulses==1&&stagingTeleports==1,"wrong actor authority, FULL or authored-movement counters");afterCross=now;
                System.out.println("CONNECTED_PORTAL_GROUP_CROSSED actual="+now);
            }
        }catch(Throwable error){failure=error.toString();error.printStackTrace();}
    }
    /** Passive observations around the existing real body method. No engine call/pose is supplied by this hook. */
    public static void crossing(BlockBodyEntity body,D6PortalTransform transform,boolean after){
        if(!ENABLED||finished||failure!=null||entity(body).getWorld().isClient||entity(body).getId()!=rootId&&entity(body).getId()!=childId)return;
        try{require(entity(body).getWorld() instanceof net.minecraft.server.world.ServerWorld,"server body required");var w=(net.minecraft.server.world.ServerWorld)entity(body).getWorld();
            require(w.getServer().isOnThread(),"owner-thread transfer observation required");var player=w.getServer().getPlayerManager().getPlayer(playerId);require(player!=null,"live occupant required");
            if(!after){require(!OPEN.containsKey(entity(body).getUuid())&&CROSSED.stream().noneMatch(c->c.after.uuid.equals(entity(body).getUuid())),"duplicate/nested body transfer");OPEN.put(entity(body).getUuid(),new Before(w.getServer().getTicks(),transform,hull(body),p(player.getPos()),BoardingSystem.state(player)));return;}
            var before=OPEN.remove(entity(body).getUuid());require(before!=null&&before.transform.equals(transform),"missing same-invocation before sample");var actual=hull(body);var old=before.hull;
            require(old.uuid.equals(actual.uuid)&&old.id==actual.id&&old.revision==actual.revision,"body identity or geometry recreated");
            require(actual.p.minus(transform.transformPoint(old.p)).length()<1e-8&&nearQuat(actual.q,transform.transformOrientation(old.q),1e-10),"physical COM/orientation rigid map mismatch");
            require(actual.v.minus(transform.transformVelocity(old.v)).length()<1e-8&&actual.l.minus(transform.transformAngularMomentum(old.l)).length()<1e-8,"physical linear/angular momentum map mismatch");
            require(actual.force.minus(transform.rotation().rotate(old.force)).length()<1e-8&&actual.torque.minus(transform.rotation().rotate(old.torque)).length()<1e-8,"pending physical force frame mismatch");
            require(old.v.length()>1&&old.l.length()>1e-5&&old.full&&actual.full,"missing moving/spinning/full physical premise");CROSSED.add(new Crossing(before,actual));
            System.out.println("CONNECTED_PORTAL_BODY_TRANSFER "+CROSSED.getLast());
        }catch(Throwable error){failure=error.toString();error.printStackTrace();}
    }
    private static boolean nearQuat(Quat a,Quat b,double epsilon){return 1-Math.abs(ImmPtlQuaternions.dot(a,b))<epsilon;}
    private static void tick(MinecraftClient c){
        if(finished)return;if(++ticks>1800||System.nanoTime()-boot>180_000_000_000L){finish(c,"FAIL","finite180s/1800tick deadline phase="+phase);return;}
        if(playerId==null&&c.currentScreen instanceof net.minecraft.client.gui.screen.ConfirmScreen prompt&&prompt.getTitle().getString().equals("Warning! Low disk space!"))for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("Continue"))b.onPress();
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)for(var node:p.children())if(node instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(failure!=null){finish(c,"FAIL",failure);return;}if(c.player==null||c.world==null||c.getServer()==null)return;
        DescentClient.markUserFlightChoice();
        if(playerId==null){require(c.runDirectory.getName().equals("cubic-client-automation")&&FabricLoader.getInstance().isModLoaded("bettercubicchunks"),"disposable actual native runtime required");playerId=c.player.getUuid();c.setScreen(null);c.options.setPerspective(Perspective.THIRD_PERSON_BACK);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);}
        var state=BoardingClient.state();var authority=live;
        if(CLIENT.size()>=2048){finish(c,"FAIL","bounded client journal exhausted");return;}
        var sample=new LinkedHashMap<String,Object>();sample.put("tick",ticks);sample.put("phase",phase);sample.put("p",p(c.player.getPos()));sample.put("attachment",state);sample.put("epoch",BoardingClient.epoch());sample.put("forwardHeld",c.options.forwardKey.isPressed());sample.put("hulls",clientHulls(c));CLIENT.add(sample);
        if(phase==0){
            if(!placed||authority==null||!loaded(c.world)||state==null||!state.grounded()||state.support()!=rootId||!BoardingClient.drives(c.player)||authority.attachment==null||!authority.attachment.grounded()||authority.attachment.support()!=rootId)return;
            if(!(c.world.getEntityById(rootId) instanceof BlockBodyEntity a)||!(c.world.getEntityById(childId) instanceof BlockBodyEntity b)||a.cells().size()!=4||b.cells().size()!=2||!entity(a).getUuid().equals(authority.root.uuid)||!entity(b).getUuid().equals(authority.child.uuid))return;
            if(++age<5)return;capture="before";phase=1;captureFrames=0;return;
        }
        if(phase==1&&beforeCaptured){launchRequested=true;phase=2;age=0;c.options.forwardKey.setPressed(true);lastClient=c.player.getPos();}
        if(phase==2){
            if(c.options.forwardKey.isPressed())heldTicks++;if(++age>=4)c.options.forwardKey.setPressed(false);
            if(lastClient!=null&&c.player.getPos().distanceTo(lastClient)>8)jumped=true;lastClient=c.player.getPos();
            if(afterCross!=null&&c.player.getX()>B.getX()+.9){phase=3;age=0;c.options.forwardKey.setPressed(false);}
            else if(age>240)finish(c,"FAIL","normal island physics did not transit actual linked panels");return;
        }
        if(phase==3&&++age>=8){
            if(!clientAgrees(c,authority,state)){if(age>80)finish(c,"FAIL","post-transit client hull/attachment agreement absent");return;}
            require(heldTicks>=2&&jumped&&afterCross!=null,"missing actual walking-input/client discontinuity witness");capture="after";captureFrames=0;phase=4;
        }
    }
    private static List<Map<String,Object>> clientHulls(MinecraftClient c){var out=new ArrayList<Map<String,Object>>();for(int id:new int[]{rootId,childId})if(c.world.getEntityById(id) instanceof BlockBodyEntity b){var view=BoardingClient.bodyView(b,1);out.add(Map.of("uuid",entity(b).getUuid(),"id",id,"p",b.massPosition(),"q",b.rotation(),"v",b.physics().linearVelocity(),"renderP",view==null?b.renderMassPosition(1):view.position(),"renderQ",view==null?b.renderRotation(1):view.rotation()));}return out;}
    private static boolean clientAgrees(MinecraftClient c,State authority,DynamicBodyAttachment.State state){
        if(authority==null||authority.attachment==null||!authority.attachment.grounded()||authority.attachment.support()!=rootId||state==null||!state.grounded()||state.support()!=rootId||state.gravityOwner()!=rootId||!BoardingClient.drives(c.player)||c.player.getPos().distanceTo(mc(authority.player))>.5)return false;
        if(!nearQuat(state.frame(),authority.attachment.frame(),.001))return false;
        for(var expected:List.of(authority.root,authority.child)){
            if(!(c.world.getEntityById(expected.id) instanceof BlockBodyEntity b)||!entity(b).getUuid().equals(expected.uuid)||b.shapeRevision()!=expected.revision||b.massPosition().minus(expected.p).length()>.7||!nearQuat(b.rotation(),expected.q,.001))return false;
            if(!nearQuat(b.renderRotation(1),expected.q,.002))return false;
        }return true;
    }
    public static void beforeFrame(){if(ENABLED){rootDrawn=false;childDrawn=false;DRAW.clear();}}
    /** Actual quaternion passed to the real renderer, including BoardingClient's predicted body view. */
    public static void drew(BlockBodyEntity b,Quat q,float delta){if(!ENABLED||finished||entity(b).getId()!=rootId&&entity(b).getId()!=childId)return;if(entity(b).getId()==rootId)rootDrawn=true;else childDrawn=true;
        var view=BoardingClient.bodyView(b,delta);if(DRAW.size()<16)DRAW.add(Map.of("uuid",entity(b).getUuid(),"id",entity(b).getId(),"q",q,"delta",delta,"p",view==null?b.renderMassPosition(delta):view.position()));}
    public static void rendered(){
        if(!ENABLED||finished||capture==null||!rootDrawn||!childDrawn||++captureFrames<3)return;var c=MinecraftClient.getInstance();if(c.currentScreen!=null||c.player==null)return;
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){var name=capture;var dir=c.runDirectory.toPath().resolve("connected-portal-frames");Files.createDirectories(dir);image.writeTo(dir.resolve(name+".png"));
            var frame=new LinkedHashMap<String,Object>();frame.put("name",name);frame.put("clientPos",p(c.player.getPos()));frame.put("cameraPos",p(c.gameRenderer.getCamera().getPos()));frame.put("actualDraws",List.copyOf(DRAW));frame.put("attachment",BoardingClient.state());frame.put("width",image.getWidth());frame.put("height",image.getHeight());FRAMES.add(frame);capture=null;
            if(name.equals("before"))beforeCaptured=true;else{require(phase==4&&clientAgrees(c,live,BoardingClient.state()),"actual completed after-frame lost client/server contact agreement");for(var draw:DRAW){var expected=(Integer)draw.get("id")==rootId?live.root:live.child;require(nearQuat((Quat)draw.get("q"),expected.q,.002)&&((Vec3)draw.get("p")).minus(expected.p).length()<1,"actual renderer still uses the old portal frame");}finish(c,"PASS","one real connected occupied island transit; actual render/PNG context, no pixelquality/performance/fullMP claim");}
        }catch(Throwable error){finish(c,"FAIL","actual frame: "+error);}
    }
    private static void finish(MinecraftClient c,String status,String reason){if(finished)return;finished=true;c.options.forwardKey.setPressed(false);
        try{var out=new LinkedHashMap<String,Object>();out.put("status",status);out.put("reason",reason);out.put("stagingTeleports",stagingTeleports);out.put("groupLaunchPulses",launchPulses);out.put("worldFixtureBlocks",placed?11:0);out.put("localFixtureCells",placed?6:0);out.put("fixtureJoint",link);out.put("heldForwardTicks",heldTicks);out.put("normalThirdPersonCamera",true);out.put("clientJump",jumped);out.put("crossings",List.copyOf(CROSSED));out.put("afterCross",afterCross);out.put("live",live);out.put("client",CLIENT);out.put("frames",FRAMES);synchronized(SERVER){out.put("server",List.copyOf(SERVER));}Files.writeString(c.runDirectory.toPath().resolve("connected-portal-outcome.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out)+"\n");System.out.println("CONNECTED_PORTAL_OUTCOME "+status+" "+reason);
        }catch(Throwable error){error.printStackTrace();}c.scheduleStop();}
}
