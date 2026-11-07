package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.mega.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** Actual local cuts/placement while ordinary captured-ship input stays held.
 * No body pose, velocity, angular momentum or fake engine state is assigned. */
public final class NativeUfoEngineCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.ufoEngines");
    private static SkyUfoEntity body;
    private static ServerPlayerEntity pilot;
    private static volatile int stage,offSamples,onSamples,offSixAxisInputs,onSixAxisInputs,axis;
    private static final java.util.concurrent.atomic.AtomicIntegerArray AXIS_SAMPLES=new java.util.concurrent.atomic.AtomicIntegerArray(6);
    private static volatile UfoControlSystem.Input lastInput;
    private static volatile long lastInputTick;
    private static double maxDriveVelocityError,maxDriveMomentumError;
    private record Drive(Vec3 velocity,Vec3 momentum,Quat rotation,com.terminaldetector.drmd.d6.D6Mat3 inertia,UfoControlSystem.Input input,int axis,long tick) {}
    private static Drive drive;
    private static long countedDriveTick=Long.MIN_VALUE;
    private static volatile double offDelta,onDelta,materialError,travel,turn;
    private static volatile boolean exterior;
    private static volatile Vec3d camera;
    private static volatile long repairedRevision;
    private static Vec3 offVelocity,onVelocity,onMomentum,startPosition;
    private static Quat onRotation;
    private static volatile Vec3 localVelocityDelta,localMomentumDelta;
    private static Quat startRotation;
    private static Map<BlockPos,net.minecraft.block.BlockState> original;
    private static net.minecraft.entity.Entity entity(SkyUfoEntity b){return b;}
    private static void require(boolean ok,String why){if(!ok){MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException(why);}}
    public static void watch(SkyUfoEntity b,ServerPlayerEntity p) {
        if(!ENABLED)return;body=b;pilot=p;require(b.activeThrusters()==4,"new UFO must carry four actual engines");
        original=grid();
        ServerTickEvents.END_WORLD_TICK.register(w->{
            if(w!=p.getServerWorld()||(stage!=2&&stage!=5&&stage!=7))return;
            require(UfoControlSystem.controls(p),"engine failure/repair cannot replace the captured-ship owner");
            require(BodySpatialIndex.loaded(w).stream().filter(other->((net.minecraft.entity.Entity)other).getUuid().equals(entity(b).getUuid())).count()==1,"one original physical UFO identity throughout engine edits");
            if(stage==7){require(b.activeThrusters()==4,"independent axis checks use the repaired engine bank");return;}
            if(stage==2){
                require(b.activeThrusters()==0,"all four real engine cells remain absent");offSamples++;
                offDelta=Math.max(offDelta,b.physics().linearVelocity().minus(offVelocity).length());
                require(offDelta<.2,"unpowered ship gained thrust while ordinary full input was held: "+offDelta);
                travel=b.massPosition().minus(startPosition).length();var q=b.rotation();
                turn=2*Math.acos(Math.min(1,Math.abs(q.x()*startRotation.x()+q.y()*startRotation.y()+q.z()*startRotation.z()+q.w()*startRotation.w())));
            }else{require(b.activeThrusters()==4,"repaired motors remain actual local material");onSamples++;onDelta=b.physics().linearVelocity().minus(onVelocity).length();localVelocityDelta=onRotation.inverse().rotate(b.physics().linearVelocity().minus(onVelocity));localMomentumDelta=onRotation.inverse().rotate(b.physics().angularMomentum().minus(onMomentum));}
        });
    }
    private static Map<BlockPos,net.minecraft.block.BlockState> grid(){var result=new HashMap<BlockPos,net.minecraft.block.BlockState>();for(var c:body.cells())result.put(c.pos(),c.state());return Map.copyOf(result);}
    private static void held(MinecraftClient c,boolean value){c.options.forwardKey.setPressed(value);c.options.rightKey.setPressed(value);com.terminaldetector.drmd.client.input.DescentKeybinds.ascend.setPressed(value);com.terminaldetector.drmd.client.input.DescentKeybinds.rollRight.setPressed(value);}
    public static void accepted(ServerPlayerEntity p,UfoControlSystem.Input in) {
        if(!ENABLED||p!=pilot)return;lastInput=in;lastInputTick=p.getServer().getTicks();
        if(Math.abs(in.forward())<.1||Math.abs(in.strafe())<.1||Math.abs(in.vertical())<.1||Math.abs(in.pitch())<.1||Math.abs(in.yaw())<.1||Math.abs(in.roll())<.1)return;
        if(stage==2)offSixAxisInputs++;else if(stage==5)onSixAxisInputs++;
    }
    private static int inputAxis(UfoControlSystem.Input in) {
        float[] values={in.forward(),in.strafe(),in.vertical(),in.pitch(),in.yaw(),in.roll()};int result=-1;
        for(int i=0;i<6;i++)if(Math.abs(values[i])>1e-5){if(result>=0)return -1;result=i;}return result;
    }
    public static void beginDrive(SkyUfoEntity b) {
        if(!ENABLED||b!=body)return;drive=null;
        var in=lastInput;long tick=pilot.getServer().getTicks();
        if(stage==7&&in!=null&&inputAxis(in)==axis&&tick-lastInputTick<=4)
            drive=new Drive(b.physics().linearVelocity(),b.physics().angularMomentum(),b.rotation(),b.physics().worldInertia(),in,axis,tick);
    }
    public static void endDrive(SkyUfoEntity b,boolean controlled) {
        if(!ENABLED||b!=body||!controlled||stage!=7||drive==null||drive.axis()!=axis||drive.tick()==countedDriveTick)return;
        var d=drive;var in=d.input();Vec3 expectedVelocity=new Vec3(0,0,0),expectedMomentum=new Vec3(0,0,0);
        if(axis<3){var local=axis==0?new Vec3(0,0,in.forward()):axis==1?new Vec3(-in.strafe(),0,0):new Vec3(0,in.vertical(),0);expectedVelocity=d.rotation().rotate(local).scaled(5*.05);}
        else {var local=axis==3?new Vec3(in.pitch(),0,0):axis==4?new Vec3(0,in.yaw(),0):new Vec3(0,0,in.roll());expectedMomentum=d.inertia().transform(d.rotation().rotate(local)).scaled(.05);}
        maxDriveVelocityError=Math.max(maxDriveVelocityError,b.physics().linearVelocity().minus(d.velocity()).minus(expectedVelocity).length());
        maxDriveMomentumError=Math.max(maxDriveMomentumError,b.physics().angularMomentum().minus(d.momentum()).minus(expectedMomentum).length());
        require(maxDriveVelocityError<1e-8&&maxDriveMomentumError<1e-6,"independent engine input must obey F/m and the actual world inertia at real mounts: "+maxDriveVelocityError+" / "+maxDriveMomentumError);
        countedDriveTick=d.tick();AXIS_SAMPLES.incrementAndGet(axis);
    }
    private static void axisInput(MinecraftClient c) {
        held(c,false);
        switch(axis){
            case 0 -> c.options.forwardKey.setPressed(true);
            case 1 -> c.options.rightKey.setPressed(true);
            case 2 -> com.terminaldetector.drmd.client.input.DescentKeybinds.ascend.setPressed(true);
            case 3 -> com.terminaldetector.drmd.client.flight.UfoControlClient.mouse(0,-30);
            case 4 -> com.terminaldetector.drmd.client.flight.UfoControlClient.mouse(-30,0);
            case 5 -> com.terminaldetector.drmd.client.input.DescentKeybinds.rollRight.setPressed(true);
            default -> throw new IllegalStateException("native engine axis");
        }
    }
    private static void mouse(){com.terminaldetector.drmd.client.flight.UfoControlClient.mouse(-30,-30);}
    private static boolean allAxes(Vec3 v){return v!=null&&Math.abs(v.x())>.1&&Math.abs(v.y())>.1&&Math.abs(v.z())>.1;}
    private static double continuity(Vec3d before,Vec3d velocity,Vec3d point){return Math.max(body.localToWorld(point).distanceTo(before),body.surfaceVelocityAt(body.localToWorld(point)).distanceTo(velocity));}
    /** Return an additional fresh scene, or null while waiting on real world ticks. */
    public static String tick(MinecraftClient c) {
        if(stage==1||stage==2||stage==4||stage==5)mouse();
        if(stage==0){
            held(c,true);mouse();stage=1;c.getServer().execute(()->{
                var point=new Vec3d(3.5,-.5,3.5);var before=body.localToWorld(point);var velocity=body.surfaceVelocityAt(before);
                var cuts=new HashMap<BlockPos,net.minecraft.block.BlockState>();for(var slot:SkyUfoThrusters.SLOTS)cuts.put(slot,Blocks.AIR.getDefaultState());
                require(body.setLocalBlocks(cuts)&&body.activeThrusters()==0,"actual atomic local edit disables the four engine mounts");
                materialError=Math.max(materialError,continuity(before,velocity,point));require(materialError<1e-8,"engine loss resets a material point's pose or velocity");
                offVelocity=body.physics().linearVelocity();startPosition=body.massPosition();startRotation=body.rotation();stage=2;
            });
        }else if(stage==2&&offSamples>=12&&offSixAxisInputs>=8){
            require(travel>.2&&turn>.01,"disabled engines must leave genuine translational/rotational inertia");
            held(c,false);stage=3;return "engines-off-inertial-flight";
        }else if(stage==3){
            held(c,true);mouse();stage=4;c.getServer().execute(()->{
                var point=new Vec3d(3.5,-.5,3.5);var before=body.localToWorld(point);var velocity=body.surfaceVelocityAt(before);var access=new BlockBodyWorldAccess(body);
                for(var slot:SkyUfoThrusters.SLOTS)require(access.placeLocalBlock(slot,ModWorldBlocks.SHIP_THRUSTER.getDefaultState()),"actual collision-checked local placement repairs each engine mount");
                materialError=Math.max(materialError,continuity(before,velocity,point));require(materialError<1e-8,"engine repair resets material motion");
                require(grid().equals(original),"repair restores exactly the original real hull grid");
                repairedRevision=body.shapeRevision();onVelocity=body.physics().linearVelocity();onMomentum=body.physics().angularMomentum();onRotation=body.rotation();stage=5;
            });
        }else if(stage==5&&onSamples>=12&&onSixAxisInputs>=8){
            require(onDelta>.8,"restored motors must accelerate the original captured hull through real input: "+onDelta);
            require(allAxes(localVelocityDelta)&&allAxes(localMomentumDelta),"actual restored engines must respond on all three linear and all three angular axes");
            held(c,false);stage=6;return "engines-restored-thrust";
        }else if(stage==6){axis=0;stage=7;axisInput(c);}
        else if(stage==7){
            axisInput(c);
            if(AXIS_SAMPLES.get(axis)>=4){held(c,false);if(axis==5){stage=8;return "six-axis-isolated-inertia";}axis++;}
        }
        return null;
    }
    public static boolean complete(){return stage==8;}
    public static void prepareExterior(MinecraftClient c) {
        c.options.hudHidden=false;c.options.setPerspective(Perspective.FIRST_PERSON);
        c.getServer().execute(()->{
            require(!UfoControlSystem.controls(pilot),"exterior inspection follows actual H release");
            var at=body.localToWorld(new Vec3d(.5,-12,.5));var target=body.localToWorld(new Vec3d(.5,-1.5,.5));
            var angles=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(new Vec3(target.x-at.x,target.y-(at.y+pilot.getStandingEyeHeight()),target.z-at.z));
            // Canonical creative flight only for the exterior camera, after all survival
            // control/engine assertions and the actual H release have completed.
            pilot.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            pilot.getAbilities().allowFlying=true;pilot.getAbilities().flying=true;pilot.sendAbilitiesUpdate();
            pilot.getInventory().selectedSlot=0;pilot.getInventory().setStack(0,new ItemStack(ModWorldBlocks.SHIP_THRUSTER));pilot.getInventory().markDirty();pilot.currentScreenHandler.sendContentUpdates();
            pilot.teleport(pilot.getServerWorld(),at.x,at.y,at.z,Set.of(),(float)angles.yawDegrees(),(float)angles.pitchDegrees());pilot.setVelocity(Vec3d.ZERO);camera=at;exterior=true;
        });
    }
    public static boolean exteriorReady(MinecraftClient c) {
        if(!exterior||camera==null||c.player.squaredDistanceTo(camera)>.5||!c.player.getMainHandStack().isOf(ModWorldBlocks.SHIP_THRUSTER.asItem()))return false;
        if(!c.player.getAbilities().allowFlying)return false;
        if(!c.player.getAbilities().flying){c.player.setOnGround(false);c.player.getAbilities().flying=true;c.player.sendAbilitiesUpdate();}
        var e=c.world.getEntityById(entity(body).getId());if(!(e instanceof SkyUfoEntity b)||b.shapeRevision()!=repairedRevision||SkyUfoThrusters.SLOTS.stream().anyMatch(slot->!b.getLocalBlock(slot).isOf(ModWorldBlocks.SHIP_THRUSTER)))return false;
        var target=b.localToWorld(new Vec3d(.5,-1.5,.5));var aim=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(new Vec3(target.x-c.player.getEyePos().x,target.y-c.player.getEyePos().y,target.z-c.player.getEyePos().z));c.player.setYaw((float)aim.yawDegrees());c.player.setPitch((float)aim.pitchDegrees());return true;
    }
    public static void append(Path marker) throws java.io.IOException {
        if(!ENABLED)return;require(complete()&&exterior&&offSamples>=12&&onSamples>=12,"complete engine runtime evidence required");
        var json=com.google.gson.JsonParser.parseString(Files.readString(marker)).getAsJsonObject();
        json.add("independentAxisSamples",new com.google.gson.Gson().toJsonTree(java.util.stream.IntStream.range(0,6).map(AXIS_SAMPLES::get).toArray()));json.addProperty("maxEngineVelocityLawError",maxDriveVelocityError);json.addProperty("maxEngineMomentumLawError",maxDriveMomentumError);
        json.addProperty("engineOffSixAxisInputs",offSixAxisInputs);json.addProperty("engineOnSixAxisInputs",onSixAxisInputs);json.add("localVelocityDelta",new com.google.gson.Gson().toJsonTree(localVelocityDelta));json.add("localMomentumDelta",new com.google.gson.Gson().toJsonTree(localMomentumDelta));
        json.addProperty("engineOffSamples",offSamples);json.addProperty("engineOnSamples",onSamples);json.addProperty("engineOffMaxVelocityDelta",offDelta);json.addProperty("engineOnVelocityDelta",onDelta);json.addProperty("materialPointError",materialError);json.addProperty("inertialTravel",travel);json.addProperty("inertialTurn",turn);json.addProperty("restoredEngines",body.activeThrusters());json.addProperty("newEngineItemObserved",true);
        Files.writeString(marker,new com.google.gson.Gson().toJson(json)+"\n");
    }
}
