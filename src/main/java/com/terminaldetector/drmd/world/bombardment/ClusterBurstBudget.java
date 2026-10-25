package com.terminaldetector.drmd.world.bombardment;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;

/** Admit a whole 7/14-fragment burst or defer it; never silently truncate its design count. */
public final class ClusterBurstBudget {
    public static final int PER_TICK=128,PER_CHUNK_TICK=48,MAX_LIVE=512;
    private long tick=Long.MIN_VALUE;
    private int used,live;
    private final Map<Long,Integer> chunks=new HashMap<>();
    public boolean reserve(long now,long chunk,int count,IntSupplier actualLive) {
        if(count!=7 && count!=14)return false;
        if(tick!=now){tick=now;used=0;chunks.clear();live=Math.max(0,actualLive.getAsInt());}
        int inChunk=chunks.getOrDefault(chunk,0);
        if(count>PER_TICK-used || count>PER_CHUNK_TICK-inChunk || count>MAX_LIVE-live)return false;
        used+=count;live+=count;chunks.put(chunk,inChunk+count);return true;
    }
}
