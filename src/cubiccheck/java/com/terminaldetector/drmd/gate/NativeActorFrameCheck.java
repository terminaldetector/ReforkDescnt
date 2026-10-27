package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.entity.mob.*;
import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import com.terminaldetector.drmd.world.gravity.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;
import org.joml.*;

/** Actual production client: spawn metadata, six surface axes, full flying model transforms. */
public final class NativeActorFrameCheck {
    private static boolean started,finished;
    private static volatile boolean prepared,preparing;
    private static int ticks,stage,frames;
    private static final List<Integer> ids=new ArrayList<>();
    private static final List<UUID> fields=new ArrayList<>();
    private static final Set<Integer> models=new HashSet<>();
    private static final List<String> results=new ArrayList<>();
    private static final float[][] FLIGHT={{0,0,0},{90,-90,45},{180,90,-65},{-90,0,180},{135,-35,90},{-135,35,-90}};
    public static void register(){ClientTickEvents.END_CLIENT_TICK.register(NativeActorFrameCheck::tick);}
    public static void camera(Camera camera) {
        if(!started || finished)return;
        var accessor=(CameraAccessor)camera;
        accessor.drmd$invokeSetPos(new Vec3d(12,2158,40));accessor.drmd$invokeSetRotation(180,0);
    }
    private static boolean loaded(MinecraftClient client) {
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(client.world);
        for(int x=0;x<=1;x++)for(int y=134;y<=135;y++)for(int z=0;z<=2;z++)
            if(!access.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z)))return false;
        return true;
    }
    private static void tick(MinecraftClient client) {
        if(finished)return;
        if(++ticks>3000)throw new IllegalStateException("Actor client timeout stage="+stage+" prepared="+prepared+" ids="+ids);
        if(client.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)
            for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget button
                && button.getMessage().getString().equals("I know what I'm doing!"))button.onPress();
        if(client.player==null || client.world==null || client.getServer()==null)return;
        if(!started) {
            if(!client.runDirectory.toPath().getFileName().toString().equals("cubic-client-automation"))throw new IllegalStateException("Disposable world required");
            started=true;client.setScreen(null);client.inGameHud.getChatHud().clear(false);client.options.hudHidden=true;
            DescentConfig.hud=false;DescentConfig.cockpit=false;DescentConfig.weaponView=false;DescentConfig.planetFloor=false;DescentConfig.levelSky=false;
            client.getServer().execute(()->{
                var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
                com.terminaldetector.drmd.flight.FlightSystem.disable(player,com.terminaldetector.drmd.DescentPlayerData.get(player));
                player.getAbilities().allowFlying=true;player.getAbilities().flying=true;player.sendAbilitiesUpdate();
                player.teleport(player.getServerWorld(),12,2156.38,38,Set.of(),180,0);
            });return;
        }
        if(!prepared && !preparing && ids.isEmpty() && client.player.getY()>2100 && loaded(client)) {
            preparing=true;
            client.getServer().execute(()->prepare(client));
        }
    }
    private static void prepare(MinecraftClient client) {
        var world=client.getServer().getOverworld();
        for(UUID id:fields)GravityFields.remove(id);fields.clear();
        for(int id:ids){Entity entity=world.getEntityById(id);if(entity!=null)entity.discard();}ids.clear();
        if(stage==0) {
            world.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,client.getServer());world.setTimeOfDay(6000);
            for(BlockPos pos:BlockPos.iterate(new BlockPos(1,2146,8),new BlockPos(24,2170,39)))
                world.setBlockState(pos,Blocks.AIR.getDefaultState(),2);
        }
        EntityType<?>[] types={EntityType.COW,ModEntities.SPIDER_TURRET,ModEntities.TRIPOD,
            ModEntities.DRONE,ModEntities.SCANNER,ModEntities.OBLIVION_SEEKER};
        Vec3d up=Vec3d.of(Direction.values()[stage].getVector());
        for(int i=0;i<types.length;i++) {
            MobEntity actor=(MobEntity)types[i].create(world);actor.setAiDisabled(true);actor.setPersistent();actor.setNoGravity(true);
            actor.setPosition(6+(i%3)*6,2155.5,i<3?16:26);
            if(i<3) {
                UUID id=UUID.randomUUID();fields.add(id);
                GravityFields.put(new GravityFields.Field(id,world.getRegistryKey(),actor.getBlockPos(),up.negate(),2,1,FieldShape.SPHERE,"client pose"));
                if(!ActorFrames.trySetUp(actor,up))throw new IllegalStateException("Native frame placement rejected "+types[i]+" "+up);
            } else {
                float[] pose=FLIGHT[stage];actor.setYaw(pose[0]);actor.setPitch(pose[1]);actor.bodyYaw=pose[0];actor.setHeadYaw(pose[0]);
                if(actor instanceof DroneEntity drone)drone.setFlightRoll(pose[2]);
                if(actor instanceof CyberMobEntity cyber)cyber.setFlightRoll(pose[2]);
            }
            world.spawnEntity(actor);ids.add(actor.getId());
        }
        frames=0;models.clear();preparing=false;prepared=true;
    }
    /** Compare the transform actually appended by LivingEntityRenderer, not just a helper quaternion. */
    public static void model(LivingEntity actor,Matrix4f before,Matrix4f after,float bodyYaw,float delta) {
        if(!prepared || !ids.contains(actor.getId()))return;
        Vec3d up,forward;
        if(ActorFrames.active(actor)) {
            up=ActorFrames.up(actor);forward=ActorFrameMath.toWorld(Vec3d.fromPolar(0,bodyYaw),up);
        } else {
            forward=actor.getRotationVec(delta);up=com.terminaldetector.drmd.ai.FlightAttitude.upOf(forward,ActorPhysicsProfile.roll(actor));
        }
        Matrix4f relative=new Matrix4f(before).invert().mul(after);
        Vector3f actualUp=relative.transformDirection(new Vector3f(0,1,0)).normalize();
        Vector3f actualForward=relative.transformDirection(new Vector3f(0,0,-1)).normalize();
        if(actualUp.distance(new Vector3f((float)up.x,(float)up.y,(float)up.z))>.002
            || actualForward.distance(new Vector3f((float)forward.x,(float)forward.y,(float)forward.z))>.002)
            throw new IllegalStateException("Actual model basis disagrees: "+actor.getType()+" stage="+stage+" up="+actualUp+" expected="+up);
        models.add(actor.getId());
    }
    public static void rendered() {
        if(!prepared || finished)return;
        var client=MinecraftClient.getInstance();
        if(ids.size()!=6 || ids.stream().anyMatch(id->client.world.getEntityById(id)==null))return;
        for(int id:ids) {
            var actor=(LivingEntity)client.world.getEntityById(id);
            if(ActorFrames.supported(actor)) {
                Vec3d expected=Vec3d.of(Direction.values()[stage].getVector());
                if(ActorFrames.up(actor).distanceTo(expected)>1e-6)throw new IllegalStateException("Initial spawn metadata lost frame "+actor.getType());
                if(actor.getEyePos().subtract(actor.getPos()).normalize().distanceTo(expected)>1e-6)throw new IllegalStateException("Client eye disagrees with metadata");
                if(!actor.getBoundingBox().equals(ActorFrames.body(actor,actor.getPos(),expected)))throw new IllegalStateException("Client body disagrees with metadata");
            }
        }
        if(++frames<90 || models.size()!=6)return;
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            var root=client.runDirectory.toPath().resolve("actor-frames");Files.createDirectories(root);
            image.writeTo(root.resolve("six-axis-"+stage+".png"));
            results.add("{\"stage\":"+stage+",\"models\":"+models.size()+",\"renderedFrames\":"+frames+"}");
            System.out.println("ACTOR_FRAME_CLIENT stage="+stage+" six models, metadata, eyes, body and actual render basis PASS");
            prepared=false;
            if(++stage==6) {
                Files.writeString(client.runDirectory.toPath().resolve("actor-frames-ok.json"),"{\"scenes\":["+String.join(",",results)+"]}\n");
                finished=true;client.scheduleStop();return;
            }
            preparing=true;client.getServer().execute(()->prepare(client));
        } catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
}
