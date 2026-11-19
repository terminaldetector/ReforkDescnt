package com.terminaldetector.drmd.gate;

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
public final class NativeMirrorCheck {
    private static boolean started,warmed;
    private static volatile boolean ready,pendingPrepare;
    private static int ticks,age,stage;
    private static int[] on,backingReference;
    private static int baselineWidth,baselineHeight,forcedTargetFailures;
    private static final boolean FALLBACK_CHECK=Boolean.getBoolean("drmd.gate.mirrorFallback");
    private static final List<Map<String,Object>> FALLBACK_RESULTS=new ArrayList<>();
    private static final String[] NAMES={"joined-on","joined-off","hole-on","hole-off","angled-on","angled-off",
            "occluded-on","occluded-off","charged-on","charged-off","portal-on","portal-off","banked-on","banked-off","both-on","both-off","target-unavailable","target-recovered"};
    private static final List<String> RESULTS=new ArrayList<>();
    /** Test-only allocator interception happens before any GL mutation; every ordinary call forwards once. */
    public static Object allocateTarget(java.util.function.Supplier<?> allocate) {
        if(!FALLBACK_CHECK||stage!=16)return allocate.get();
        forcedTargetFailures++;
        throw new IllegalStateException("controlled mirror allocator refusal before GL");
    }
    public static void register() {ClientTickEvents.END_CLIENT_TICK.register(NativeMirrorCheck::tick);}
    public static void overrideCamera(Camera camera) {
        if(!started)return;
        var access=(CameraAccessor)camera;
        access.drmd$invokeSetPos(stage==4 || stage==5?new Vec3d(15,2156,30.5):new Vec3d(13,2156,30.5));
        access.drmd$invokeSetRotation(!warmed?0:stage==4 || stage==5?160:180,0);
    }
    private static void tick(MinecraftClient client) {
        if(++ticks>2400)throw new IllegalStateException("Mirror join timeout "+NAMES[stage]+" "+PortalViewDiagnostics.lastSummary("mirror"));
        if(client.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)
            for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget button
                    && button.getMessage().getString().equals("I know what I'm doing!"))button.onPress();
        if(client.player==null || client.world==null || client.getServer()==null)return;
        com.terminaldetector.drmd.client.DescentClientState.enabled=stage==12 || stage==13;
        if(stage==12 || stage==13) {
            var attitude=new com.terminaldetector.drmd.flight.ShipAttitude();
            attitude.setForwardUp(new Vec3d(0,0,1),new Vec3d(0,1,0));attitude.rollLocal(45);
            com.terminaldetector.drmd.client.flight.ShipAttitudeClient.acceptPortalFrame(client.player,attitude.forward(),attitude.up());
        }
        if(!started) {
            if(!client.runDirectory.toPath().getFileName().toString().equals("cubic-client-automation"))throw new IllegalStateException("Disposable world required");
            started=true;client.setScreen(null);client.inGameHud.getChatHud().clear(false);client.options.hudHidden=true;
            client.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
            DescentConfig.cockpit=false;DescentConfig.weaponView=false;DescentConfig.hud=false;
            client.getServer().execute(()->{
                var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
                com.terminaldetector.drmd.flight.FlightSystem.disable(player,com.terminaldetector.drmd.DescentPlayerData.get(player));
                player.getAbilities().allowFlying=true;player.getAbilities().flying=true;player.sendAbilitiesUpdate();
                player.teleport(player.getServerWorld(),13,2154.38,30.5,Set.of(),180,0);
            });
            prepare(client);
        }
        if(pendingPrepare && Math.abs(client.player.getY()-2154.38)<3 && volumesReady(client)) {
            pendingPrepare=false;prepareReady(client);
        }
        if(ready)age++;
    }
    private static boolean volumesReady(MinecraftClient client) {
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(client.world);
        for(int x=0;x<=1;x++)for(int y=134;y<=135;y++)for(int z=0;z<=2;z++)
            if(!access.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z)))return false;
        return true;
    }
    private static void prepare(MinecraftClient client) {ready=false;age=0;pendingPrepare=true;}
    private static void prepareReady(MinecraftClient client) {
        ready=false;age=0;DescentConfig.planetFloor=false;DescentConfig.levelSky=false;
        DescentConfig.mirrorReflection=(stage%2==0 && stage!=10)||stage==17;
        if(stage>=16)MirrorFramebuffer.release();
        DescentConfig.portalSeeThrough=stage==10 || stage==14;
        if(stage%2==1){ready=true;return;}
        client.getServer().execute(()->{
            var world=client.getServer().getOverworld();
            var access=com.terminaldetector.drmd.world.access.WorldAccess.of(world);
            for(int x=0;x<=1;x++)for(int y=134;y<=135;y++)for(int z=0;z<=2;z++)
                if(!access.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z))){pendingPrepare=true;return;}
            world.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,client.getServer());
            world.setTimeOfDay(6000);
            for(var pos:BlockPos.iterate(new BlockPos(5,2152,2),new BlockPos(21,2163,39)))
                world.setBlockState(pos,pos.getY()==2152?Blocks.OBSIDIAN.getDefaultState():Blocks.AIR.getDefaultState(),3);
            // Warm gold wall is behind the real camera; it can only appear via a mirror.
            for(var pos:BlockPos.iterate(new BlockPos(6,2153,35),new BlockPos(20,2162,35)))
                world.setBlockState(pos,Blocks.GOLD_BLOCK.getDefaultState(),3);
            if (stage==10 || stage==14) {
                int x = stage==14 ? 16 : 12;
                var a=new BlockPos(x,2155,24);var b=new BlockPos(x,2155,12);
                world.setBlockState(a,ModWorldBlocks.PORTAL_PANEL.getDefaultState().with(PortalPanelBlock.FACING,Direction.SOUTH).with(PortalPanelBlock.LINKED,true),3);
                world.setBlockState(b,ModWorldBlocks.PORTAL_PANEL.getDefaultState().with(PortalPanelBlock.FACING,Direction.NORTH).with(PortalPanelBlock.LINKED,true),3);
                ((PortalPanelBlockEntity)world.getBlockEntity(a)).setLink(null,b,world.getRegistryKey());
                ((PortalPanelBlockEntity)world.getBlockEntity(b)).setLink(null,a,world.getRegistryKey());
                world.getChunkManager().markForUpdate(a);world.getChunkManager().markForUpdate(b);
                for(var p:BlockPos.iterate(new BlockPos(9,2153,5),new BlockPos(stage==14 ? 20 : 16,2160,5)))world.setBlockState(p,Blocks.DIAMOND_BLOCK.getDefaultState(),3);
            }
            if (stage != 10) {
                var block=stage==8?ModWorldBlocks.CHARGED_MIRROR:ModWorldBlocks.MIRROR;
                for(int x=12;x<=13;x++)for(int y=2155;y<=2156;y++)
                    if(stage!=2 || x!=13 || y!=2156)world.setBlockState(new BlockPos(x,y,24),block.getDefaultState().with(net.minecraft.state.property.Properties.FACING,Direction.SOUTH),3);
                if(stage==6)world.setBlockState(new BlockPos(12,2155,27),Blocks.REDSTONE_BLOCK.getDefaultState(),3);
            }
            ready=true;
        });
    }
    public static void rendered() {
        if(!started || !ready || age<50)return;
        var client=MinecraftClient.getInstance();
        if(client.currentScreen!=null)return;
        if(!warmed) {
            try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                var dir=client.runDirectory.toPath().resolve("native-mirror-frames");Files.createDirectories(dir);
                image.writeTo(dir.resolve("direct-gold-control.png"));int gold=0;
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                    int c=image.getColor(x,y),r=c&255,g=c>>8&255,b=c>>16&255;
                    if(r>g && g>b*1.2 && r>80)gold++;
                }
                System.out.println("NATIVE_MIRROR_CONTROL gold="+gold+" camera="+client.gameRenderer.getCamera().getPos()
                    +" yaw="+client.gameRenderer.getCamera().getYaw()+" pitch="+client.gameRenderer.getCamera().getPitch());
                if(gold<1000){ready=false;throw new IllegalStateException("Direct native gold wall is not visible: "+gold);}
                warmed=true;age=0;return;
            }catch(java.io.IOException failure){ready=false;throw new IllegalStateException(failure);}
        }
        if(stage>=16){fallbackFrame(client);return;}
        if(stage%2==0) {
            var count=stage==10?com.terminaldetector.drmd.client.portal.PortalSeeThroughRenderer.scannedCount():MirrorReflectionRenderer.scannedCount();
            if(count!=1 && stage!=10){age=30;return;}
            var summary=PortalViewDiagnostics.lastSummary(stage==10?"portal":"mirror");
            if(summary==null || !summary.startsWith("drawing")){age=30;return;}
            if (stage==14 && !Objects.toString(PortalViewDiagnostics.lastSummary("portal"), "").startsWith("drawing")) {
                age=30;return;
            }
        }
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            var root=client.runDirectory.toPath().resolve("native-mirror-frames");Files.createDirectories(root);
            image.writeTo(root.resolve(NAMES[stage]+".png"));
            System.out.println("NATIVE_MIRROR_FRAME scene="+NAMES[stage]+" camera="+client.gameRenderer.getCamera().getPos()
                +" yaw="+client.gameRenderer.getCamera().getYaw()+" pitch="+client.gameRenderer.getCamera().getPitch()
                +" diagnostic="+PortalViewDiagnostics.lastSummary(stage==10?"portal":"mirror"));
            int w=image.getWidth(),h=image.getHeight();
            if(stage==1&&FALLBACK_CHECK){
                baselineWidth=w;baselineHeight=h;backingReference=new int[w*h];
                for(int y=0;y<h;y++)for(int x=0;x<w;x++)backingReference[y*w+x]=image.getColor(x,y);
            }
            if(stage%2==0) {
                on=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)on[y*w+x]=image.getColor(x,y);
            } else {
                int changed=0;
                for(int y=h/4;y<h*3/4;y++)for(int x=w/4;x<w*3/4;x++)if(on[y*w+x]!=image.getColor(x,y))changed++;
                if(changed<100)throw new IllegalStateException("No visible native reflection "+NAMES[stage]+" changed="+changed);
                if(stage==3 || stage==7) {
                    Vec3d point=stage==3?new Vec3d(13.5,2156.5,25):new Vec3d(12.5,2155.5,28);
                    var relative=point.subtract(client.gameRenderer.getCamera().getPos());
                    var clip=new org.joml.Vector4f((float)relative.x,(float)relative.y,(float)relative.z,1)
                            .mul(OffscreenWorldView.framePosition()).mul(OffscreenWorldView.frameProjection());
                    int px=(int)((clip.x/clip.w*.5+.5)*w),py=(int)((.5-clip.y/clip.w*.5)*h);
                    int leaked=0;
                    for(int y=py-4;y<py+4;y++)for(int x=px-4;x<px+4;x++)if(on[y*w+x]!=image.getColor(x,y))leaked++;
                    if(leaked!=0)throw new IllegalStateException("Reflection leaked into "+NAMES[stage]+": "+leaked);
                    System.out.println("MIRROR_JOIN maskedPixels=64 unchanged "+NAMES[stage]);
                }
                if (stage==15) {
                    var camera = client.gameRenderer.getCamera().getPos();
                    int mirrorPixels = apertureChanges(image, camera, new Vec3d(12.75,2155.5,24.9));
                    int portalPixels = apertureChanges(image, camera, new Vec3d(16.5,2155.5,24.9));
                    if (mirrorPixels < 64 || portalPixels < 64)
                        throw new IllegalStateException("Same-frame aperture lost its own view: mirror="+mirrorPixels+" portal="+portalPixels);
                    System.out.println("MIRROR_PORTAL_SAME_FRAME mirrorChanged="+mirrorPixels+" portalChanged="+portalPixels);
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
            if(++stage==(FALLBACK_CHECK?18:16)) {
                Files.writeString(client.runDirectory.toPath().resolve("native-mirror-ok.json"),"{\"frames\":"+NAMES.length+",\"scenes\":["+String.join(",",RESULTS)+"]}\n");
                started=false;client.scheduleStop();return;
            }
            prepare(client);
        }catch(java.io.IOException e){ready=false;throw new IllegalStateException(e);}
        catch(RuntimeException failure){ready=false;throw failure;}
    }

    private static void fallbackFrame(MinecraftClient client) {
        String diagnostic=Objects.toString(PortalViewDiagnostics.lastSummary("mirror"),"");
        if(stage==16){
            if(forcedTargetFailures==0||!MirrorFramebuffer.unavailableReason().contains("controlled mirror allocator refusal"))return;
            if(diagnostic.startsWith("drawing"))throw new IllegalStateException("failed target reported reflection success");
        }else if(!diagnostic.startsWith("drawing"))return;
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){
            var folder=client.runDirectory.toPath().resolve("native-mirror-frames");Files.createDirectories(folder);
            image.writeTo(folder.resolve(NAMES[stage]+".png"));
            if(backingReference==null||image.getWidth()!=baselineWidth||image.getHeight()!=baselineHeight)throw new IllegalStateException("same-pose backing baseline unavailable");
            var camera=client.gameRenderer.getCamera().getPos();int x0=baselineWidth,y0=baselineHeight,x1=-1,y1=-1;
            for(var p:List.of(new Vec3d(12,2155,25.002),new Vec3d(14,2155,25.002),new Vec3d(14,2157,25.002),new Vec3d(12,2157,25.002))){
                var r=p.subtract(camera);var v=new org.joml.Vector4f((float)r.x,(float)r.y,(float)r.z,1).mul(OffscreenWorldView.framePosition()).mul(OffscreenWorldView.frameProjection());
                if(v.w<=0)throw new IllegalStateException("backing projection behind camera");
                int x=(int)((v.x/v.w*.5+.5)*baselineWidth),y=(int)((.5-v.y/v.w*.5)*baselineHeight);x0=Math.min(x0,x);y0=Math.min(y0,y);x1=Math.max(x1,x);y1=Math.max(y1,y);
            }
            x0+=6;y0+=6;x1-=6;y1-=6;
            if(x0<0||y0<0||x1>baselineWidth||y1>baselineHeight||(x1-x0)*(y1-y0)<256)throw new IllegalStateException("actual inset backing domain too small");
            int changed=0,domain=0;
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){domain++;if((image.getColor(x,y)&0xffffff)!=(backingReference[y*baselineWidth+x]&0xffffff))changed++;}
            if(forcedTargetFailures!=1)throw new IllegalStateException("allocator repeated after same-size refusal: "+forcedTargetFailures);
            if(stage==16&&changed!=0)throw new IllegalStateException("fallback backing differs from real same-pose OFF baseline: "+changed+"/"+domain);
            if(stage==17){
                int gold=0;for(int y=baselineHeight/2-4;y<baselineHeight/2+4;y++)for(int x=baselineWidth/2-4;x<baselineWidth/2+4;x++){
                    int rgb=image.getColor(x,y),r=rgb&255,g=rgb>>8&255,b=rgb>>16&255;if(r>g&&g>b*1.2&&r>80)gold++;
                }
                if(changed<100||gold<32)throw new IllegalStateException("actual reflection did not recover after target release: changed="+changed+" gold="+gold);
            }
            int[] viewport=new int[4];org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT,viewport);
            int actualFbo=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING),error=org.lwjgl.opengl.GL11.glGetError();
            if(actualFbo!=client.getFramebuffer().fbo||viewport[0]!=0||viewport[1]!=0||viewport[2]!=baselineWidth||viewport[3]!=baselineHeight||OffscreenWorldView.busy()||error!=0)
                throw new IllegalStateException("failed target damaged outer FBO/viewport/depth or GL: "+actualFbo+" error="+error);
            FALLBACK_RESULTS.add(Map.of("scene",NAMES[stage],"samePoseOffBaseline","joined-off.png","aperturePixels",domain,"changedAgainstOff",changed,
                "allocatorRefusals",forcedTargetFailures,"unavailableReason",MirrorFramebuffer.unavailableReason(),"actualOuterFbo",actualFbo,"glError",error));
            System.out.println("NATIVE_MIRROR_FALLBACK "+FALLBACK_RESULTS.get(FALLBACK_RESULTS.size()-1));
            if(++stage==18){
                Files.writeString(client.runDirectory.toPath().resolve("native-mirror-ok.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(
                    Map.of("frames",18,"originalScenes",RESULTS,"allocatorFallback",FALLBACK_RESULTS)));
                started=false;client.scheduleStop();return;
            }
            prepare(client);
        }catch(java.io.IOException failure){ready=false;throw new IllegalStateException(failure);}
        catch(RuntimeException failure){ready=false;throw failure;}
    }

    private static int apertureChanges(net.minecraft.client.texture.NativeImage image, Vec3d camera, Vec3d point) {
        var relative = point.subtract(camera);
        var clip = new org.joml.Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1)
                .mul(OffscreenWorldView.framePosition()).mul(OffscreenWorldView.frameProjection());
        int width = image.getWidth(), height = image.getHeight();
        int centerX = (int) ((clip.x / clip.w * .5 + .5) * width);
        int centerY = (int) ((.5 - clip.y / clip.w * .5) * height);
        int changed = 0;
        for (int y = Math.max(0, centerY - 8); y < Math.min(height, centerY + 8); y++)
            for (int x = Math.max(0, centerX - 8); x < Math.min(width, centerX + 8); x++)
                if (on[y * width + x] != image.getColor(x, y)) changed++;
        return changed;
    }
}
