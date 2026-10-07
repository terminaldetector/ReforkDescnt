package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.render.terrain.HybridTerrainModel;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.geometry.HybridTerrainCommands;
import com.terminaldetector.drmd.world.micro.CarvedBlock;
import com.terminaldetector.drmd.world.micro.CarvedBlockEntity;
import com.terminaldetector.drmd.world.micro.MicroGrid;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.BackupPromptScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import java.nio.file.Files;

/** Runs only in the isolated rendercheck source set; never included in the playable mod. */
public final class HybridRenderCheck implements ClientModInitializer {
    private static final BlockPos DAMAGED=new BlockPos(20,203,18);
    private volatile boolean ready;
    private boolean setupQueued;
    private int ticks, sceneTicks;
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(MinecraftClient client) {
        ++ticks;
        if (ticks%200==0) System.out.println("DRMD rendercheck: screen="+
                (client.currentScreen==null?"none":client.currentScreen.getClass().getName()+": "+client.currentScreen.getTitle().getString())+
                ", world="+(client.world!=null)+", ready="+ready+", sceneTicks="+sceneTicks);
        if (ticks>2400) {
            capture(client,"hybrid-timeout.png");
            throw new IllegalStateException("Render check timed out; screen="+
                    (client.currentScreen==null?"none":client.currentScreen.getClass().getName())+", sceneTicks="+sceneTicks);
        }
        // GameTest saves use the experimental registry lifecycle. Only this disposable fixture
        // may acknowledge its load warning; this source set is absent from the playable jar.
        if (client.currentScreen instanceof BackupPromptScreen prompt) {
            if (!client.runDirectory.toPath().getFileName().toString().equals("rendercheck"))
                throw new IllegalStateException("Render check must run in its disposable directory");
            for (var child:prompt.children()) if (child instanceof ButtonWidget button &&
                    button.getMessage().getString().equals("I know what I'm doing!")) {
                System.out.println("DRMD rendercheck: loading disposable experimental fixture");
                button.onPress();
                return;
            }
            throw new IllegalStateException("Experimental fixture prompt has no expected continue button");
        }
        if (client.world==null || client.player==null || client.getServer()==null) return;
        DescentClient.markUserFlightChoice();
        DescentPlayerData.get(client.player).setEnabled(false);
        DescentConfig.cockpit=false;DescentConfig.hud=false;DescentConfig.weaponView=false;
        DescentConfig.planetFloor=false;DescentConfig.levelSky=false;DescentConfig.skyUfoVirtualHull=false;
        client.options.hudHidden=true;client.options.pauseOnLostFocus=false;
        if (!setupQueued) {
            setupQueued=true;
            var server=client.getServer();var uuid=client.player.getUuid();
            server.execute(() -> {
                var player=server.getPlayerManager().getPlayer(uuid);
                if (player==null) throw new IllegalStateException("Missing render-check player");
                var world=server.getOverworld();setup(world);
                player.changeGameMode(GameMode.SPECTATOR);
                DescentPlayerData.get(player).setEnabled(false);
                player.teleport(world,14,209,29,180,28);
                ready=true;
            });
            return;
        }
        if (!ready || client.currentScreen!=null) return;
        client.player.setPosition(14,209,29);client.player.setVelocity(Vec3d.ZERO);
        client.player.setYaw(180);client.player.setPitch(28);
        if (!(client.getBlockRenderManager().getModel(ModWorldBlocks.CARVED.getDefaultState()) instanceof HybridTerrainModel))
            throw new IllegalStateException("The carved blockstate did not receive the hybrid baked model");
        ++sceneTicks;
        if (sceneTicks==160) {
            capture(client,"hybrid-before.png");
            client.getServer().execute(() -> {
                var world=client.getServer().getOverworld();
                var be=(CarvedBlockEntity)world.getBlockEntity(DAMAGED);
                if (be==null || !be.organic()) throw new IllegalStateException("Missing organic scene block");
                long mask=MicroGrid.carve(be.mask(),.5,.5,.95,.5);
                be.setMask(mask);
                world.setBlockState(DAMAGED.east(),Blocks.BRICKS.getDefaultState());
            });
        }
        if (sceneTicks==240) {
            if (!(client.world.getBlockEntity(DAMAGED) instanceof CarvedBlockEntity be) || be.mask()==MicroGrid.FULL || !be.organic())
                throw new IllegalStateException("Damage/mode did not sync to the render-check client");
            capture(client,"hybrid-after.png");
            try {Files.writeString(client.runDirectory.toPath().resolve("rendercheck-ok.txt"),"Hybrid model loaded; organic mode and damage synced; before/after scene captured.\n");}
            catch (java.io.IOException e) {throw new IllegalStateException(e);}
            client.scheduleStop();
        }
    }
    private static void setup(ServerWorld world) {
        world.setTimeOfDay(6000);
        for(BlockPos p:BlockPos.iterate(new BlockPos(2,200,5),new BlockPos(27,211,24)))
            world.setBlockState(p,p.getY()==200?Blocks.IRON_BLOCK.getDefaultState():Blocks.AIR.getDefaultState());
        for(int centre:new int[]{8,20}) for(int x=centre-4;x<=centre+4;x++) for(int z=9;z<=17;z++) {
            int height=Math.max(1,5-Math.max(Math.abs(x-centre),Math.abs(z-13)));
            for(int y=201;y<201+height;y++) world.setBlockState(new BlockPos(x,y,z),Blocks.STONE.getDefaultState());
        }
        world.setBlockState(DAMAGED,Blocks.STONE.getDefaultState());
        HybridTerrainCommands.apply(world,new BlockPos(16,201,9),new BlockPos(24,206,18),true);
        for(int y=201;y<=204;y++) world.setBlockState(new BlockPos(25,y,14),Blocks.BRICKS.getDefaultState());
        var rigid=new BlockPos(10,202,19);
        world.setBlockState(rigid,Blocks.OAK_PLANKS.getDefaultState());
        CarvedBlock.replace(world,rigid,Blocks.OAK_PLANKS.getDefaultState(),MicroGrid.carve(-1,.9,.8,.9,.45));
        var glass=rigid.east(2);world.setBlockState(glass,Blocks.RED_STAINED_GLASS.getDefaultState());
        CarvedBlock.replace(world,glass,Blocks.RED_STAINED_GLASS.getDefaultState(),MicroGrid.carve(-1,.9,.8,.9,.45));
    }
    private static void capture(MinecraftClient client,String name) {
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            var directory=client.runDirectory.toPath().resolve("screenshots");Files.createDirectories(directory);
            image.writeTo(directory.resolve(name));
        } catch(java.io.IOException e) {throw new IllegalStateException("Cannot save render verification",e);}
    }
}
