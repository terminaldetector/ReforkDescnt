package com.terminaldetector.drmd.world.compat;

/** Server-thread budget shared by every recursive call in one upstream scheduling pass. */
public final class CubicScheduleBudget {
    public record Snapshot(long passes,long deferred,int maxCalls,long maxPassNanos){}
    private boolean active;
    private long start,deadline,passes,deferred,maxPassNanos;
    private int calls,maxCalls;
    public void begin(long now,long allowanceNanos){
        start=now;deadline=now+Math.max(0,Math.min(Long.MAX_VALUE/2,allowanceNanos));active=true;calls=0;passes++;
    }
    /** False means still pending, exactly as an unfinished asynchronous dependency. */
    public boolean admit(long now){
        if(!active)return true;
        calls++;
        if(now-deadline>=0){deferred++;return false;}
        return true;
    }
    public void end(long now){
        if(active){maxCalls=Math.max(maxCalls,calls);maxPassNanos=Math.max(maxPassNanos,Math.max(0,now-start));active=false;}
    }
    public Snapshot snapshot(){return new Snapshot(passes,deferred,maxCalls,maxPassNanos);}
}
