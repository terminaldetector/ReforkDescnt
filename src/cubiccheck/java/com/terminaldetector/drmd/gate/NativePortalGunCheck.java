package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.portal.gun.*;
import com.terminaldetector.drmd.world.portal.mirror.*;
import com.terminaldetector.drmd.client.portal.*;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.gate.mixin.ProjectorInputAccess;
import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Vector4f;

/** Actual mouse-entry methods, actual C2S aim, BE metadata, owned pair and through-view. */
public final class NativePortalGunCheck {
    private static final BlockPos A=new BlockPos(8,2156,12),B=new BlockPos(22,2156,12),C=new BlockPos(8,2156,28);
    private static final Vec3d PLAYER=new Vec3d(15,2154.88,26);
    private static boolean started,preparing,shot,capture,finished;
    private static volatile boolean ready;
    private static int phase,ticks,age,frames;
    private static final List<String> RESULTS=new ArrayList<>();
    private static final String[] NAMES={"blue-only","paired-blue-orange","blue-replaced","pair-cleared","container-protected"};
    public static void register(){ClientTickEvents.END_CLIENT_TICK.register(NativePortalGunCheck::tick);}
    public static void camera(Camera camera) {
        if(!capture)return;
        var access=(CameraAccessor)camera;
        access.drmd$invokeSetPos(phase==2?new Vec3d(8.5,2156.5,20):new Vec3d(15,2156.5,26));
        access.drmd$invokeSetRotation(phase==2?0:180,0);
    }
    private static void tick(MinecraftClient client) {
        if(finished)return;
        if(++ticks>3000)throw new IllegalStateException("Portal gun fixture timeout phase="+phase+" age="+age+" ready="+ready);
        if(client.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)
            for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget button
                && button.getMessage().getString().equals("I know what I'm doing!"))button.onPress();
        if(client.player==null || client.world==null || client.getServer()==null)return;
        com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
        com.terminaldetector.drmd.client.DescentClientState.enabled=false;
        if(!started) {
            if(!client.runDirectory.toPath().getFileName().toString().equals("cubic-client-automation"))throw new IllegalStateException("Disposable cubic runtime required");
            started=true;client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);client.setScreen(null);client.inGameHud.getChatHud().clear(false);
            client.options.hudHidden=false;client.options.getFov().setValue(80);
            DescentConfig.hud=false;DescentConfig.cockpit=false;DescentConfig.weaponView=false;DescentConfig.planetFloor=false;DescentConfig.levelSky=false;
            DescentConfig.portalSeeThrough=true;
            client.getServer().execute(()->{
                var p=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());p.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
                com.terminaldetector.drmd.flight.FlightSystem.disable(p,com.terminaldetector.drmd.DescentPlayerData.get(p));
                p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.sendAbilitiesUpdate();
                p.teleport(p.getServerWorld(),PLAYER.x,PLAYER.y,PLAYER.z,Set.of(),180,0);
                p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModWorldBlocks.PORTAL_PROJECTOR));
            });return;
        }
        if(!ready) {
            if(!preparing && client.player.getY()>2100 && loaded(client)) {
                preparing=true;client.getServer().execute(()->{
                    var world=client.getServer().getOverworld();
                    for(BlockPos pos:BlockPos.iterate(new BlockPos(3,2152,8),new BlockPos(27,2161,32)))world.setBlockState(pos,Blocks.AIR.getDefaultState(),2);
                    wall(world,A,Direction.SOUTH);wall(world,B,Direction.SOUTH);wall(world,C,Direction.NORTH);
                    world.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,client.getServer());world.setTimeOfDay(6000);
                    ready=true;
                });
            }
            return;
        }
        if(!client.player.getMainHandStack().isOf(ModWorldBlocks.PORTAL_PROJECTOR) || capture)return;
        if(++age<25)return;
        if(!shot) {
            // Aim at the backing wall's actual front face, not a point in its empty anchor cell.
            Vec3d point=Vec3d.ofCenter(phase==0?A:phase==1?B:phase==2?C:new BlockPos(15,2156,24));
            if(phase<3)point=point.add(0,0,phase==2?.5:-.5);
            aim(client,point);
            if(phase==0 || phase==2)((ProjectorInputAccess)client).drmd$attack();
            else {
                boolean wasSneaking=client.player.input.sneaking;
                client.player.input.sneaking=phase==3;
                ((ProjectorInputAccess)client).drmd$use();client.player.input.sneaking=wasSneaking;
            }
            shot=true;age=0;return;
        }
        if(age==60)client.getServer().execute(()->{
            var p=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            System.out.println("PORTAL_GUN_WAIT phase="+phase+" pair="+PortalGunState.get(p.getServerWorld()).pair(p.getUuid()));
        });
        if(age<20 || !synced(client))return;
        capture=true;frames=0;
    }
    private static boolean loaded(MinecraftClient client) {
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(client.world);
        for(int x=0;x<=1;x++)for(int y=134;y<=135;y++)for(int z=0;z<=2;z++)
            if(!access.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z)))return false;
        return true;
    }
    private static void wall(net.minecraft.server.world.ServerWorld world,BlockPos anchor,Direction facing) {
        for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)world.setBlockState(anchor.add(x,y,0).offset(facing.getOpposite()),Blocks.STONE.getDefaultState(),2);
    }
    private static void aim(MinecraftClient client,Vec3d point) {
        Vec3d d=point.subtract(client.player.getEyePos()).normalize();
        client.player.setYaw(com.terminaldetector.drmd.ai.FlightAttitude.yawOf(d));client.player.setPitch(com.terminaldetector.drmd.ai.FlightAttitude.pitchOf(d));
    }
    private static boolean panel(MinecraftClient client,BlockPos pos,int slot,boolean linked) {
        return client.world.getBlockEntity(pos) instanceof PortalPanelBlockEntity be && be.ownedBy(client.player.getUuid(),slot)
            && be.isLinked()==linked && client.world.getBlockState(pos).get(PortalPanelBlock.CHANNEL)==slot;
    }
    private static boolean synced(MinecraftClient client) {
        return switch(phase) {
            case 0->panel(client,A,1,false);
            case 1->panel(client,A,1,true) && panel(client,B,2,true)
                && B.equals(((PortalPanelBlockEntity)client.world.getBlockEntity(A)).getLinkPartnerPos());
            case 2->panel(client,C,1,true) && panel(client,B,2,true) && client.world.getBlockState(A).isAir()
                && C.equals(((PortalPanelBlockEntity)client.world.getBlockEntity(B)).getLinkPartnerPos());
            default->client.world.getBlockState(C).isAir() && client.world.getBlockState(B).isAir();
        };
    }
    public static void rendered() {
        if(!capture || finished)return;
        var client=MinecraftClient.getInstance();
        if(++frames<60)return;
        if(client.currentScreen!=null)throw new IllegalStateException("Gun opened a screen instead of firing");
        if(phase==1 && (PortalSeeThroughRenderer.scannedCount()!=2 || !String.valueOf(PortalViewDiagnostics.lastSummary("portal")).startsWith("drawing")))return;
        if(phase==3 && PortalSeeThroughRenderer.scannedCount()!=0)return;
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            var root=client.runDirectory.toPath().resolve("portal-gun-frames");Files.createDirectories(root);
            image.writeTo(root.resolve(NAMES[phase]+".png"));
            int blue=0,orange=0;
            if(phase==1) {
                for(int slot=1;slot<=2;slot++) {
                    var point=Vec3d.ofCenter(slot==1?A:B).add(0,0,.5).subtract(client.gameRenderer.getCamera().getPos());
                    var clip=new Vector4f((float)point.x,(float)point.y,(float)point.z,1).mul(OffscreenWorldView.framePosition()).mul(OffscreenWorldView.frameProjection());
                    int cx=(int)((clip.x/clip.w*.5+.5)*image.getWidth()),cy=(int)((.5-clip.y/clip.w*.5)*image.getHeight());
                    int count=0;
                    for(int y=Math.max(0,cy-85);y<Math.min(image.getHeight(),cy+86);y++)for(int x=Math.max(0,cx-85);x<Math.min(image.getWidth(),cx+86);x++) {
                        int c=image.getColor(x,y),r=c&255,g=c>>8&255,b=c>>16&255;
                        if(slot==1?b>60 && b>r*1.4 && r<70 && g>40 && g<160:r>80 && r>g*1.2 && g>b*1.4)count++;
                    }
                    if(slot==1)blue=count;else orange=count;
                }
                if(blue<80 || orange<80)throw new IllegalStateException("Actual coloured portal frames missing: blue="+blue+" orange="+orange);
            }
            RESULTS.add("{\"scene\":\""+NAMES[phase]+"\",\"frames\":"+frames+",\"bluePixels\":"+blue+",\"orangePixels\":"+orange+"}");
            System.out.println("PORTAL_GUN_CLIENT "+NAMES[phase]+" PASS blue="+blue+" orange="+orange);
            capture=false;shot=false;age=0;
            if(++phase==5) {
                client.getServer().execute(()->{
                    var world=client.getServer().getOverworld();var chest=world.getBlockEntity(new BlockPos(15,2156,24));
                    if(!(chest instanceof net.minecraft.block.entity.ChestBlockEntity box) || box.getStack(0).getCount()!=7)
                        throw new IllegalStateException("Gun changed the protected chest");
                    try {Files.writeString(client.runDirectory.toPath().resolve("portal-gun-ok.json"),"{\"actualMouseEntry\":true,\"packetPlacement\":true,\"scenes\":["+String.join(",",RESULTS)+"]}\n");}
                    catch(java.io.IOException failure){throw new IllegalStateException(failure);}
                    finished=true;client.execute(client::scheduleStop);
                });return;
            }
            if(phase==4)client.getServer().execute(()->{
                var world=client.getServer().getOverworld();var pos=new BlockPos(15,2156,24);
                world.setBlockState(pos,Blocks.CHEST.getDefaultState(),3);
                ((net.minecraft.block.entity.ChestBlockEntity)world.getBlockEntity(pos)).setStack(0,new ItemStack(Items.DIAMOND,7));
            });
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
}
