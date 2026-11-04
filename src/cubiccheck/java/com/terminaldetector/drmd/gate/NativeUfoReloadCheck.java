package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.client.flight.BoardingClient;
import com.terminaldetector.drmd.client.flight.UfoControlClient;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem;
import com.terminaldetector.drmd.world.mega.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.util.UUID;

/** Two actual client processes and the same saved world; no NBT clone or replacement hull. */
public final class NativeUfoReloadCheck {
    public static final boolean WRITE=Boolean.getBoolean("drmd.gate.ufoReloadWrite");
    public static final boolean READ=Boolean.getBoolean("drmd.gate.ufoReloadRead");
    private static volatile UUID watched;
    private static volatile boolean restored,serverReady;
    private static boolean done;private static int ticks,draws,stable,frames;
    private static Path file(String name){return MinecraftClient.getInstance().runDirectory.toPath().resolve(name);}
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private NativeUfoReloadCheck(){}
    private static net.minecraft.entity.Entity entity(BlockBodyEntity body){return body;}
    public static void watch(UUID id) {
        watched=id;try{Files.writeString(file("ufo-reload-target.txt"),id.toString());}catch(Exception e){throw new IllegalStateException(e);}
    }
    public static void saved(SkyUfoEntity body,NbtCompound n) {
        if(!WRITE||entity(body).getWorld().isClient||!entity(body).getUuid().equals(watched))return;
        try{Files.writeString(file("ufo-reload-expected.snbt"),n.toString());}catch(Exception e){throw new IllegalStateException(e);}
    }
    public static void loaded(SkyUfoEntity body,NbtCompound saved) {
        if(!READ||entity(body).getWorld().isClient)return;
        try {
            var id=UUID.fromString(Files.readString(file("ufo-reload-target.txt")).trim());if(!entity(body).getUuid().equals(id))return;
            var expected=StringNbtReader.parse(Files.readString(file("ufo-reload-expected.snbt")));
            var actual=entity(body).writeNbt(new NbtCompound());
            require(expected.getCompound("body").equals(saved.getCompound("body")),"world storage must retain the exact saved physics data");
            var before=expected.getCompound("body");var after=actual.getCompound("body");
            // Rotation is normalized on load; motionPaused is a current-tick render
            // readiness hint, not persisted physical motion. Check actual state explicitly.
            for(String key:new String[]{"qx","qy","qz","qw","vx","vy","vz","lx","ly","lz","passengerGravityx","passengerGravityy","passengerGravityz"})
                require(Double.isFinite(after.getDouble(key))&&Math.abs(before.getDouble(key)-after.getDouble(key))<=1e-10,"restored physical component differs: "+key);
            require(before.getBoolean("gravity")==after.getBoolean("gravity"),"restored gravity mode");
            require(expected.getList("Pos",NbtElement.DOUBLE_TYPE).equals(actual.getList("Pos",NbtElement.DOUBLE_TYPE)),"restored world position");
            require(expected.getCompound("shape").equals(actual.getCompound("shape")),"actual world reload must retain the local block grid");
            require(expected.getBoolean("autopilot")&&body.autopilotEnabled(),"reload restores the pre-capture AI mode");
            require(body.physics().linearVelocity().length()>.1&&body.physics().angularVelocity().length()>.01,"fixture must save a translating and rotating ship");
            watched=id;restored=true;System.out.println("UFO_RELOAD_NBT PASS uuid="+id+" cells="+body.cells().size());
        }catch(Exception e){throw new IllegalStateException("actual UFO reload verification failed",e);}
    }
    public static void register() {
        require(READ,"reload reader stand only");
        require(MinecraftClient.getInstance().runDirectory.toPath().getFileName().toString().equals("cubic-client-automation"),"disposable native runtime only");
        ServerTickEvents.END_SERVER_TICK.register(s->{
            if(!restored||serverReady)return;var w=s.getOverworld();var e=w.getEntity(watched);if(!(e instanceof SkyUfoEntity body))return;
            var players=s.getPlayerManager().getPlayerList();if(players.isEmpty())return;var p=players.getFirst();
            require(!UfoControlSystem.controls(p),"old pilot connection must not survive a process restart");
            require(w.getEntitiesByClass(SkyUfoEntity.class,entity(body).getBoundingBox().expand(64),u->true).size()==1,"reload must not duplicate the local UFO");
            if(body.containsPos(p.getPos())&&BoardingSystem.drives(p))stable++;else stable=0;
            if(stable>=40)serverReady=true;
        });
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(done)return;if(++ticks>1800)throw new IllegalStateException("UFO reload timeout: restored="+restored+" contact="+stable+" draws="+draws);
            com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
            if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)for(var child:prompt.children())
                if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
            if(c.player!=null&&c.world!=null&&serverReady){require(!UfoControlClient.active(),"client cannot retain a previous process's pilot input");require(BoardingClient.drives(c.player),"saved player must resume physical walking on the saved UFO");}
        });
    }
    public static void drew(BlockBodyEntity body){if(READ&&entity(body).getUuid().equals(watched))draws++;}
    public static void rendered() {
        var c=MinecraftClient.getInstance();if(done||!serverReady||draws<20||c.currentScreen!=null||++frames<3)return;
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            image.writeTo(file("ufo-reloaded.png"));
            Files.writeString(file("ufo-reload-ok.json"),"{\"actualProcessRestart\":true,\"serializedDataExact\":true,\"restoredPhysicsTolerance\":1e-10,\"sameUuid\":\""+watched+"\",\"noDuplicateHull\":true,\"restoredWalkingTicks\":"+stable+",\"renderedFrames\":"+draws+",\"stalePilotAbsent\":true}\n");
            done=true;c.scheduleStop();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
