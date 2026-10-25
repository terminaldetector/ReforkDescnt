package com.terminaldetector.drmd.world.gravity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Transition notifications survive short field-edge jitter; no periodic reminders while held. */
public final class GravityNoticeGate {
    public static final long COOLDOWN_TICKS=100;
    private record Entry(UUID current,long notifiedAt,boolean notified) {}
    private final Map<UUID,Entry> entries=new HashMap<>();
    public synchronized boolean observe(UUID player,UUID source,long tick) {
        Entry old=entries.get(player);
        boolean entered=source!=null && (old==null || !Objects.equals(source,old.current()));
        boolean notify=entered && (old==null || !old.notified() || tick-old.notifiedAt()>=COOLDOWN_TICKS);
        entries.put(player,new Entry(source,notify?tick:old==null?0:old.notifiedAt(),notify || old!=null&&old.notified()));
        return notify;
    }
    public synchronized void forget(UUID player) { entries.remove(player); }
    public synchronized void clear() { entries.clear(); }
}
