package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** Production BlockBody + native world volumes + actual renderer, in a disposable high-altitude arena. */
public final class NativeBodyCollisionCheck {
    private static final BlockPos A=new BlockPos(8,2156,16),B=new BlockPos(16,2156,16),C=new BlockPos(8,2156,24);
    private static final Set<Integer> DRAWN=new HashSet<>();
    private static final String[] NAMES={"diagonal-corner","terrain-stop","hollow-door"};
    private static final List<String> RESULTS=new ArrayList<>();
    private static boolean started,preparing,finished;private static volatile boolean ready;
    private static int ticks,stage,frames;private static volatile int[] ids;
    private static net.minecraft.entity.Entity entity(BlockBodyEntity b){return b;}
    public static void register(){ClientTickEvents.END_CLIENT_TICK.register(NativeBodyCollisionCheck::tick);}
    public static void drew(BlockBodyEntity b){if(ready && !finished)DRAWN.add(entity(b).getId());}
    public static void camera(Camera camera) {
        if(!ready)return;BlockPos p=stage==0?A:stage==1?B.east():C.add(1,1,0);
        var a=(CameraAccessor)camera;a.drmd$invokeSetPos(Vec3d.ofCenter(p).add(7,4,10));a.drmd$invokeSetRotation(145,18);
    }
    private static void tick(MinecraftClient client) {
        if(finished)return;
        if(++ticks>2400)throw new IllegalStateException("Native contact timeout stage="+stage+" ready="+ready);
        if(client.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)
            for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget button
                && button.getMessage().getString().equals("I know what I'm doing!"))button.onPress();
        if(client.player==null || client.world==null || client.getServer()==null)return;
        com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();com.terminaldetector.drmd.client.DescentClientState.enabled=false;
        if(!started) {
            if(!client.runDirectory.toPath().getFileName().toString().equals("cubic-client-automation"))throw new IllegalStateException("Disposable runtime required");
            started=true;client.setScreen(null);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);client.options.hudHidden=true;
            DescentConfig.hud=false;DescentConfig.cockpit=false;DescentConfig.weaponView=false;DescentConfig.planetFloor=false;DescentConfig.levelSky=false;
            DescentConfig.portalSeeThrough=false;DescentConfig.mirrorReflection=false;
            client.getServer().execute(()->{
                var p=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                com.terminaldetector.drmd.flight.FlightSystem.disable(p,com.terminaldetector.drmd.DescentPlayerData.get(p));
                p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.sendAbilitiesUpdate();
                p.teleport(p.getServerWorld(),14.5,2156,28.5,Set.of(),145,18);
            });return;
        }
        if(!ready && !preparing && client.player.getY()>2100 && loaded(client)) {
            preparing=true;client.getServer().execute(()->setup(client));
        }
    }
    private static boolean loaded(MinecraftClient c) {
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(c.world);
        for(int x=0;x<=1;x++)for(int y=133;y<=135;y++)for(int z=0;z<=2;z++)
            if(!access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;
        return true;
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void setup(MinecraftClient c) {
        var w=c.getServer().getOverworld();
        for(var p:BlockPos.iterate(new BlockPos(3,2152,10),new BlockPos(25,2164,30)))w.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        w.setTimeOfDay(6000);w.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,c.getServer());
        var q=new Quat(0,0,Math.sin(Math.PI/8),Math.cos(Math.PI/8));
        w.setBlockState(A,Blocks.IRON_BLOCK.getDefaultState(),2);var a=BlockBodyEntity.assemble(w,A,A);
        a.physics().withRotation(q).withLinearVelocity(new Vec3(0,0,1));w.setBlockState(A.east().up(),Blocks.OBSIDIAN.getDefaultState(),2);
        double before=entity(a).getZ();entity(a).tick();require(entity(a).getZ()>before+.049,"native diagonal corner falsely blocks OBB");
        a.physics().withLinearVelocity(new Vec3(0,0,0));entity(a).tick();
        w.setBlockState(B,Blocks.IRON_BLOCK.getDefaultState(),2);var b=BlockBodyEntity.assemble(w,B,B);
        b.physics().withRotation(q).withLinearVelocity(new Vec3(8,0,0));w.setBlockState(B.east(2),Blocks.OBSIDIAN.getDefaultState(),2);
        for(int i=0;i<5;i++)entity(b).tick();
        require(entity(b).getX()>B.getX()+.7 && entity(b).getX()<B.getX()+1.3 && b.physics().linearVelocity().length()<1e-8,"native real wall contact missing");
        var cells=new LinkedHashMap<BlockPos,net.minecraft.block.BlockState>();
        for(int x=0;x<3;x++)for(int y=0;y<3;y++)if(x!=1||y!=1) {
            var p=C.add(x,y,0);w.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState(),2);cells.put(p,Blocks.IRON_BLOCK.getDefaultState());
        }
        var hollow=BlockBodyEntity.assembleSelection(w,cells);hollow.physics().withRotation(new Quat(0,Math.sin(.35),0,Math.cos(.35)));entity(hollow).tick();
        require(hollow.raycast(hollow.localToWorld(new Vec3d(1.5,1.5,-4)),hollow.localToWorld(new Vec3d(1.5,1.5,4))).isEmpty(),"native doorway filled by broadphase");
        require(hollow.collider().boxes().size()<8,"native grid not merged");
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(w);Vec3 far=new Vec3(8.5,5000,16.5);var cube=new D6ChunkPos(0,5000>>4,1);
        require(!access.isVolumeAvailable(cube),"unknown exit fixture already available");var old=a.physics().position();
        var turn=D6PortalTransform.of(old,new Vec3(0,0,1),far,new Vec3(0,0,-1));
        require(!a.canCrossPortal(turn,far),"unknown native Y cube accepted");
        require(!access.isVolumeAvailable(cube) && a.physics().position().equals(old),"rejected query loads volume or mutates pose");
        ids=new int[]{entity(a).getId(),entity(b).getId(),entity(hollow).getId()};ready=true;
        System.out.println("BODY_CONTACT_NATIVE server geometry, hole, collision and unknown-Y rejection PASS");
    }
    public static void rendered() {
        if(!ready || finished)return;var c=MinecraftClient.getInstance();
        for(int id:ids)if(!(c.world.getEntityById(id) instanceof BlockBodyEntity))return;
        boolean visible=DRAWN.contains(ids[stage]);DRAWN.clear();
        if(!visible || ++frames<90)return;
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            var path=c.runDirectory.toPath().resolve("body-contact-frames");Files.createDirectories(path);image.writeTo(path.resolve(NAMES[stage]+".png"));
            RESULTS.add("{\"scene\":\""+NAMES[stage]+"\",\"renderedFrames\":"+frames+"}");
            System.out.println("BODY_CONTACT_CLIENT "+NAMES[stage]+" PASS");
            frames=0;if(++stage==3) {
                Files.writeString(c.runDirectory.toPath().resolve("body-contacts-ok.json"),"{\"orientedTerrain\":true,\"hollowRay\":true,\"unknownNativeYRejected\":true,\"actualRenderer\":true,\"scenes\":["+String.join(",",RESULTS)+"]}\n");
                finished=true;c.scheduleStop();
            }
        }catch(java.io.IOException error){throw new IllegalStateException(error);}
    }
}
