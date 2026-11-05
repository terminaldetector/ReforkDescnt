package com.terminaldetector.drmd;

import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import java.util.*;

/** Test-owned entity-ticking leases; never release another fixture's global forced-chunk flag. */
final class AiFixtureChunks implements AutoCloseable {
    private static final ChunkTicketType<UUID> TICKET=ChunkTicketType.create("drmd_ai_fixture",Comparator.<UUID>naturalOrder());
    private final ServerWorld world;
    private final UUID owner=UUID.randomUUID();
    private final Set<ChunkPos> columns=new LinkedHashSet<>();
    private boolean closed;
    AiFixtureChunks(ServerWorld world,BlockPos centre,int radius){
        this.world=world;
        // Custom tickets do not reset Vanilla's300-tick empty-world entity idle timer.
        // This fixture lasts at most80 admission+40 measured ticks, as an active player world.
        world.resetIdleTimeout();
        for(int x=(centre.getX()>>4)-radius;x<=(centre.getX()>>4)+radius;x++)for(int z=(centre.getZ()>>4)-radius;z<=(centre.getZ()>>4)+radius;z++){
            var pos=new ChunkPos(x,z);columns.add(pos);world.getChunkManager().addTicket(TICKET,pos,2,owner);
        }
        // Vanilla's ENTITY_TICKING future requires a FULL region two chunks beyond each ticket centre.
        for(int x=(centre.getX()>>4)-radius-2;x<=(centre.getX()>>4)+radius+2;x++)for(int z=(centre.getZ()>>4)-radius-2;z<=(centre.getZ()>>4)+radius+2;z++)world.getChunk(x,z);
        world.resetIdleTimeout();
    }
    boolean entityTicking(BlockPos... positions){
        if(closed)return false;
        for(var p:positions)if(!world.shouldTickEntity(p))return false;
        return true;
    }
    String readiness(BlockPos... positions){
        var out=new StringBuilder();for(var p:positions)out.append(p).append(" loaded=").append(world.isChunkLoaded(ChunkPos.toLong(p))).append(" tracked=").append(world.shouldTick(p)).append(" ticking=").append(world.shouldTickEntity(p)).append(';');return out.toString();
    }
    @Override public void close(){
        if(closed)return;closed=true;
        for(var pos:columns)world.getChunkManager().removeTicket(TICKET,pos,2,owner);
    }
}
