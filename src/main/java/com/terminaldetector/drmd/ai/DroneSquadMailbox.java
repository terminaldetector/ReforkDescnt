package com.terminaldetector.drmd.ai;

import java.util.*;

/** Bounded immutable observations, not live entity handles or a command queue. */
public final class DroneSquadMailbox {
    public static final int CAPACITY=256,TTL=60,CLEANUP_LIMIT=32,CADENCE=5;
    public record Message(UUID scout,UUID target,double x,double y,double z,long observedAt,long expiresAt){}
    public record Stats(int size,int cleanupExamined,long cleanupTick,long evictions){}
    private final LinkedHashMap<UUID,Message> messages=new LinkedHashMap<>();
    private long newestObservation=Long.MIN_VALUE,cleanupTick=Long.MIN_VALUE,evictions;
    private int cleanupExamined;
    public boolean offer(UUID receiver,UUID scout,UUID target,double x,double y,double z,long observedAt){
        if(receiver==null||scout==null||target==null||!Double.isFinite(x*x+y*y+z*z)
            ||observedAt<0||observedAt>Long.MAX_VALUE-TTL||observedAt<newestObservation)return false;
        var old=messages.get(receiver);
        if(old!=null&&observedAt<=old.observedAt())return false;
        newestObservation=observedAt;
        if(old==null&&messages.size()==CAPACITY){var iterator=messages.entrySet().iterator();iterator.next();iterator.remove();evictions++;}
        messages.remove(receiver);messages.put(receiver,new Message(scout,target,x,y,z,observedAt,observedAt+TTL));return true;
    }
    public Optional<Message> peek(UUID receiver,long now){
        var message=messages.get(receiver);
        return message!=null&&now>=message.observedAt()&&now<message.expiresAt()?Optional.of(message):Optional.empty();
    }
    public void cleanup(long now){
        if(cleanupTick!=Long.MIN_VALUE&&now>=cleanupTick&&now-cleanupTick<CADENCE)return;
        cleanupTick=now;cleanupExamined=0;var iterator=messages.entrySet().iterator();
        while(iterator.hasNext()&&cleanupExamined<CLEANUP_LIMIT){
            var message=iterator.next().getValue();cleanupExamined++;
            if(now<message.observedAt()||now>=message.expiresAt())iterator.remove();else break;
        }
    }
    public Stats stats(){return new Stats(messages.size(),cleanupExamined,cleanupTick,evictions);}
}
