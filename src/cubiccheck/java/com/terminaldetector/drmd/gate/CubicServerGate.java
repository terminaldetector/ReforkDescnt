package com.terminaldetector.drmd.gate;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import java.nio.file.*;
import java.util.*;

/** Disposable second-seed authority/reopen test. Never part of the distributed game mod. */
public final class CubicServerGate implements ModInitializer {
    private static final int[] HEIGHTS={-512,-420,-240,-64,0,320,640,880,1024,1025,1640,2150,2160};
    private static final BlockPos HIGH=new BlockPos(8,2150,8),LOW=new BlockPos(8,-512,8);
    private final Map<BlockPos,BlockState> markers=new LinkedHashMap<>();
    private final boolean reopen=Boolean.getBoolean("drmd.gate.server.reopen");
    private int ticks;
    private boolean checked;
    private Path root;
    @Override public void onInitialize() {
        if(!Boolean.getBoolean("drmd.gate.server"))return;
        root=FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        if(!root.getFileName().toString().equals("cubic-seed2-gate"))throw new IllegalStateException("Dedicated disposable second-seed runtime required");
        if(reopen && !Files.isRegularFile(root.resolve("server-gate-write-ok.json")))
            throw new IllegalStateException("Reopen requires the saved write-run proof");
        for(int y:HEIGHTS)markers.put(new BlockPos(8,y,8),Blocks.GOLD_BLOCK.getDefaultState());
        markers.put(HIGH,Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.EAST));
        markers.put(LOW,Blocks.BARREL.getDefaultState());
        markers.put(new BlockPos(8,1025,8),Blocks.DIAMOND_BLOCK.getDefaultState());
        markers.put(new BlockPos(1,2159,1),Blocks.GOLD_BLOCK.getDefaultState());
        markers.put(new BlockPos(1,2160,1),Blocks.DIAMOND_BLOCK.getDefaultState());
        ServerLifecycleEvents.SERVER_STARTED.register(server->command(server,"jfr start"));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }
    private void tick(MinecraftServer server) {
        var world=server.getOverworld();
        if(world.getSeed()!=271026L || world.getBottomY()!=-16384 || world.getHeight()!=32768)
            throw new IllegalStateException("Wrong second-seed authoritative cubic world");
        if(ticks%200==0)for(int y:HEIGHTS)command(server,"bcc load 8 "+y+" 8 0");
        if(checked){ticks++;return;}
        if(++ticks>4000)throw new IllegalStateException("Native cubes never became FULL for the server gate");
        for(var pos:markers.keySet())if(!full(world,pos)) {
            if(ticks%100==0)System.out.println("CUBIC_SERVER_GATE_WAIT pos="+pos+" tick="+ticks);
            return;
        }
        // Availability, writes and immediate readback run in this same authoritative server tick.
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(world);
        if(access.backend()!=com.terminaldetector.drmd.world.access.CubicAvailability.Backend.NATIVE_CUBIC)
            throw new IllegalStateException("WorldAccess did not select the real cubic backend");
        try {
            var column=world.getChunkManager().getChunk(0,0,ChunkStatus.FULL,false);
            var ext=Class.forName("dev.fallingcloud.bettercubicchunks.chunk.ISkyLightSourcesExt");
            Object state=ext.getMethod("bcc$columnState").invoke(((WorldChunk)column).getChunkSkyLight());
            var count=state.getClass().getMethod("loadedCubeCount");
            int before=(Integer)count.invoke(state);
            var unknown=new BlockPos(8,8192,8);
            if(access.readIfLoaded(unknown).isPresent() || before!=(Integer)count.invoke(state))
                throw new IllegalStateException("Unknown native volume became fake air or changed the loaded cube count");
            for(var pos:markers.keySet())if(!access.readIfLoaded(pos).orElseThrow().equals(world.getBlockState(pos)))
                throw new IllegalStateException("WorldAccess disagrees with native storage at "+pos);
            System.out.println("CUBIC_WORLD_ACCESS native=true unknownEmpty=true loadedCountUnchanged="+before);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        if(!reopen) {
            for(var entry:markers.entrySet()) {
                if(!world.setBlockState(entry.getKey(),entry.getValue(),3)
                        && !world.getBlockState(entry.getKey()).equals(entry.getValue()))
                    throw new IllegalStateException("Native FULL-cube write refused: "+entry.getKey());
            }
            var chest=(ChestBlockEntity)world.getBlockEntity(HIGH);
            var barrel=(BarrelBlockEntity)world.getBlockEntity(LOW);
            var namedChest=new ItemStack(Items.CHEST);var namedBarrel=new ItemStack(Items.BARREL);
            namedChest.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,Text.literal("gate-v5-Y2150"));
            namedBarrel.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,Text.literal("gate-v5-negative"));
            chest.readComponents(namedChest);barrel.readComponents(namedBarrel);
            chest.setStack(0,new ItemStack(Items.DIAMOND,37));barrel.setStack(0,new ItemStack(Items.EMERALD,19));
            chest.markDirty();barrel.markDirty();
        }
        for(var entry:markers.entrySet())if(!world.getBlockState(entry.getKey()).equals(entry.getValue()))
            throw new IllegalStateException("Native saved state differs at "+entry.getKey()+": "+world.getBlockState(entry.getKey()));
        if(!(world.getBlockEntity(HIGH) instanceof ChestBlockEntity chest)
                || !chest.getName().getString().equals("gate-v5-Y2150")
                || !chest.getStack(0).isOf(Items.DIAMOND) || chest.getStack(0).getCount()!=37)
            throw new IllegalStateException("Native chest name/item inventory did not survive");
        if(!(world.getBlockEntity(LOW) instanceof BarrelBlockEntity barrel)
                || !barrel.getName().getString().equals("gate-v5-negative")
                || !barrel.getStack(0).isOf(Items.EMERALD) || barrel.getStack(0).getCount()!=19)
            throw new IllegalStateException("Native barrel name/item inventory did not survive");
        if(Boolean.getBoolean("drmd.gate.worldEdits"))NativeWorldEditsGate.verify(world,root);
        command(server,"bcc status");
        try {
            Files.writeString(root.resolve("server-gate-"+(reopen?"reopen":"write")+"-ok.json"),
                "{\"status\":\""+(reopen?"SERVER_SUBSET_PASS_FULL_GATE_PENDING":"SERVER_WRITE_READBACK_PASS_REOPEN_PENDING")+"\",\"seed\":271026,\"mode\":\""+(reopen?"REOPEN_READ_ONLY":"WRITE_AND_VERIFY")
                +"\",\"markers\":"+markers.size()+",\"heights\":"+Arrays.toString(HEIGHTS)
                +",\"chestDiamonds\":37,\"barrelEmeralds\":19,\"sameTickFullCheck\":true}\n");
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        System.out.println("CUBIC_SERVER_GATE_PASS mode="+(reopen?"REOPEN_READ_ONLY":"WRITE_AND_VERIFY")+" markers="+markers.size());
        checked=true;
    }
    private static boolean full(ServerWorld world,BlockPos pos) {
        var chunk=world.getChunkManager().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false);
        if(!(chunk instanceof WorldChunk full))return false;
        try {
            var api=Class.forName("dev.fallingcloud.bettercubicchunks.chunk.ISkyLightSourcesExt");
            Object state=api.getMethod("bcc$columnState").invoke(full.getChunkSkyLight());
            return state!=null && "FULL".equals(String.valueOf(state.getClass().getMethod("status",int.class)
                .invoke(state,world.getSectionIndex(pos.getY()))));
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void command(MinecraftServer server,String command) {
        server.getCommandManager().executeWithPrefix(server.getCommandSource(),command);
    }
}
