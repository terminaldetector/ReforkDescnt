package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.flight.BoardingClient;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.network.*;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.client.*;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** Actual pilot keys + production C2S/S2C + integrated authoritative server. No boarding teleport. */
public final class NativeBoardingCheck {
    private static final BlockPos DECK=new BlockPos(8,Integer.getInteger("drmd.boarding.labY",2156),16);
    private static boolean started,prepared,finished,capture;
    private static volatile boolean ready,serverLanding,serverHandoff;
    private static int age,ticks,frames,bodyId=-1;
    private static volatile int phase;
    private static int synchronizedTicks,predictionSamples;
    private static Vec3d rideStart,walkStart;
    private static BoardingNetworking.State lastActive;
    private static volatile boolean jumpRelease;
    private static volatile boolean jumpSent;
    private record JumpStart(com.terminaldetector.drmd.world.contraption.boarding.DynamicBodyAttachment.State state,double seconds) {}
    private record JumpProof(JumpStart before,com.terminaldetector.drmd.world.contraption.boarding.DynamicBodyAttachment.State after) {}
    private static final ThreadLocal<JumpStart> JUMP_STEP=new ThreadLocal<>();
    private static volatile JumpProof jumpProof;
    private static double jumpNetworkError,previousPacketJumpDifference;
    private static volatile double departureError,maxPredictionError;
    private static final List<String> RESULTS=new ArrayList<>();
    private static String scene;
    public static void register(){ClientTickEvents.END_CLIENT_TICK.register(NativeBoardingCheck::tick);}
    private static net.minecraft.entity.Entity entity(BlockBodyEntity b){return b;}
    private static void require(boolean yes,String why){if(!yes){finished=true;MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException(why);}}
    /** Test-only observation of the exact authoritative motor call, never an input/state override. */
    public static void beforeJumpStep(com.terminaldetector.drmd.world.contraption.boarding.DynamicBodyAttachment.State state,
            com.terminaldetector.drmd.world.contraption.boarding.DynamicBodyAttachment.Input input,double seconds) {
        JUMP_STEP.remove();var server=MinecraftClient.getInstance().getServer();
        if(!finished&&server!=null&&server.isOnThread()&&phase==3&&jumpSent&&input.jump()&&state.grounded()&&state.support()==bodyId)
            JUMP_STEP.set(new JumpStart(state,seconds));
    }
    public static void afterJumpStep(com.terminaldetector.drmd.world.contraption.boarding.DynamicBodyAttachment.State state) {
        var before=JUMP_STEP.get();JUMP_STEP.remove();if(before!=null)jumpProof=new JumpProof(before,state);
    }
    public static void received(BoardingNetworking.State s) {
        if(finished)return;
        if(s.active()) {
            // Compare the same command on both peers. Sampling live positions in a scheduled
            // server task compared different simulation times (especially across a jump).
            if(s.epoch()==BoardingClient.epoch()) {
                var predicted=BoardingClient.predictedState(s.acknowledgment());
                if(predicted!=null) {
                    double error=predicted.feet().minus(new Vec3(s.x(),s.y(),s.z())).length();
                    maxPredictionError=Math.max(maxPredictionError,error);predictionSamples++;
                    if(error>.3)System.out.println("BOARDING_ACK_ERROR seq="+s.acknowledgment()+" error="+error+" predicted="+predicted+" authoritative="+s);
                }
            }
            lastActive=s;
        }
        else if(phase==1||phase==2||(phase==3&&!jumpSent)) {
            require(false,"support lease lost before jump: phase="+phase+" before="+lastActive+" after="+s);
        } else if(phase==3&&lastActive!=null) {
            var proof=jumpProof;require(proof!=null,"release packet has no observed authoritative jump step");
            var before=proof.before().state();var after=proof.after();
            departureError=Math.hypot(after.velocity().x()-before.velocity().x(),after.velocity().z()-before.velocity().z());
            require(departureError<.03,"boarding jump lost carrier horizontal speed in one motor step: "+departureError);
            require(Math.abs(after.velocity().y()-before.velocity().y()-(.42*20-32*proof.before().seconds()))<.08,"boarding jump inheritance incorrect in one motor step: before="+before+" after="+after);
            jumpNetworkError=new Vec3(s.vx(),s.vy(),s.vz()).minus(after.velocity()).length();
            require(jumpNetworkError<1e-8,"release packet differs from the actual authoritative jump: "+jumpNetworkError);
            previousPacketJumpDifference=Math.hypot(s.vx()-lastActive.vx(),s.vz()-lastActive.vz());
            System.out.println("BOARDING_JUMP_SAME_STEP before="+before+" after="+after+" networkError="+jumpNetworkError+" previousPacketDifference="+previousPacketJumpDifference);
            jumpRelease=true;
        }
    }
    private static void tick(MinecraftClient c) {
        if(finished)return;
        if(++ticks>3600)require(false,"native boarding timeout phase="+phase+" ready="+ready+" state="+BoardingClient.state());
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)
            for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(c.player==null||c.world==null||c.getServer()==null)return;
        com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
        if(!started) {
            String runtime=c.runDirectory.toPath().getFileName().toString();
            require(runtime.equals("cubic-client-automation")||runtime.equals("boardingcheck"),"disposable boarding runtime required");
            started=true;c.setScreen(null);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            c.inGameHud.getChatHud().clear(false);c.options.hudHidden=true;c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;
            com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;
            com.terminaldetector.drmd.client.config.DescentConfig.planetFloor=false;
            com.terminaldetector.drmd.client.config.DescentConfig.levelSky=false;
            c.getServer().execute(()->{
                var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());
                com.terminaldetector.drmd.flight.FlightSystem.disable(p,DescentPlayerData.get(p));
                p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.setInvulnerable(true);p.sendAbilitiesUpdate();
                p.teleport(p.getServerWorld(),12,DECK.getY()+4,20,Set.of(),0,15);
            });return;
        }
        if(!prepared&&c.player.getY()>DECK.getY()-50&&loaded(c)) {
            prepared=true;c.getServer().execute(()->setup(c));return;
        }
        if(!ready||capture)return;
        var body=c.world.getEntityById(bodyId);if(!(body instanceof BlockBodyEntity deck))return;
        age++;
        if(phase==4&&age%20==0) {
            System.out.println("BOARDING_AIR client="+c.player.getPos()+" velocity="+c.player.getVelocity()+" ground="+c.player.isOnGround()+" flying="+c.player.getAbilities().flying+" noGravity="+c.player.hasNoGravity()+" alive="+c.player.isAlive()+" screen="+c.currentScreen+" flight="+com.terminaldetector.drmd.client.DescentClientState.enabled);
            c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());System.out.println("BOARDING_AIR server="+p.getPos()+" velocity="+p.getVelocity()+" ground="+p.isOnGround()+" flying="+p.getAbilities().flying+" noGravity="+p.hasNoGravity()+" alive="+p.isAlive());});
        }
        if(phase==4&&age>120)require(false,"jump did not return to a real deck contact");
        if(BoardingClient.drives(c.player)&&age%5==0) {
            c.getServer().execute(()->{
                var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());

                if(age%20==0&&p.getServerWorld().getEntityById(bodyId) instanceof BlockBodyEntity serverBody)
                    System.out.println("BOARDING_TRACE phase="+phase+" player="+p.getPos()+" state="+BoardingSystem.state(p)
                        +" body="+entity(serverBody).getPos()+" q="+serverBody.rotation()+" v="+serverBody.physics().linearVelocity()+" L="+serverBody.physics().angularMomentum());
            });
        }
        boolean synchronizedBody=lastActive!=null&&lastActive.body()!=null&&entity(deck).getPos().distanceTo(
            new Vec3d(lastActive.body().x(),lastActive.body().y(),lastActive.body().z()))<.35;
        if(phase==0&&BoardingClient.drives(c.player)&&serverLanding) {
            if(!synchronizedBody||!BoardingClient.state().grounded())synchronizedTicks=0;else synchronizedTicks++;
            if(age%10==0)System.out.println("BOARDING_TRACK_SYNC actor="+c.player.getPos()+" body="+entity(deck).getPos()+" packet="+lastActive);
            if(synchronizedTicks<8)return;
            System.out.println("BOARDING_BASELINE actor="+c.player.getPos()+" body="+entity(deck).getPos()+" packet="+lastActive);
            rideStart=authoritativeFeet(deck,lastActive);request("landing");phase=1;age=0;
        } else if(phase==1&&age>=35) {
            require(BoardingClient.drives(c.player),"walking lease dropped while riding");
            var at=authoritativeFeet(deck,lastActive);require(at.distanceTo(rideStart)<.8,"idle player slides off translating deck: "+at.subtract(rideStart));
            require(entity(deck).getX()>DECK.getX()+5,"actual deck translation was not simulated");
            request("riding");phase=2;age=0;walkStart=at;
        } else if(phase==2) {
            if(age==1)c.options.forwardKey.setPressed(true);
            if(age==13)c.options.forwardKey.setPressed(false);
            if(age>=25) {
                require(BoardingClient.drives(c.player),"walking loses real deck contact");
                double distance=authoritativeFeet(deck,lastActive).z-walkStart.z;
                require(distance>1&&distance<3.5,"actual forward input did not walk relative to deck: "+distance);
                request("walking");phase=3;age=0;
            }
        } else if(phase==3) {
            if(age==1){jumpSent=true;c.options.jumpKey.setPressed(true);}
            if(age==3)c.options.jumpKey.setPressed(false);
            if(jumpRelease) {c.options.jumpKey.setPressed(false);request("jump");phase=4;age=0;}
        } else if(phase==4&&BoardingClient.drives(c.player)&&age>12) {
            var p=c.player;
            c.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(p.getX()+100,p.getY()+100,p.getZ(),true));
            ClientPlayNetworking.send(new BoardingNetworking.Input(BoardingClient.epoch(),BoardingClient.sequence()+10000,1,0,false,0,0,1));
            phase=5;age=0;
        } else if(phase==5&&age>=12) {
            require(BoardingClient.drives(c.player),"forged packet disrupted the valid controller");
            require(c.player.getPos().distanceTo(entity(deck).getPos())<8,"forged position escaped the physical deck");
            ClientPlayNetworking.send(new ModNetworking.ActionPayload("toggle"));phase=6;age=0;
        } else if(phase==6&&com.terminaldetector.drmd.client.DescentClientState.enabled&&serverHandoff) {
            require(!BoardingClient.drives(c.player),"flight and contact motors run together");
            require(predictionSamples>=30,"too few acknowledged prediction samples: "+predictionSamples);
            require(maxPredictionError<.8,"client/server boarding prediction diverged: "+maxPredictionError);
            request("sixdof-handoff");phase=7;
        }
    }
    private static Vec3d authoritativeFeet(BlockBodyEntity body,BoardingNetworking.State state) {
        require(state!=null&&state.body()!=null,"same-tick authoritative hull required");
        var b=state.body();var q=new com.terminaldetector.drmd.client.portal.PortalTransform.Quat(b.qx(),b.qy(),b.qz(),b.qw());
        return BoardingSystem.mc(body.localCentre().plus(q.inverse().rotate(new Vec3(state.x()-b.x(),state.y()-b.y(),state.z()-b.z()))));
    }
    private static boolean loaded(MinecraftClient c) {
        var a=com.terminaldetector.drmd.world.access.WorldAccess.of(c.world);
        for(int x=0;x<=1;x++)for(int y=(DECK.getY()>>4)-1;y<=(DECK.getY()>>4)+1;y++)for(int z=0;z<=2;z++)
            if(!a.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z)))return false;
        return true;
    }
    private static void setup(MinecraftClient c) {
        var w=c.getServer().getOverworld();
        for(var p:BlockPos.iterate(DECK.add(-4,-8,-4),DECK.add(20,10,16)))w.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        for(var p:BlockPos.iterate(DECK,DECK.add(7,0,7)))w.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState(),2);
        var body=BlockBodyEntity.assemble(w,DECK,DECK.add(7,0,7));body.physics().withLinearVelocity(new Vec3(3,0,0));entity(body).tick();bodyId=entity(body).getId();
        var player=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());
        player.getAbilities().allowFlying=false;player.getAbilities().flying=false;player.sendAbilitiesUpdate();
        player.teleport(w,DECK.getX()+4,DECK.getY()+2.4,DECK.getZ()+4,Set.of(),0,15);
        w.setTimeOfDay(6000);ready=true;
        long launched=w.getTime();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_WORLD_TICK.register(world->{
            if(!ready||finished||world!=w)return;
            // A flying test ship has actual bounded propulsion and an attitude controller. A
            // freely floating plate correctly tips under an off-centre passenger in world gravity;
            // this fixture must not fake a stable ship by overwriting its pose or carrying players.
            Vec3 desired=new Vec3(3,0,0),dv=desired.minus(body.physics().linearVelocity()).scaled(.3);
            if(dv.length()>.25)dv=dv.scaled(.25/dv.length());
            body.applyPhysicsImpulse(entity(body).getPos(),new Vec3d(dv.x(),dv.y(),dv.z()).multiply(body.physicsMass()));
            var target=com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.rotationByRadians(new Vec3(0,1,0),(w.getTime()-launched)*.05*.1);
            var error=com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.hamiltonProduct(target,body.rotation().inverse()).normalized();
            if(error.w()<0)error=new com.terminaldetector.drmd.client.portal.PortalTransform.Quat(-error.x(),-error.y(),-error.z(),-error.w());
            Vec3 axis=new Vec3(error.x(),error.y(),error.z());double angle=2*Math.acos(Math.max(-1,Math.min(1,error.w())));
            Vec3 acceleration=axis.normalized().scaled(36*angle).minus(body.physics().angularVelocity().minus(new Vec3(0,.1,0)).scaled(12));
            Vec3 torque=body.physics().worldInertia().transform(acceleration);double limit=body.physicsMass()*12;
            if(torque.length()>limit)torque=torque.scaled(limit/torque.length());
            body.applyPhysicsTorque(new Vec3d(torque.x(),torque.y(),torque.z()));
            if(BoardingSystem.drives(player))serverLanding=true;
            if(DescentPlayerData.get(player).isEnabled()&&!BoardingSystem.drives(player)&&!player.hasVehicle())serverHandoff=true;
        });
        Vec3d[] old={player.getPos(),entity(body).getPos()};int[] steps={0};
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_WORLD_TICK.register(world->{if(world==w&&!finished){old[0]=player.getPos();old[1]=entity(body).getPos();}});
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(world->{
            if(world!=w||finished)return;
            if(++steps[0]<=20||steps[0]%20==0)System.out.println("BOARDING_STEP n="+steps[0]+" playerDelta="+player.getPos().subtract(old[0])+" bodyDelta="+entity(body).getPos().subtract(old[1])
                +" leased="+BoardingSystem.drives(player)+" state="+BoardingSystem.state(player));
        });
    }
    private static void request(String name){scene=name;frames=0;capture=true;}
    public static void rendered() {
        if(!capture||finished)return;var c=MinecraftClient.getInstance();
        if(c.currentScreen!=null||c.player==null||c.world==null)return;
        if(++frames<3)return;
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            var dir=c.runDirectory.toPath().resolve("boarding-frames");Files.createDirectories(dir);image.writeTo(dir.resolve(scene+".png"));
            RESULTS.add("{\"scene\":\""+scene+"\",\"frames\":"+frames+"}");
            System.out.println("BOARDING_CLIENT "+scene+" PASS state="+BoardingClient.state());capture=false;
            if(phase==7) {
                Files.writeString(c.runDirectory.toPath().resolve("boarding-ok.json"),"{\"actualInputs\":true,\"authoritativeContact\":true,\"worldUpOnly\":true,\"physicalThrusters\":true,\"yawMotion\":true,\"teleportFollow\":false,\"sameStepJump\":true,\"jumpReleaseNetworkError\":"+jumpNetworkError+",\"previousPacketJumpDifference\":"+previousPacketJumpDifference+",\"jumpWorldVelocityError\":"+departureError+",\"predictionSamples\":"+predictionSamples+",\"maxPredictionError\":"+maxPredictionError+",\"scenes\":["+String.join(",",RESULTS)+"]}\n");
                finished=true;c.options.forwardKey.setPressed(false);c.options.jumpKey.setPressed(false);c.scheduleStop();
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
