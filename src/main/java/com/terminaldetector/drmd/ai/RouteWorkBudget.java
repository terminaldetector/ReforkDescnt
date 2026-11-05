package com.terminaldetector.drmd.ai;

import java.util.*;

/** Per-world, server-thread admission and rotating work budget. Holds IDs only, never worlds/entities. */
public final class RouteWorkBudget {
    private final int maxSearches,perTick,quantum;
    private final LinkedHashMap<UUID,Long> searches=new LinkedHashMap<>();
    private long tick=Long.MIN_VALUE;
    private int remaining;
    public RouteWorkBudget(int maxSearches,int perTick,int quantum) {
        if(maxSearches<1||maxSearches>256||perTick<1||quantum<1||quantum>perTick||quantum>128)throw new IllegalArgumentException("route budgets");
        this.maxSearches=maxSearches;this.perTick=perTick;this.quantum=quantum;
    }
    private void advance(long now) {
        if(tick==now)return;
        searches.values().removeIf(seen->now<seen||now-seen>20);tick=now;remaining=perTick;
    }
    public boolean begin(UUID id,long now) {
        Objects.requireNonNull(id);advance(now);
        if(!searches.containsKey(id)&&searches.size()>=maxSearches)return false;
        searches.put(id,now);return true;
    }
    public int take(UUID id,long now) {
        advance(now);if(!searches.containsKey(id))return 0;
        searches.put(id,now);
        if(remaining==0||!searches.keySet().iterator().next().equals(id))return 0;
        searches.remove(id);searches.put(id,now);int granted=Math.min(quantum,remaining);remaining-=granted;return granted;
    }
    public void release(UUID id){searches.remove(id);}
    public int activeSearches(){return searches.size();}
}
