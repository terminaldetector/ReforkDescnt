package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.flight.ShipAttitude;
import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import java.nio.file.*;

/** Focused real-client terrain-view regression, separate from the full video acceptance audit. */
public final class HorizonAnchorCheck {
    private static boolean registered,started,off;
    private static int ticks,age,index,draws,checks;
    private static Matrix4f expected;
    private static int[] on;
    // yaw, pitch (positive down), bank, horizontal camera displacement
    private static final int[][] CASES={{0,55,0,0},{90,55,0,0},{180,55,0,0},{270,55,0,0},
        {0,55,90,0},{0,55,180,0},{0,55,270,0},{0,-45,0,0},{0,55,0,160},{0,55,90,320}};
    public static void register() {
        registered=true;
        RepairVisualCheck.register();
        ClientTickEvents.END_CLIENT_TICK.register(HorizonAnchorCheck::tick);
        WorldRenderEvents.START.register(ctx->{if(started) {
            overrideCamera(ctx.camera());expected=new Matrix4f(ctx.positionMatrix());
        }});
        WorldRenderEvents.END.register(ctx->{if(started && age>=30)capture(MinecraftClient.getInstance());});
    }
    public static void overrideCamera(Camera camera) {
        if(started) ((CameraAccessor)camera).drmd$invokeSetPos(new Vec3d(12.5+CASES[index][3],2000,30.5));
    }
    public static void drawn(Matrix4f actual) {
        if(!registered || !started)return;
        if(expected==null || !expected.equals(actual,1e-5f))
            throw new IllegalStateException("Horizon VBO does not use the terrain view matrix: case="+index);
        draws++;checks++;
    }
    private static void tick(MinecraftClient client) {
        if(!registered)return;
        if(++ticks>3000)throw new IllegalStateException("Horizon check timeout case="+index);
        if(client.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt) {
            for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget button
                    && button.getMessage().getString().equals("I know what I'm doing!"))button.onPress();
        }
        if(client.player==null || client.world==null || client.getServer()==null)return;
        if(!started) {
            if(!client.runDirectory.toPath().getFileName().toString().equals("shootcheck"))throw new IllegalStateException("Disposable world required");
            started=true;client.setScreen(null);client.inGameHud.getChatHud().clear(false);
            client.getServer().execute(()->{
                var world=client.getServer().getOverworld();
                world.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,client.getServer());
                world.setTimeOfDay(6000);
            });
            DescentConfig.cockpit=false;DescentConfig.weaponView=false;DescentConfig.hud=false;
            DescentConfig.hybridRender=true;DescentConfig.levelSky=false;DescentConfig.skyUfoVirtualHull=false;
            DescentConfig.planetFloorWithDistantHorizons=true;DescentConfig.fallAftermath=false;
            client.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
            client.options.getFov().setValue(80);client.options.hudHidden=true;
            prepare(client);
        }
        DescentClientState.enabled=true;
        var pose=CASES[index];double yaw=Math.toRadians(pose[0]),pitch=Math.toRadians(pose[1]);
        var forward=new Vec3d(-Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),-Math.cos(yaw)*Math.cos(pitch));
        var up=new Vec3d(-Math.sin(yaw)*Math.sin(pitch),Math.cos(pitch),-Math.cos(yaw)*Math.sin(pitch));
        var attitude=new ShipAttitude();attitude.setForwardUp(forward,up);attitude.rollLocal(pose[2]);
        ShipAttitudeClient.acceptPortalFrame(client.player,attitude.forward(),attitude.up());
        age++;
        if(ticks%100==0)System.out.println("HORIZON_STATE case="+index+" age="+age+" draws="+draws
            +" seed="+com.terminaldetector.drmd.client.planet.PlanetClientState.INSTANCE.hasSeed()
            +" camera="+client.gameRenderer.getCamera().getPos()+" dimension="+client.world.getRegistryKey());
    }
    private static void prepare(MinecraftClient client) {age=0;draws=0;off=false;DescentConfig.planetFloor=true;}
    private static void capture(MinecraftClient client) {
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            Path directory=client.runDirectory.toPath().resolve("horizon-anchor-frames");Files.createDirectories(directory);
            image.writeTo(directory.resolve(String.format("%02d-%s.png",index,off?"off":"on")));
            if(!off) {
                if(draws==0){age=15;return;}
                on=new int[image.getWidth()*image.getHeight()];
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)on[y*image.getWidth()+x]=image.getColor(x,y);
                off=true;DescentConfig.planetFloor=false;age=15;return;
            }
            int changed=0;
            for(int y=0;y<image.getHeight();y+=2)for(int x=0;x<image.getWidth();x+=2) {
                int a=on[y*image.getWidth()+x],b=image.getColor(x,y);
                if(Math.abs((a&255)-(b&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a>>16&255)-(b>>16&255))>40)changed++;
            }
            if(CASES[index][1]>0 && changed<500)throw new IllegalStateException("No terrain below camera: "+index+" pixels="+changed);
            if(CASES[index][1]<0 && changed>100)throw new IllegalStateException("Terrain followed camera into the sky: "+changed);
            System.out.println("HORIZON_ANCHOR case="+index+" changedPixels="+changed+" matrixChecks="+checks);
            if(++index==CASES.length) {
                Files.writeString(client.runDirectory.toPath().resolve("horizon-anchor-ok.json"),"{\"cases\":"+index+",\"matrixChecks\":"+checks+",\"frames\":"+(index*2)+",\"upwardViewClear\":true}\n");
                started=false;registered=false;RepairVisualCheck.start(client);
            } else prepare(client);
        } catch(java.io.IOException e) {throw new IllegalStateException(e);}
    }
}
