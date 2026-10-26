package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.portal.*;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import com.terminaldetector.drmd.world.portal.mirror.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** Real-client joined panes, hole mask, angled projection, occlusion and native portal regression. */
public final class MirrorJoinCheck {
    private static boolean started;
    private static volatile boolean ready;
    private static int ticks,age,stage;
    private static int[] on;
    private static final String[] NAMES={"joined-on","joined-off","hole-on","hole-off","angled-on","angled-off",
            "occluded-on","occluded-off","charged-on","charged-off","portal-on","portal-off","banked-on","banked-off"};
    private static final List<String> RESULTS=new ArrayList<>();
    public static void register() {ClientTickEvents.END_CLIENT_TICK.register(MirrorJoinCheck::tick);}
    public static void overrideCamera(Camera camera) {
        if(!started)return;
        var access=(CameraAccessor)camera;
        access.drmd$invokeSetPos(stage==4 || stage==5?new Vec3d(15,206,30.5):new Vec3d(13,206,30.5));
        access.drmd$invokeSetRotation(stage==4 || stage==5?160:180,0);
    }
    private static void tick(MinecraftClient client) {
        if(++ticks>2400)throw new IllegalStateException("Mirror join timeout "+NAMES[stage]+" "+PortalViewDiagnostics.lastSummary("mirror"));
        if(client.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)
            for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget button
                    && button.getMessage().getString().equals("I know what I'm doing!"))button.onPress();
        if(client.player==null || client.world==null || client.getServer()==null)return;
        com.terminaldetector.drmd.client.DescentClientState.enabled=stage>=12;
        if(stage>=12) {
            var attitude=new com.terminaldetector.drmd.flight.ShipAttitude();
            attitude.setForwardUp(new Vec3d(0,0,1),new Vec3d(0,1,0));attitude.rollLocal(45);
            com.terminaldetector.drmd.client.flight.ShipAttitudeClient.acceptPortalFrame(client.player,attitude.forward(),attitude.up());
        }
        if(!started) {
            if(!client.runDirectory.toPath().getFileName().toString().equals("shootcheck"))throw new IllegalStateException("Disposable world required");
            started=true;client.setScreen(null);client.inGameHud.getChatHud().clear(false);client.options.hudHidden=true;
            client.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
            DescentConfig.cockpit=false;DescentConfig.weaponView=false;DescentConfig.hud=false;
            prepare(client);
        }
        if(ready)age++;
    }
    private static void prepare(MinecraftClient client) {
        ready=false;age=0;DescentConfig.planetFloor=false;DescentConfig.levelSky=false;
        DescentConfig.mirrorReflection=stage%2==0 && stage!=10;
        DescentConfig.portalSeeThrough=stage==10;
        if(stage%2==1){ready=true;return;}
        client.getServer().execute(()->{
            var world=client.getServer().getOverworld();
            world.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,client.getServer());
            world.setTimeOfDay(6000);
            for(var pos:BlockPos.iterate(new BlockPos(5,202,2),new BlockPos(21,213,39)))
                world.setBlockState(pos,pos.getY()==202?Blocks.OBSIDIAN.getDefaultState():Blocks.AIR.getDefaultState(),3);
            // Warm gold wall is behind the real camera; it can only appear via a mirror.
            for(var pos:BlockPos.iterate(new BlockPos(6,203,35),new BlockPos(20,212,35)))
                world.setBlockState(pos,Blocks.GOLD_BLOCK.getDefaultState(),3);
            if(stage==10) {
                var a=new BlockPos(12,205,24);var b=new BlockPos(12,205,12);
                world.setBlockState(a,ModWorldBlocks.PORTAL_PANEL.getDefaultState().with(PortalPanelBlock.FACING,Direction.SOUTH).with(PortalPanelBlock.LINKED,true),3);
                world.setBlockState(b,ModWorldBlocks.PORTAL_PANEL.getDefaultState().with(PortalPanelBlock.FACING,Direction.NORTH).with(PortalPanelBlock.LINKED,true),3);
                ((PortalPanelBlockEntity)world.getBlockEntity(a)).setLink(null,b,world.getRegistryKey());
                ((PortalPanelBlockEntity)world.getBlockEntity(b)).setLink(null,a,world.getRegistryKey());
                world.getChunkManager().markForUpdate(a);world.getChunkManager().markForUpdate(b);
                for(var p:BlockPos.iterate(new BlockPos(9,203,5),new BlockPos(16,210,5)))world.setBlockState(p,Blocks.DIAMOND_BLOCK.getDefaultState(),3);
            } else {
                var block=stage==8?ModWorldBlocks.CHARGED_MIRROR:ModWorldBlocks.MIRROR;
                for(int x=12;x<=13;x++)for(int y=205;y<=206;y++)
                    if(stage!=2 || x!=13 || y!=206)world.setBlockState(new BlockPos(x,y,24),block.getDefaultState().with(net.minecraft.state.property.Properties.FACING,Direction.SOUTH),3);
                if(stage==6)world.setBlockState(new BlockPos(12,205,27),Blocks.REDSTONE_BLOCK.getDefaultState(),3);
            }
            ready=true;
        });
    }
    public static void rendered() {
        if(!started || !ready || age<50)return;
        var client=MinecraftClient.getInstance();
        if(stage%2==0) {
            var count=stage==10?com.terminaldetector.drmd.client.portal.PortalSeeThroughRenderer.scannedCount():MirrorReflectionRenderer.scannedCount();
            if(count!=1 && stage!=10){age=30;return;}
            var summary=PortalViewDiagnostics.lastSummary(stage==10?"portal":"mirror");
            if(summary==null || !summary.startsWith("drawing")){age=30;return;}
        }
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            var root=client.runDirectory.toPath().resolve("mirror-join-frames");Files.createDirectories(root);
            image.writeTo(root.resolve(NAMES[stage]+".png"));
            int w=image.getWidth(),h=image.getHeight();
            if(stage%2==0) {
                on=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)on[y*w+x]=image.getColor(x,y);
            } else {
                int changed=0;
                for(int y=h/4;y<h*3/4;y++)for(int x=w/4;x<w*3/4;x++)if(on[y*w+x]!=image.getColor(x,y))changed++;
                if(changed<100)throw new IllegalStateException("No visible native reflection "+NAMES[stage]+" changed="+changed);
                if(stage==3 || stage==7) {
                    Vec3d point=stage==3?new Vec3d(13.5,206.5,25):new Vec3d(12.5,205.5,28);
                    var relative=point.subtract(client.gameRenderer.getCamera().getPos());
                    var clip=new org.joml.Vector4f((float)relative.x,(float)relative.y,(float)relative.z,1)
                            .mul(OffscreenWorldView.framePosition()).mul(OffscreenWorldView.frameProjection());
                    int px=(int)((clip.x/clip.w*.5+.5)*w),py=(int)((.5-clip.y/clip.w*.5)*h);
                    int leaked=0;
                    for(int y=py-4;y<py+4;y++)for(int x=px-4;x<px+4;x++)if(on[y*w+x]!=image.getColor(x,y))leaked++;
                    if(leaked!=0)throw new IllegalStateException("Reflection leaked into "+NAMES[stage]+": "+leaked);
                    System.out.println("MIRROR_JOIN maskedPixels=64 unchanged "+NAMES[stage]);
                }
                RESULTS.add("{\"scene\":\""+NAMES[stage-1]+"\",\"changedPixels\":"+changed+"}");
                System.out.println("MIRROR_JOIN "+NAMES[stage]+" changed="+changed);
            }
            if(stage==0 || stage==8 || stage==12) {
                int gold=0;
                for(int y=h/2-4;y<h/2+4;y++)for(int x=w/2-4;x<w/2+4;x++) {
                    int color=image.getColor(x,y),r=color&255,g=color>>8&255,b=color>>16&255;
                    if(r>g && g>b*1.2 && r>80)gold++;
                }
                if(gold<32)throw new IllegalStateException("Joined centre seam is not a continuous gold reflection: "+gold);
                System.out.println("MIRROR_JOIN seamGold="+gold);
            }
            if(++stage==NAMES.length) {
                Files.writeString(client.runDirectory.toPath().resolve("mirror-join-ok.json"),"{\"frames\":14,\"scenes\":["+String.join(",",RESULTS)+"]}\n");
                started=false;client.scheduleStop();return;
            }
            prepare(client);
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
