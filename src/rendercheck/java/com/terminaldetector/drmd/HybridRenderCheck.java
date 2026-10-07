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
    private volatile boolean collapseReady;
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
        if (ticks>4800) {
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
        client.player.setPosition(sceneTicks>=260?17:14,sceneTicks>=260?211:209,sceneTicks>=260?37:29);client.player.setVelocity(Vec3d.ZERO);
        client.player.setYaw(180);client.player.setPitch(sceneTicks>=260?24:28);
        if (!(client.getBlockRenderManager().getModel(ModWorldBlocks.CARVED.getDefaultState()) instanceof HybridTerrainModel))
            throw new IllegalStateException("The carved blockstate did not receive the hybrid baked model");
        if(sceneTicks>=260 && !collapseReady)return;
        ++sceneTicks;
        if (sceneTicks==160) {
            capture(client,"hybrid-before.png");
            client.getServer().execute(() -> {
                var world=client.getServer().getOverworld();
                var state=world.getBlockState(DAMAGED);
                if (!(state.getBlock() instanceof com.terminaldetector.drmd.world.geometry.OrganicBlock) || world.getBlockEntity(DAMAGED)!=null)
                    throw new IllegalStateException("Intact organic terrain must be palette-backed");
                long mask=MicroGrid.carve(MicroGrid.FULL,.5,.5,.95,.5);
                var be=CarvedBlock.replace(world,DAMAGED,state,mask);
                if(be==null || !be.organic())throw new IllegalStateException("Organic damage transition lost mode");
                world.setBlockState(DAMAGED.east(),Blocks.BRICKS.getDefaultState());
            });
        }
        if (sceneTicks==240) {
            if (!(client.world.getBlockEntity(DAMAGED) instanceof CarvedBlockEntity be) || be.mask()==MicroGrid.FULL || !be.organic())
                throw new IllegalStateException("Damage/mode did not sync to the render-check client");
            capture(client,"hybrid-after.png");
            try {Files.writeString(client.runDirectory.toPath().resolve("rendercheck-ok.txt"),"Hybrid model loaded; organic mode and damage synced; before/after scene captured.\n");}
            catch (java.io.IOException e) {throw new IllegalStateException(e);}
        }
        if(sceneTicks==260)client.getServer().execute(() -> {
            var world=client.getServer().getOverworld();
            for(BlockPos p:BlockPos.iterate(new BlockPos(1,200,4),new BlockPos(35,216,31)))
                world.setBlockState(p,p.getY()==200?Blocks.OBSIDIAN.getDefaultState():Blocks.AIR.getDefaultState());
            for(int y=201;y<=207;y++)world.setBlockState(new BlockPos(10,y,14),Blocks.OAK_LOG.getDefaultState());
            for(BlockPos p:BlockPos.iterate(new BlockPos(8,205,12),new BlockPos(12,209,16)))
                if(world.getBlockState(p).isAir() && Math.abs(p.getX()-10)+Math.abs(p.getZ()-14)+Math.abs(p.getY()-207)<=4)
                    world.setBlockState(p,Blocks.OAK_LEAVES.getDefaultState().with(net.minecraft.state.property.Properties.PERSISTENT,true));
            for(int y=201;y<=211;y++)world.setBlockState(new BlockPos(25,y,14),Blocks.BRICKS.getDefaultState());
            var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            player.teleport(world,17,211,37,180,24);collapseReady=true;
        });
        if(sceneTicks==340) {
            if(!collapseReady)throw new IllegalStateException("Collapse fixture setup incomplete");
            capture(client,"collapse-before.png");
            client.getServer().execute(() -> {
                var world=client.getServer().getOverworld();var owner=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                for(int x:new int[]{10,25}) {
                    var rocket=new com.terminaldetector.drmd.entity.ProjectileEntity(com.terminaldetector.drmd.entity.ModEntities.PROJECTILE,world);
                    rocket.setOwner(owner);rocket.setDamageClass(com.terminaldetector.drmd.weapon.core.DamageClass.EXPLOSIVE);
                    rocket.setMeshKind(com.terminaldetector.drmd.entity.ProjectileEntity.MESH_ROCKET);rocket.setVisualScale(1);
                    rocket.setDirectDamage(100);rocket.setSplashDamage(35);rocket.setSplashRadius(2);rocket.setWorldBlast(true);
                    rocket.setPosition(x+.5,201.5,29);rocket.setVelocity(0,0,-.7);world.spawnEntity(rocket);
                }
            });
        }
        if(sceneTicks==354)capture(client,"collapse-flight.png");
        if(sceneTicks>=340 && sceneTicks<=680 && sceneTicks%5==0)capture(client,String.format("collapse-frame-%04d.png",sceneTicks));
        if(sceneTicks==680) {
            var bodies=client.world.getEntitiesByClass(com.terminaldetector.drmd.world.contraption.BlockBodyEntity.class,
                new net.minecraft.util.math.Box(1,200,4,35,216,31),b -> b.physicsGravity());
            if(bodies.size()!=2 || bodies.stream().anyMatch(b -> Math.abs(b.rotation().x())<.35))
                throw new IllegalStateException("Rocket-cut tree/column did not physically tip on client: "+bodies.size());
            capture(client,"collapse-after.png");
            try {Files.writeString(client.runDirectory.toPath().resolve("collapse-ok.txt"),"Real rocket impacts detached tree and column; both gravity bodies tipped; flight and before/after frames captured.\n");}
            catch(java.io.IOException e){throw new IllegalStateException(e);}
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
