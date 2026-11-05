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
        for(int x=(centre.getX()>>4)-radius;x<=(centre.getX()>>4)+radius;x++)for(int z=(centre.getZ()>>4)-radius;z<=(centre.getZ()>>4)+radius;z++){
            var pos=new ChunkPos(x,z);columns.add(pos);world.getChunkManager().addTicket(TICKET,pos,2,owner);world.getChunk(x,z);
        }
    }
    boolean entityTicking(BlockPos... positions){
        if(closed)return false;
        for(var p:positions)if(!world.shouldTickEntity(p))return false;
        return true;
    }
    @Override public void close(){
        if(closed)return;closed=true;
        for(var pos:columns)world.getChunkManager().removeTicket(TICKET,pos,2,owner);
    }
}
