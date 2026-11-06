package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.flight.BoardingClient;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import com.terminaldetector.drmd.network.BoardingNetworking;
import com.terminaldetector.drmd.physics.collision.BodyJointConstraint;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.*;
import net.minecraft.client.option.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** Actual joint world phase, production hull renderer and a real pilot on a sliding component. */
public final class NativeJointCheck {
    private static final int Y=Integer.getInteger("drmd.joints.labY",2156);
    private static final Vec3 ZERO=new Vec3(0,0,0);
    private static final String[] NAMES={"fixed-components","hinge-motor","moving-platform","broken-joint","joined-deck-transfer"};
    private static final Set<Integer> DRAWN=new HashSet<>();private static final List<String> RESULTS=new ArrayList<>();
    private static volatile boolean ready,broken;private static boolean started,prepared,finished;
    private static volatile int phase;private static int ticks,frames,phaseAge,predictionSamples,platformSamples;
    private static volatile int[] ids;private static volatile BlockBodyEntity[] bodies;private static volatile BodyJointLink[] links;
    private static double predictionError,platformStart=Double.NaN,platformTravel,maxAnchorError,breakVelocityError;
    private static volatile double serverAnchorError,serverMotorError,serverBreakVelocityError;
    private static volatile Vec3 expectedBreakVelocity;
    private static BoardingNetworking.State last;
    private static int seamLeftSamples,seamRightSamples,seamChanges,seamSupport=-1,seamEpoch=-1;
    private static boolean seamWalkingDone;private static volatile boolean seamPlaced;
    private static double seamAssemblyStart,seamAssemblyTravel;
    private static net.minecraft.entity.Entity entity(BlockBodyEntity b){return b;}
    private static void require(boolean value,String message){if(!value){finished=true;MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException(message);}}
    public static void register(){NativeJointReloadCheck.registerWriter();ClientTickEvents.END_CLIENT_TICK.register(NativeJointCheck::tick);}
    public static void drew(BlockBodyEntity b){if(ready&&!finished)DRAWN.add(entity(b).getId());}
    public static void received(BoardingNetworking.State s) {
        if(!ready||finished)return;
        if(phase==4&&seamLeftSamples>0)require(s.active(),"joined deck transition released the actual boarding lease: before="+last+" after="+s);
        if(!s.active())return;
        if(phase==4&&s.grounded()&&(s.support()==ids[6]||s.support()==ids[7])) {
            if(seamEpoch<0)seamEpoch=s.epoch();require(s.epoch()==seamEpoch,"support transition reset the input epoch");
            if(seamSupport!=-1&&seamSupport!=s.support())seamChanges++;
            seamSupport=s.support();if(s.support()==ids[6])seamLeftSamples++;else seamRightSamples++;
        }
        if(s.epoch()==BoardingClient.epoch()) {
            var predicted=BoardingClient.predictedState(s.acknowledgment());
            if(predicted!=null){predictionSamples++;predictionError=Math.max(predictionError,predicted.feet().minus(new Vec3(s.x(),s.y(),s.z())).length());}
        }
        if(phase==2&&s.support()==ids[5]&&s.grounded())platformSamples++;
        last=s;
    }
    public static void camera(Camera camera) {
        if(!ready||finished||phase==2||phase==4)return;var c=MinecraftClient.getInstance();int first=phase==1?2:0;
        if(!(c.world.getEntityById(ids[first]) instanceof BlockBodyEntity a)||!(c.world.getEntityById(ids[first+1]) instanceof BlockBodyEntity b))return;
        var at=entity(a).getPos().add(entity(b).getPos()).multiply(.5);var access=(CameraAccessor)camera;
        access.drmd$invokeSetPos(at.add(7,5,11));access.drmd$invokeSetRotation(148,20);
    }
    private static void tick(MinecraftClient c) {
        if(finished)return;if(++ticks>2400)require(false,"joint client timeout phase="+phase+" ready="+ready+" state="+BoardingClient.state());
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)
            for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(c.player==null||c.world==null||c.getServer()==null)return;
        com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
        if(!started) {
            var runtime=c.runDirectory.toPath().getFileName().toString();require(runtime.equals("jointcheck")||runtime.equals("cubic-client-automation"),"disposable joint runtime required");
            started=true;c.setScreen(null);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);c.inGameHud.getChatHud().clear(false);
            c.options.hudHidden=true;c.options.setPerspective(Perspective.THIRD_PERSON_BACK);c.options.getCloudRenderMode().setValue(CloudRenderMode.OFF);
            com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;
            com.terminaldetector.drmd.client.config.DescentConfig.planetFloor=false;com.terminaldetector.drmd.client.config.DescentConfig.levelSky=false;
            c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());com.terminaldetector.drmd.flight.FlightSystem.disable(p,DescentPlayerData.get(p));p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.setInvulnerable(true);p.sendAbilitiesUpdate();p.teleport(p.getServerWorld(),27,Y+4,19,Set.of(),0,15);});return;
        }
        if(!prepared&&c.player.getY()>Y-50&&loaded(c)){prepared=true;c.getServer().execute(()->setup(c));return;}
        if(!ready)return;phaseAge++;
        if(phase==4&&seamPlaced) {
            var state=BoardingClient.state();var parent=(BlockBodyEntity)c.world.getEntityById(ids[6]);
            if(parent!=null&&state!=null&&state.grounded()&&(state.support()==ids[6]||state.support()==ids[7])) {
                double x=parent.worldToLocal(c.player.getPos()).x;
                if(x>3.8)seamWalkingDone=true;
                c.options.leftKey.setPressed(!seamWalkingDone);
                require(x>1.8&&x<5.5,"pilot left the joined physical deck: "+x);
            }
        }
        if(phase==2) {
            if(phaseAge==1)c.options.forwardKey.setPressed(true);if(phaseAge==6)c.options.forwardKey.setPressed(false);
            var state=BoardingClient.state();
            if(state!=null&&state.grounded()&&state.support()==ids[5]) {
                var body=(BlockBodyEntity)c.world.getEntityById(ids[5]);if(body!=null){var local=body.worldToLocal(c.player.getPos());require(local.x>-.2&&local.x<3.2&&local.z>-.2&&local.z<3.2,"pilot left the actual sliding platform: "+local);}
            }
        }
    }
    private static boolean loaded(MinecraftClient c) {
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(c.world);
        for(int x=0;x<=2;x++)for(int y=(Y>>4)-1;y<=(Y>>4)+1;y++)for(int z=0;z<=2;z++)if(!access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;return true;
    }
    private static List<BlockBodyEntity.Cell> box(int x,int y,int z) {
        var out=new ArrayList<BlockBodyEntity.Cell>();for(int a=0;a<x;a++)for(int b=0;b<y;b++)for(int d=0;d<z;d++)out.add(new BlockBodyEntity.Cell(new BlockPos(a,b,d),Blocks.IRON_BLOCK.getDefaultState()));return out;
    }
    private static BodyJointLink link(BlockBodyEntity a,BlockBodyEntity b,BlockPos ca,Vec3 pa,BlockPos cb,Vec3 pb,BodyJointConstraint.Kind kind,double upper,double speed,double force) {
        return new BodyJointLink(UUID.randomUUID(),new BodyJointLink.Endpoint(entity(a).getUuid(),ca,pa,Quat.IDENTITY),new BodyJointLink.Endpoint(entity(b).getUuid(),cb,pb,Quat.IDENTITY),kind,0,upper,speed,force,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
    }
    private static Vec3d anchor(BlockBodyEntity b,BodyJointLink.Endpoint e){return b.localToWorld(new Vec3d(e.point().x(),e.point().y(),e.point().z()));}
    private static void setup(MinecraftClient c) {
        var w=c.getServer().getOverworld();for(var p:BlockPos.iterate(new BlockPos(4,Y-4,12),new BlockPos(36,Y+8,40)))w.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        bodies=new BlockBodyEntity[]{BlockBodyEntity.createLocal(w,box(2,2,2),new Vec3d(8,Y,16)),BlockBodyEntity.createLocal(w,box(2,2,2),new Vec3d(10,Y,16)),
            BlockBodyEntity.createLocal(w,box(2,2,2),new Vec3d(8,Y,32)),BlockBodyEntity.createLocal(w,box(2,2,2),new Vec3d(10,Y,32)),
            BlockBodyEntity.createLocal(w,box(6,1,6),new Vec3d(20,Y,16)),BlockBodyEntity.createLocal(w,box(3,1,3),new Vec3d(26,Y,17.5)),
            BlockBodyEntity.createLocal(w,box(3,1,6),new Vec3d(20,Y,32)),BlockBodyEntity.createLocal(w,box(3,1,6),new Vec3d(23,Y,32))};
        links=new BodyJointLink[]{link(bodies[0],bodies[1],new BlockPos(1,0,1),new Vec3(2,1,1),new BlockPos(0,0,1),new Vec3(0,1,1),BodyJointConstraint.Kind.FIXED,0,0,0),
            link(bodies[2],bodies[3],new BlockPos(1,0,1),new Vec3(2,1,1),new BlockPos(0,0,1),new Vec3(0,1,1),BodyJointConstraint.Kind.MOTOR,0,1.2,40),
            link(bodies[4],bodies[5],new BlockPos(5,0,3),new Vec3(6,.5,3),new BlockPos(0,0,1),new Vec3(0,.5,1.5),BodyJointConstraint.Kind.SLIDER,3,0,60),
            link(bodies[6],bodies[7],new BlockPos(2,0,3),new Vec3(3,.5,3),new BlockPos(0,0,3),new Vec3(0,.5,3),BodyJointConstraint.Kind.FIXED,0,0,0)};
        var registry=BodyJointRegistry.get(w);for(int i=0;i<4;i++)registry.add(w,bodies[2*i],bodies[2*i+1],links[i]);
        ids=Arrays.stream(bodies).mapToInt(b->entity(b).getId()).toArray();bodies[1].applyPhysicsImpulse(bodies[1].physicsCentre(),new Vec3d(16,0,2));bodies[1].applyPhysicsTorque(new Vec3d(.05,.2,.1));
        var pilot=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());pilot.getAbilities().allowFlying=false;pilot.getAbilities().flying=false;pilot.sendAbilitiesUpdate();pilot.teleport(w,27.5,Y+1.05,19,Set.of(),0,15);w.setTimeOfDay(6000);ready=true;
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_WORLD_TICK.register(world->{
            if(world!=w||finished||!ready)return;
            for(int index:new int[]{0,4,6}) {
                var body=bodies[index];Vec3 desired=index==4||index==6&&phase==4?new Vec3(1.5,0,0):ZERO,dv=desired.minus(body.physics().linearVelocity()).scaled(.4);if(dv.length()>.25)dv=dv.scaled(.25/dv.length());
                body.applyPhysicsImpulse(body.physicsCentre(),BoardingSystem.mc(dv.scaled(body.physicsMass())));
                var q=body.rotation();if(q.w()<0)q=new Quat(-q.x(),-q.y(),-q.z(),-q.w());var axis=new Vec3(q.x(),q.y(),q.z());double angle=2*Math.acos(Math.max(-1,Math.min(1,q.w())));
                var acceleration=axis.normalized().scaled(-36*angle).minus(body.physics().angularVelocity().scaled(12));var torque=body.physics().worldInertia().transform(acceleration);double cap=body.physicsMass()*16;if(torque.length()>cap)torque=torque.scaled(cap/torque.length());body.applyPhysicsTorque(BoardingSystem.mc(torque));
            }
            registry.configure(links[2].id(),phase==2?.8:0,60,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
            if(phase==3&&!broken&&registry.find(links[0].id()).isPresent()){registry.configure(links[0].id(),0,0,1,Double.POSITIVE_INFINITY);bodies[1].applyPhysicsImpulse(bodies[1].physicsCentre(),new Vec3d(40,0,0));expectedBreakVelocity=bodies[1].physics().linearVelocity();}
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(world->{
            if(world!=w||finished||!ready)return;
            if(phase<3)for(int i=0;i<2;i++)serverAnchorError=Math.max(serverAnchorError,anchor(bodies[2*i],links[i].a()).distanceTo(anchor(bodies[2*i+1],links[i].b())));
            if(phase==1)serverMotorError=Math.abs(bodies[3].physics().angularVelocity().x()-bodies[2].physics().angularVelocity().x()-1.2);
            if(phase==3&&!broken&&registry.find(links[0].id()).isEmpty()) {
                require(BodyJointSystem.stats(w).broken()>0,"native connection vanished without an actual force break");
                serverBreakVelocityError=bodies[1].physics().linearVelocity().minus(expectedBreakVelocity).length();require(serverBreakVelocityError<1e-8&&bodies[1].physics().linearVelocity().x()>4,"broken component lost its real launch momentum: "+serverBreakVelocityError);broken=true;
            }
            if(phase==3&&broken&&phaseAge>12){for(int i=0;i<2;i++){bodies[i].physics().withLinearVelocity(ZERO).withAngularMomentum(ZERO);bodies[i].publishPhysicsState();}}
        });
    }
    public static void rendered() {
        if(!ready||finished)return;var c=MinecraftClient.getInstance();if(c.currentScreen!=null||c.player==null)return;
        int a=phase==4?6:phase==1?2:phase==2?4:0;boolean visible=DRAWN.contains(ids[a])&&DRAWN.contains(ids[a+1]);DRAWN.clear();if(!visible)return;
        if(phase==2&&(last==null||!last.grounded()||last.support()!=ids[5]||!BoardingClient.drives(c.player)))return;
        if(phase==3&&!broken)return;
        if(phase==4&&(!seamWalkingDone||seamRightSamples<12||last==null||!last.grounded()||last.support()!=ids[7]))return;
        if(++frames<90)return;
        var parent=(BlockBodyEntity)c.world.getEntityById(ids[a]);var child=(BlockBodyEntity)c.world.getEntityById(ids[a+1]);
        if(phase<2){double e=anchor(parent,links[phase].a()).distanceTo(anchor(child,links[phase].b()));maxAnchorError=Math.max(maxAnchorError,e);require(serverAnchorError<.02&&e<.12,"joint anchor drift exceeds native render allowance: server="+serverAnchorError+" client="+e);}
        if(phase==1)require(serverMotorError<.03,"native hinge motor did not reach its requested speed: "+serverMotorError);
        if(phase==2) {
            platformTravel=anchor(child,links[2].b()).subtract(anchor(parent,links[2].a())).x-platformStart;
            require(platformTravel>.4&&platformTravel<3.1&&platformSamples>=30,"real moving platform/player proof incomplete: travel="+platformTravel+" samples="+platformSamples);
            require(predictionSamples>=30&&predictionError<.8,"joint pilot prediction incomplete/diverged: "+predictionError);
        }
        if(phase==4) {
            seamAssemblyTravel=entity(bodies[6]).getX()-seamAssemblyStart;
            require(seamLeftSamples>=3&&seamRightSamples>=12&&seamChanges==1,"joined seam contact proof incomplete/oscillating: left="+seamLeftSamples+" right="+seamRightSamples+" changes="+seamChanges);
            require(seamAssemblyTravel>.4&&predictionSamples>=40&&predictionError<.8,"moving joined deck/prediction proof incomplete: travel="+seamAssemblyTravel+" error="+predictionError);
        }
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            var dir=c.runDirectory.toPath().resolve("joint-frames");Files.createDirectories(dir);image.writeTo(dir.resolve(NAMES[phase]+".png"));RESULTS.add("{\"scene\":\""+NAMES[phase]+"\",\"frames\":"+frames+"}");System.out.println("JOINT_CLIENT "+NAMES[phase]+" PASS");
            phase++;frames=0;phaseAge=0;
            if(phase==2){var pa=(BlockBodyEntity)c.world.getEntityById(ids[4]);var ch=(BlockBodyEntity)c.world.getEntityById(ids[5]);platformStart=anchor(ch,links[2].b()).subtract(anchor(pa,links[2].a())).x;}
            if(phase==4) {
                c.options.forwardKey.setPressed(false);seamAssemblyStart=entity(bodies[6]).getX();
                c.getServer().execute(()->{
                    var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());var body=bodies[6];var at=body.localToWorld(new Vec3d(2.4,1.001,3));
                    p.teleport(p.getServerWorld(),at.x,at.y,at.z,Set.of(),0,15);p.setVelocity(BoardingSystem.mc(body.physics().linearVelocity().scaled(.05)));seamPlaced=true;
                });
            }
            if(phase==5){NativeJointReloadCheck.watch(bodies);Files.writeString(c.runDirectory.toPath().resolve("joints-ok.json"),"{\"actualJoints\":true,\"actualRenderer\":true,\"actualPlatformPlayer\":true,\"forceBreak\":true,\"teleportFollow\":false,\"maxServerAnchorError\":"+serverAnchorError+",\"maxClientAnchorError\":"+maxAnchorError+",\"motorSpeedError\":"+serverMotorError+",\"platformTravel\":"+platformTravel+",\"breakVelocityError\":"+serverBreakVelocityError+",\"seamLeftSamples\":"+seamLeftSamples+",\"seamRightSamples\":"+seamRightSamples+",\"seamSupportChanges\":"+seamChanges+",\"seamAssemblyTravel\":"+seamAssemblyTravel+",\"platformSamples\":"+platformSamples+",\"predictionSamples\":"+predictionSamples+",\"maxPredictionError\":"+predictionError+",\"scenes\":["+String.join(",",RESULTS)+"]}\n");finished=true;c.options.forwardKey.setPressed(false);c.options.leftKey.setPressed(false);c.scheduleStop();}
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
