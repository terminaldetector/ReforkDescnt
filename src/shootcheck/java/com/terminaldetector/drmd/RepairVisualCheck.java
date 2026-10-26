package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.portal.PortalViewDiagnostics;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import com.terminaldetector.drmd.world.portal.mirror.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.block.*;
import net.minecraft.util.math.*;
import java.nio.file.*;

/** Real placed models, live on/off views and inventory icons in the disposable world. */
public final class RepairVisualCheck {
    private static boolean active;
    private static volatile boolean ready;
    private static int stage,age,total;
    private static int[] before;
    private static final BlockPos NEAR=new BlockPos(12,205,24),FAR=new BlockPos(12,205,12);
    private static final String[] NAMES={"smooth-ground","mirror-on","mirror-off","portal-on","portal-off","tool-icons"};
    public static void register(){ClientTickEvents.END_CLIENT_TICK.register(RepairVisualCheck::tick);}
    public static void start(MinecraftClient client){active=true;stage=0;prepare(client);}
    public static void overrideCamera(Camera camera) {
        if(!active)return;
        var access=(CameraAccessor)camera;access.drmd$invokeSetPos(new Vec3d(12.5,205.5,30.5));
        access.drmd$invokeSetRotation(180,stage==0?35:0);
    }
    private static void tick(MinecraftClient client) {
        if(!active)return;
        if(++total>2400)throw new IllegalStateException("Repair visual timeout stage="+stage+" "+PortalViewDiagnostics.lastSummary(stage<3?"mirror":"portal"));
        com.terminaldetector.drmd.client.DescentClientState.enabled=false;
        if(ready)age++;
    }
    private static void prepare(MinecraftClient client) {
        age=0;ready=false;DescentConfig.planetFloor=false;DescentConfig.levelSky=false;
        DescentConfig.mirrorReflection=stage==1;DescentConfig.portalSeeThrough=stage==3;
        if(stage==5){client.options.getGuiScale().setValue(2);client.onResolutionChanged();client.setScreen(new Icons());ready=true;return;}
        client.setScreen(null);
        if(stage==2 || stage==4){ready=true;return;}
        client.getServer().execute(()->{
            var w=client.getServer().getOverworld();
            if(stage==0) {
                for(var p:BlockPos.iterate(new BlockPos(6,202,2),new BlockPos(18,212,38)))
                    w.setBlockState(p,p.getY()==202?Blocks.OBSIDIAN.getDefaultState():Blocks.AIR.getDefaultState(),2);
                for(var p:BlockPos.iterate(new BlockPos(7,203,23),new BlockPos(17,203,30)))w.setBlockState(p,Blocks.GRASS_BLOCK.getDefaultState(),2);
                for(var p:BlockPos.iterate(new BlockPos(9,204,24),new BlockPos(14,204,26)))w.setBlockState(p,Blocks.GRASS_BLOCK.getDefaultState(),2);
            } else if(stage==1) {
                w.setBlockState(NEAR,ModWorldBlocks.MIRROR.getDefaultState().with(MirrorBlock.FACING,Direction.SOUTH),3);
                for(var p:BlockPos.iterate(new BlockPos(11,204,34),new BlockPos(13,207,34)))w.setBlockState(p,Blocks.GOLD_BLOCK.getDefaultState(),3);
            } else {
                w.setBlockState(NEAR,ModWorldBlocks.PORTAL_PANEL.getDefaultState().with(PortalPanelBlock.FACING,Direction.SOUTH).with(PortalPanelBlock.LINKED,true),3);
                w.setBlockState(FAR,ModWorldBlocks.PORTAL_PANEL.getDefaultState().with(PortalPanelBlock.FACING,Direction.NORTH).with(PortalPanelBlock.LINKED,true),3);
                ((PortalPanelBlockEntity)w.getBlockEntity(NEAR)).setLink(null,FAR,w.getRegistryKey());
                ((PortalPanelBlockEntity)w.getBlockEntity(FAR)).setLink(null,NEAR,w.getRegistryKey());
                w.getChunkManager().markForUpdate(NEAR);w.getChunkManager().markForUpdate(FAR);
                for(var p:BlockPos.iterate(new BlockPos(10,203,5),new BlockPos(14,208,5)))w.setBlockState(p,Blocks.DIAMOND_BLOCK.getDefaultState(),3);
            }
            ready=true;
        });
    }
    public static void rendered() {
        if(!active || !ready || age<40)return;
        var client=MinecraftClient.getInstance();
        if(stage==1 || stage==3) {
            var summary=PortalViewDiagnostics.lastSummary(stage==1?"mirror":"portal");
            if(summary==null || !summary.startsWith("drawing")){age=25;return;}
        }
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            var root=client.runDirectory.toPath().resolve("repair-visual-frames");Files.createDirectories(root);
            image.writeTo(root.resolve(NAMES[stage]+".png"));
            if(stage==1 || stage==3) {
                before=new int[image.getWidth()*image.getHeight()];
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)before[y*image.getWidth()+x]=image.getColor(x,y);
            }
            if(stage==2 || stage==4) {
                int changed=0;
                for(int y=image.getHeight()/3;y<image.getHeight()*2/3;y++)for(int x=image.getWidth()/3;x<image.getWidth()*2/3;x++)
                    if(before[y*image.getWidth()+x]!=image.getColor(x,y))changed++;
                if(changed<100)throw new IllegalStateException("View submitted but no visible pixels: "+NAMES[stage]+" "+changed);
                System.out.println("REPAIR_VIEW "+NAMES[stage]+" changed="+changed);
            }
            if(++stage==NAMES.length) {
                Files.writeString(client.runDirectory.toPath().resolve("repair-visual-ok.json"),"{\"mirrorVisiblePixels\":true,\"portalVisiblePixels\":true,\"frames\":6}\n");
                active=false;client.scheduleStop();return;
            }
            prepare(client);
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static final class Icons extends net.minecraft.client.gui.screen.Screen {
        private static final String[] IDS={"mining_laser","tunnel_laser","repair_laser","build_tool","gravity_scanner",
            "construction_laser_green","construction_laser_yellow","construction_laser_blue","construction_laser_purple","mirror","charged_mirror","portal_panel"};
        Icons(){super(net.minecraft.text.Text.literal("Engineer tools, mirror and portal models"));}
        @Override public void render(net.minecraft.client.gui.DrawContext context,int x,int y,float delta) {
            context.fill(0,0,width,height,0xff17212b);
            for(int i=0;i<IDS.length;i++) {
                int px=40+i%6*95,py=45+i/6*110;
                var item=net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of("drmd",IDS[i]));
                if(item==net.minecraft.item.Items.AIR)throw new IllegalStateException("Missing icon "+IDS[i]);
                context.getMatrices().push();context.getMatrices().translate(px,py,0);context.getMatrices().scale(3,3,1);
                context.drawItem(new net.minecraft.item.ItemStack(item),0,0);context.getMatrices().pop();
                context.drawTextWithShadow(textRenderer,IDS[i],px,py+55,0xffffff);
            }
        }
    }
}
