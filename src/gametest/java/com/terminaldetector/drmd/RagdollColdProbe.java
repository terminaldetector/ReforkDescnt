package com.terminaldetector.drmd;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.util.*;

/** Passive actual ENTITY_LOAD capture, registered before any cold-world chunks are loaded. */
public final class RagdollColdProbe implements ModInitializer {
    static final int MAX_ENTITIES=512;static final long MAX_BYTES=8L*1024*1024;
    static final Map<UUID,NbtCompound> expected=new LinkedHashMap<>(),loaded=new LinkedHashMap<>();
    static String error;static boolean initialized;
    @Override public void onInitialize(){
        initialized=true;if(!System.getProperty("drmd.lifecycle.mode","").equals("cold"))return;
        try{
            var file=Path.of(System.getProperty("drmd.lifecycle.savedInput",""));
            if(!Files.isRegularFile(file)||Files.size(file)>MAX_BYTES){error="missing/oversized actual closed-world input";return;}
            var tag=NbtIo.readCompressed(file,NbtSizeTracker.of(MAX_BYTES));var list=tag.getList("Entities",NbtElement.COMPOUND_TYPE);
            if(list.isEmpty()||list.size()>MAX_ENTITIES){error="cold input entity count";return;}
            for(int i=0;i<list.size();i++){var value=list.getCompound(i);UUID id=value.getUuid("UUID");if(expected.put(id,value.copy())!=null){error="duplicate saved UUID";return;}}
        }catch(java.io.IOException io){error=io.toString();return;}
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if(!expected.containsKey(entity.getUuid()))return;
            if(loaded.size()>=MAX_ENTITIES&&!loaded.containsKey(entity.getUuid())){error="load capture cap";return;}
            if(loaded.containsKey(entity.getUuid()))return;
            var actual=new NbtCompound();entity.writeNbt(actual);loaded.put(entity.getUuid(),actual);
        });
    }
}
