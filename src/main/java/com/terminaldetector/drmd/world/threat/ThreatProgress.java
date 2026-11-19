package com.terminaldetector.drmd.world.threat;

import java.util.Objects;

/** Finite, persisted recon encounter. Observation is supplied by real server perception. */
public final class ThreatProgress {
    public static final int GRACE_TICKS=12_000, WARNING_TICKS=400, SEARCH_TICKS=1_800,
            HUNT_TICKS=1_800, LOST_SIGHT_TICKS=200, COOLDOWN_TICKS=12_000;
    public static final int ALARM_TTL=GRACE_TICKS+WARNING_TICKS+SEARCH_TICKS, PATROL_SIZE=2;
    public enum Phase { QUIET, WARNING, SEARCHING, HUNTING, SIEGE, COOLDOWN }
    public record Point(double x,double y,double z) {
        public Point {if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)
                ||Math.abs(x)>30_000_000||Math.abs(y)>30_000_000||Math.abs(z)>30_000_000)throw new IllegalArgumentException("invalid alarm position");}
    }
    public record Snapshot(Phase phase,long played,long since,long until,long encounterEnds,
                           long alarmAt,long lastSeen,boolean pending,int spawned,Point origin,Point known) {
        public Snapshot {
            Objects.requireNonNull(phase);Objects.requireNonNull(origin);Objects.requireNonNull(known);
            if(played<0||played>GRACE_TICKS||since<0||until<since||until-since>COOLDOWN_TICKS
                    ||encounterEnds<0||alarmAt< -1||lastSeen< -1||spawned<0||spawned>PATROL_SIZE)
                throw new IllegalArgumentException("invalid threat progress");
            if(phase==Phase.SIEGE&&(spawned!=PATROL_SIZE||lastSeen<since||until!=encounterEnds))
                throw new IllegalArgumentException("siege requires a fully issued patrol and actual sighting");
        }
    }
    private Phase phase=Phase.QUIET;
    private long played,since,until,encounterEnds,alarmAt=-1,lastSeen=-1;
    private boolean pending;
    private int spawned;
    private Point origin=new Point(0,0,0),known=origin;
    public ThreatProgress(){}
    public ThreatProgress(Snapshot s){phase=s.phase;played=s.played;since=s.since;until=s.until;encounterEnds=s.encounterEnds;
        alarmAt=s.alarmAt;lastSeen=s.lastSeen;pending=s.pending;spawned=s.spawned;origin=s.origin;known=s.known;}
    public Snapshot snapshot(){return new Snapshot(phase,played,since,until,encounterEnds,alarmAt,lastSeen,pending,spawned,origin,known);}
    public Phase phase(){return phase;}
    public Point origin(){return origin;}
    public Point lastKnown(){return known;}
    public int spawned(){return spawned;}
    public long alarmAt(){return alarmAt;}
    public static long deadline(long now,int duration){if(now<0)throw new IllegalArgumentException("negative world clock");return now>Long.MAX_VALUE-duration?Long.MAX_VALUE:now+duration;}
    public boolean alarm(long now,Point at){
        Objects.requireNonNull(at);if(now<0)throw new IllegalArgumentException("negative world clock");
        if(phase!=Phase.QUIET)return false;
        pending=true;alarmAt=now;origin=at;known=at;return true;
    }
    public void spawnedActor(){if((phase!=Phase.SEARCHING&&phase!=Phase.HUNTING)||spawned>=PATROL_SIZE)throw new IllegalStateException("no patrol allowance");spawned++;}
    public void cancel(long now){if(phase!=Phase.QUIET&&phase!=Phase.COOLDOWN)cooldown(now);else pending=false;}
    /** Only the save-owned timed attempt may retire an unseen QUIET scout. */
    void retireRecon(long now){if(now<0)throw new IllegalArgumentException("negative world clock");if(phase!=Phase.COOLDOWN)cooldown(now);}
    private void cooldown(long now){phase=Phase.COOLDOWN;since=now;until=deadline(now,COOLDOWN_TICKS);pending=false;}
    /** Online time is separately bounded; offline gaps cannot spend a novice's protection. */
    public void advance(long now,int onlineTicks,boolean insideArea,Point observed,boolean patrolGone){
        advance(now,onlineTicks,insideArea,observed,patrolGone,false);
    }
    /** Only the director may opt its existing confirmed-alarm pair into pressure; timed recon stays unchanged. */
    public void advance(long now,int onlineTicks,boolean insideArea,Point observed,boolean patrolGone,boolean siegeAllowed){
        if(now<0)throw new IllegalArgumentException("negative world clock");
        played=Math.min(GRACE_TICKS,played+Math.max(0,Math.min(100,onlineTicks)));
        if(phase==Phase.COOLDOWN){if(now>=until){phase=Phase.QUIET;since=now;until=now;spawned=0;lastSeen=-1;}return;}
        if(phase==Phase.QUIET){
            if(pending&&(now<alarmAt||now-alarmAt>=ALARM_TTL||!insideArea)){pending=false;return;}
            if(pending&&played>=GRACE_TICKS){phase=Phase.WARNING;since=now;until=deadline(now,WARNING_TICKS);pending=false;spawned=0;}
            return;
        }
        if(!insideArea){cooldown(now);return;}
        if(phase==Phase.WARNING){
            if(now>=until){phase=Phase.SEARCHING;since=now;until=deadline(now,SEARCH_TICKS);encounterEnds=deadline(now,SEARCH_TICKS+HUNT_TICKS);}
            return;
        }
        if((patrolGone&&spawned>0)||now>=encounterEnds){cooldown(now);return;}
        if(observed!=null){
            known=observed;lastSeen=now;
            if(phase==Phase.SEARCHING){phase=Phase.HUNTING;since=now;until=encounterEnds;}
            else if(phase==Phase.HUNTING&&siegeAllowed&&spawned==PATROL_SIZE&&now>=deadline(since,WARNING_TICKS)){
                phase=Phase.SIEGE;since=now;until=encounterEnds;
            }
            return;
        }
        if(phase==Phase.SEARCHING&&now>=until){cooldown(now);return;}
        if((phase==Phase.HUNTING||phase==Phase.SIEGE)&&lastSeen>=0&&now-lastSeen>=LOST_SIGHT_TICKS){phase=Phase.SEARCHING;since=now;until=Math.min(encounterEnds,deadline(now,SEARCH_TICKS));}
    }
}
