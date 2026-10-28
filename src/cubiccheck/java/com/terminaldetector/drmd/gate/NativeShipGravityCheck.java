package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.flight.BoardingClient;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.network.*;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.client.*;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** Real controls, real server contacts and actual model/camera passes through full hull rotations. */
public final class NativeShipGravityCheck {
    private static final BlockPos DECK=new BlockPos(8,Integer.getInteger("drmd.boarding.labY",2156),16);
    private static final Vec3 UP=new Vec3(0,1,0),ZERO=new Vec3(0,0,0),VELOCITY=new Vec3(2,.4,.3);
    private static boolean started,prepared,finished,capture,jumpSent,jumpObserved,expectedRelease,released,commandSent;
    private static volatile boolean ready,settled,serverHandoff;
    private static volatile int mode;
    private static volatile long modeStarted;
    private static int phase,age,ticks,bodyId=-1,stable,frames,predictionSamples,modelFrames,cameraFrames;
    private static double maxError,modelError,jumpError,frameError,minUpY=1,maxUpX,maxUpZ;
    private static BoardingNetworking.State last;
    private static Vec3d walkStart;
    private static String scene;
    private static final Set<String> CAPTURED=new LinkedHashSet<>();
    private static final List<String> RESULTS=new ArrayList<>();
    private static net.minecraft.entity.Entity entity(BlockBodyEntity body){return body;}
    public static void register(){ClientTickEvents.END_CLIENT_TICK.register(NativeShipGravityCheck::tick);}
    private static void require(boolean yes,String why){if(!yes){finished=true;MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException(why);}}
    public static void received(BoardingNetworking.State s) {
        if(finished)return;
        if(!s.active()) {
            if(last!=null&&!expectedRelease&&phase<8)require(false,"ship contact/field lease lost: phase="+phase+" last="+last+" after="+s);
            released=true;return;
        }
        if(phase>=2&&phase<=4)require(s.grounded(),"rotating hull lost its real support contact: "+s);
        if(s.epoch()==BoardingClient.epoch()) {
            var predicted=BoardingClient.predictedState(s.acknowledgment());
            if(predicted!=null){predictionSamples++;maxError=Math.max(maxError,predicted.feet().minus(new Vec3(s.x(),s.y(),s.z())).length());}
        }
        if(s.body()!=null&&s.pose().gravityOwner()>=0) {
            Vec3 up=s.pose().frame().rotate(UP),ship=new Quat(s.body().qx(),s.body().qy(),s.body().qz(),s.body().qw()).rotate(UP);
            if(phase>=2&&phase<=5&&s.grounded())frameError=Math.max(frameError,1-up.dot(ship));
            minUpY=Math.min(minUpY,up.y());maxUpX=Math.max(maxUpX,Math.abs(up.x()));maxUpZ=Math.max(maxUpZ,Math.abs(up.z()));
        }
        if(phase==5&&jumpSent&&!jumpObserved&&last!=null&&last.grounded()&&!s.grounded()) {
            require(s.pose().gravityOwner()==bodyId,"airborne character escaped the ship field");
            Vec3 delta=new Vec3(s.vx()-last.vx(),s.vy()-last.vy(),s.vz()-last.vz()),up=last.pose().frame().rotate(UP);
            jumpError=Math.abs(delta.dot(up)-6.8);
            require(jumpError<.1,"ship jump or head clearance incorrect: error="+jumpError+" last="+last+" after="+s);
            jumpObserved=true;
        }
        last=s;
    }
    public static void model(Vec3d renderedUp) {
        var p=MinecraftClient.getInstance().player;
        if(p==null||!BoardingClient.drives(p))return;
        var expected=PassengerFrame.up(BoardingClient.state().frame());
        modelError=Math.max(modelError,renderedUp.distanceTo(BoardingSystem.mc(expected)));modelFrames++;
    }
    public static void camera() {
        var p=MinecraftClient.getInstance().player;
        if(p!=null&&BoardingClient.drives(p))cameraFrames++;
    }
    private static void tick(MinecraftClient c) {
        if(finished)return;if(++ticks>2400)require(false,"ship gravity client timeout phase="+phase+" age="+age+" state="+BoardingClient.state());
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)
            for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(c.player==null||c.world==null||c.getServer()==null)return;
        com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
        if(!started) {
            String runtime=c.runDirectory.toPath().getFileName().toString();require(runtime.equals("shipboardingcheck")||runtime.equals("cubic-client-automation"),"disposable ship gravity runtime required");
            started=true;c.setScreen(null);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            c.inGameHud.getChatHud().clear(false);c.options.hudHidden=true;c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;
            com.terminaldetector.drmd.client.config.DescentConfig.planetFloor=false;com.terminaldetector.drmd.client.config.DescentConfig.levelSky=false;
            c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());
                com.terminaldetector.drmd.flight.FlightSystem.disable(p,DescentPlayerData.get(p));p.setInvulnerable(true);
                p.getAbilities().flying=true;p.getAbilities().allowFlying=true;p.sendAbilitiesUpdate();p.teleport(p.getServerWorld(),12,DECK.getY()+5,19,Set.of(),0,0);
            });return;
        }
        if(!prepared&&c.player.getY()>DECK.getY()-20&&loaded(c)){prepared=true;c.getServer().execute(()->setup(c));return;}
        if(!ready||capture)return;
        if(!(c.world.getEntityById(bodyId) instanceof BlockBodyEntity body))return;
        age++;var state=BoardingClient.state();
        if(state!=null) {
            var expected=PassengerFrame.box(BoardingSystem.pure(c.player.getPos()),c.player.getWidth(),c.player.getHeight(),state.frame()).bounds();var actual=c.player.getBoundingBox();
            require(Math.abs(expected.min().x()-actual.minX)+Math.abs(expected.min().y()-actual.minY)+Math.abs(expected.min().z()-actual.minZ)<1e-4,"rendered player has an upright collision box");
            var eyes=c.player.getPos().add(BoardingSystem.mc(PassengerFrame.up(state.frame())).multiply(c.player.getEyeHeight(c.player.getPose())));
            require(c.player.getEyePos().distanceTo(eyes)<1e-4,"interaction eyes disagree with physical frame");
        }
        if(phase==0) {
            if(state!=null&&state.grounded()&&state.gravityOwner()==bodyId&&last!=null&&last.body()!=null
                &&entity(body).getPos().distanceTo(new Vec3d(last.body().x(),last.body().y(),last.body().z()))<.35)stable++;else stable=0;
            if(stable>=8){walkStart=body.worldToLocal(c.player.getPos());request("ship-landing");phase=1;age=0;}
        } else if(phase==1) {
            if(age==1)c.options.forwardKey.setPressed(true);if(age==13)c.options.forwardKey.setPressed(false);
            if(age>=28){require(state!=null&&state.grounded(),"walking lost real interior support");double distance=body.worldToLocal(c.player.getPos()).z-walkStart.z;
                require(distance>1&&distance<3.5,"actual keys did not walk in the ship frame: "+distance);request("ship-walking");phase=2;}
        } else if(phase>=2&&phase<=4) {
            require(state!=null&&state.gravityOwner()==bodyId,"rotation lost the physical field");Vec3 up=body.rotation().rotate(UP);
            if(phase==2) {
                if(up.x()<-.97&&!CAPTURED.contains("roll-wall")){request("roll-wall");return;}
                if(up.y()<-.97&&!CAPTURED.contains("roll-ceiling")){request("roll-ceiling");return;}
                if(settled&&mode==1){require(CAPTURED.containsAll(List.of("roll-wall","roll-ceiling")),"actual full roll was not observed");request("roll-complete");phase=3;}
            } else if(phase==3) {
                if(up.z()>.97&&!CAPTURED.contains("pitch-wall")){request("pitch-wall");return;}
                if(up.y()<-.97&&!CAPTURED.contains("pitch-ceiling")){request("pitch-ceiling");return;}
                if(settled&&mode==2){require(CAPTURED.containsAll(List.of("pitch-wall","pitch-ceiling")),"actual full pitch was not observed");request("pitch-complete");phase=4;}
            } else {
                var w=body.physics().angularVelocity();
                if(Math.abs(w.x())>.2&&Math.abs(w.y())>.2&&Math.abs(w.z())>.2&&!CAPTURED.contains("combined-axes")){request("combined-axes");return;}
                if(settled&&mode==3){require(CAPTURED.contains("combined-axes"),"three angular axes were not actually simulated");request("combined-complete");phase=5;age=0;}
            }
        } else if(phase==5) {
            if(age==1){jumpSent=true;c.options.jumpKey.setPressed(true);}if(age==3)c.options.jumpKey.setPressed(false);
            if(jumpObserved){c.options.jumpKey.setPressed(false);request("ship-jump");phase=6;age=0;}
        } else if(phase==6) {
            if(state!=null&&state.grounded()&&age>12&&!commandSent){commandSent=true;c.getNetworkHandler().sendChatCommand("d6body gravity "+entity(body).getUuid()+" 0 0 0");}
            if(commandSent&&body.passengerGravity().lengthSquared()==0){expectedRelease=true;c.options.jumpKey.setPressed(true);phase=7;age=0;}
        } else if(phase==7) {
            if(age==2)c.options.jumpKey.setPressed(false);
            if(released&&!BoardingClient.drives(c.player)){c.options.jumpKey.setPressed(false);request("gravity-off-jump");phase=8;age=0;ClientPlayNetworking.send(new ModNetworking.ActionPayload("toggle"));}
        } else if(phase==8&&com.terminaldetector.drmd.client.DescentClientState.enabled&&serverHandoff) {
            require(predictionSamples>=250&&maxError<.8,"ship prediction incomplete or diverged: n="+predictionSamples+" error="+maxError);
            require(modelFrames>=40&&cameraFrames>=40&&modelError<.05,"actual model/camera passes disagree with character frame: model="+modelFrames+" camera="+cameraFrames+" error="+modelError);
            require(frameError<.01&&minUpY<-.98&&maxUpX>.98&&maxUpZ>.98,"physical full rotations not aligned: frameError="+frameError);
            request("flight-handoff");phase=9;
        }
    }
    private static boolean loaded(MinecraftClient c) {
        var a=com.terminaldetector.drmd.world.access.WorldAccess.of(c.world);
        for(int x=0;x<=1;x++)for(int y=(DECK.getY()>>4)-1;y<=(DECK.getY()>>4)+1;y++)for(int z=0;z<=2;z++)
            if(!a.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z)))return false;
        return true;
    }
    private static void startMode(MinecraftClient c,int next){settled=false;c.getServer().execute(()->{mode=next;settled=false;modeStarted=c.getServer().getOverworld().getTime();});}
    private static void setup(MinecraftClient c) {
        var w=c.getServer().getOverworld();for(var p:BlockPos.iterate(DECK.add(-8,-10,-8),DECK.add(18,14,18)))w.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        for(int x=0;x<8;x++)for(int z=0;z<8;z++){
            w.setBlockState(DECK.add(x,0,z),Blocks.IRON_BLOCK.getDefaultState(),2);
            if(!(x>=3&&x<=4&&z>=5&&z<=6))w.setBlockState(DECK.add(x,3,z),Blocks.IRON_BLOCK.getDefaultState(),2);
        }
        var body=BlockBodyEntity.assemble(w,DECK,DECK.add(7,3,7));body.physics().withLinearVelocity(VELOCITY);body.setPassengerGravity(new Vec3(0,-32,0));entity(body).tick();bodyId=entity(body).getId();
        var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.getAbilities().flying=false;p.getAbilities().allowFlying=false;p.sendAbilitiesUpdate();
        p.teleport(w,DECK.getX()+4,DECK.getY()+1.05,DECK.getZ()+3,Set.of(),0,0);w.setTimeOfDay(6000);ready=true;modeStarted=w.getTime();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_WORLD_TICK.register(world->{
            if(world!=w||finished||!ready)return;
            Vec3 dv=VELOCITY.minus(body.physics().linearVelocity()).scaled(.3);if(dv.length()>.25)dv=dv.scaled(.25/dv.length());
            body.applyPhysicsImpulse(entity(body).getPos(),BoardingSystem.mc(dv.scaled(body.physicsMass())));
            long elapsed=w.getTime()-modeStarted;double angle=mode==0?0:Math.min(2*Math.PI,elapsed*.05*1.1);
            Vec3 axis=mode==1?new Vec3(0,0,1):mode==2?new Vec3(1,0,0):new Vec3(1,1,1).normalized();
            Quat target=ImmPtlQuaternions.rotationByRadians(axis,angle);Vec3 spin=angle<2*Math.PI&&mode!=0?axis.scaled(1.1):ZERO;
            Quat error=ImmPtlQuaternions.hamiltonProduct(target,body.rotation().inverse()).normalized();
            if(error.w()<0)error=new Quat(-error.x(),-error.y(),-error.z(),-error.w());
            Vec3 turn=new Vec3(error.x(),error.y(),error.z());double radians=2*Math.acos(Math.max(-1,Math.min(1,error.w())));
            Vec3 acceleration=turn.normalized().scaled(36*radians).minus(body.physics().angularVelocity().minus(spin).scaled(12));
            Vec3 torque=body.physics().worldInertia().transform(acceleration);double cap=body.physicsMass()*16;if(torque.length()>cap)torque=torque.scaled(cap/torque.length());
            body.applyPhysicsTorque(BoardingSystem.mc(torque));
            if(mode!=0&&elapsed>160)settled=true;
            if(DescentPlayerData.get(p).isEnabled()&&!BoardingSystem.drives(p))serverHandoff=true;
            if(elapsed%40==0)System.out.println("SHIP_GRAVITY_TRACE phase="+phase+" mode="+mode+" elapsed="+elapsed+" player="+p.getPos()+" local="+body.worldToLocal(p.getPos())+" state="+BoardingSystem.state(p)+" body="+entity(body).getPos()+" q="+body.rotation());
        });
    }
    private static void request(String name){scene=name;capture=true;frames=0;}
    public static void rendered() {
        if(!capture||finished)return;var c=MinecraftClient.getInstance();if(c.player==null||c.world==null||c.currentScreen!=null)return;
        if(++frames<3)return;
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            var dir=c.runDirectory.toPath().resolve("ship-boarding-frames");Files.createDirectories(dir);image.writeTo(dir.resolve(scene+".png"));
            CAPTURED.add(scene);RESULTS.add("{\"scene\":\""+scene+"\",\"frames\":"+frames+"}");capture=false;
            System.out.println("SHIP_GRAVITY_CLIENT "+scene+" PASS state="+BoardingClient.state());
            if(scene.equals("ship-walking"))startMode(c,1);
            if(scene.equals("roll-complete"))startMode(c,2);
            if(scene.equals("pitch-complete"))startMode(c,3);
            if(phase==9) {
                Files.writeString(c.runDirectory.toPath().resolve("ship-boarding-ok.json"),"{\"actualInputs\":true,\"shipGravity\":true,\"orientedCollision\":true,\"teleportFollow\":false,\"predictionSamples\":"+predictionSamples+",\"maxPredictionError\":"+maxError+",\"jumpVelocityError\":"+jumpError+",\"maxFrameError\":"+frameError+",\"modelFrames\":"+modelFrames+",\"cameraFrames\":"+cameraFrames+",\"maxModelUpError\":"+modelError+",\"scenes\":["+String.join(",",RESULTS)+"]}\n");
                finished=true;c.options.forwardKey.setPressed(false);c.options.jumpKey.setPressed(false);c.scheduleStop();
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
