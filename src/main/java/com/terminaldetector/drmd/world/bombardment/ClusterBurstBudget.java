package com.terminaldetector.drmd.world.bombardment;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;

/** Admit a whole 7/14-fragment burst or defer it; never silently truncate its design count. */
public final class ClusterBurstBudget {
    public static final int PER_TICK=128,PER_CHUNK_TICK=48,MAX_LIVE=512;
    public static final int BLASTS_PER_TICK=4,BLASTS_PER_CHUNK_TICK=2;
    private long tick=Long.MIN_VALUE;
    private int used,live,blasts;
    private boolean measured;
    private final Map<Long,Integer> chunks=new HashMap<>();
    private final Map<Long,Integer> blastChunks=new HashMap<>();
    private void advance(long now){if(tick==now)return;tick=now;used=live=blasts=0;measured=false;chunks.clear();blastChunks.clear();}
    public boolean reserve(long now,long chunk,int count,IntSupplier actualLive) {
        if(count!=7 && count!=14)return false;
        return reserveStage(now,chunk,count,actualLive);
    }
    /** Each real stage pays the same world/chunk/live limits on its actual birth tick. */
    public boolean reserveStage(long now,long chunk,int count,IntSupplier actualLive){
        if(count<1||count>14)return false;advance(now);
        if(!measured){live=Math.max(0,actualLive.getAsInt());measured=true;}
        int inChunk=chunks.getOrDefault(chunk,0);
        if(count>PER_TICK-used || count>PER_CHUNK_TICK-inChunk || count>MAX_LIVE-live)return false;
        used+=count;live+=count;chunks.put(chunk,inChunk+count);return true;
    }
    public boolean reserveBlast(long now,long chunk){advance(now);int local=blastChunks.getOrDefault(chunk,0);if(blasts>=BLASTS_PER_TICK||local>=BLASTS_PER_CHUNK_TICK)return false;blasts++;blastChunks.put(chunk,local+1);return true;}
}
