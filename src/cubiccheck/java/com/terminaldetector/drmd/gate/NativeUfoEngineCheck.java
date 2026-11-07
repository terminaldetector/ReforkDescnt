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
    private static volatile int stage,offSamples,onSamples,offSixAxisInputs,onSixAxisInputs;
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
            if(w!=p.getServerWorld()||(stage!=2&&stage!=5))return;
            require(UfoControlSystem.controls(p),"engine failure/repair cannot replace the captured-ship owner");
            require(BodySpatialIndex.loaded(w).stream().filter(other->((net.minecraft.entity.Entity)other).getUuid().equals(entity(b).getUuid())).count()==1,"one original physical UFO identity throughout engine edits");
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
        if(!ENABLED||p!=pilot||Math.abs(in.forward())<.1||Math.abs(in.strafe())<.1||Math.abs(in.vertical())<.1||Math.abs(in.pitch())<.1||Math.abs(in.yaw())<.1||Math.abs(in.roll())<.1)return;
        if(stage==2)offSixAxisInputs++;else if(stage==5)onSixAxisInputs++;
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
        }
        return null;
    }
    public static boolean complete(){return stage==6;}
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
        json.addProperty("engineOffSixAxisInputs",offSixAxisInputs);json.addProperty("engineOnSixAxisInputs",onSixAxisInputs);json.add("localVelocityDelta",new com.google.gson.Gson().toJsonTree(localVelocityDelta));json.add("localMomentumDelta",new com.google.gson.Gson().toJsonTree(localMomentumDelta));
        json.addProperty("engineOffSamples",offSamples);json.addProperty("engineOnSamples",onSamples);json.addProperty("engineOffMaxVelocityDelta",offDelta);json.addProperty("engineOnVelocityDelta",onDelta);json.addProperty("materialPointError",materialError);json.addProperty("inertialTravel",travel);json.addProperty("inertialTurn",turn);json.addProperty("restoredEngines",body.activeThrusters());json.addProperty("newEngineItemObserved",true);
        Files.writeString(marker,new com.google.gson.Gson().toJson(json)+"\n");
    }
}
