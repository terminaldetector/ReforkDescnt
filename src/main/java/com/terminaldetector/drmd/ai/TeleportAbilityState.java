package com.terminaldetector.drmd.ai;

/** Per-actor prototype clock/token protocol. No worlds, entities, sensing or relocation authority. */
public final class TeleportAbilityState {
    public static final int WARNING_TICKS=20,RECOVERY_TICKS=20,COOLDOWN_TICKS=100;
    public enum Phase { IDLE,START_PENDING,WARN,COMMIT_PENDING,RECOVERY,COOLDOWN,REFUSED }
    public record Saved(Phase phase,int remaining,int cooldown){}
    private Phase phase=Phase.IDLE;
    private int remaining,cooldown;
    private long epoch,lastTick=Long.MIN_VALUE;
    public Phase phase(){return phase;}
    public int remaining(){return remaining;}
    public int cooldown(){return cooldown;}
    public long epoch(){return epoch;}
    public boolean ready(){return phase==Phase.IDLE;}
    public boolean ownsMovement(){return phase==Phase.START_PENDING||phase==Phase.WARN||phase==Phase.COMMIT_PENDING||phase==Phase.RECOVERY;}
    public boolean mayFire(){return phase==Phase.IDLE||phase==Phase.COOLDOWN||phase==Phase.REFUSED;}
    private void next(Phase value,int ticks){
        if(epoch==Long.MAX_VALUE){phase=Phase.REFUSED;remaining=0;return;}
        epoch++;phase=value;remaining=ticks;
    }
    public long requestStart(){if(!ready())return -1;next(Phase.START_PENDING,0);return phase==Phase.START_PENDING?epoch:-1;}
    public boolean matches(Phase expected,long token){return phase==expected&&epoch==token;}
    public boolean warn(long token){if(!matches(Phase.START_PENDING,token))return false;next(Phase.WARN,WARNING_TICKS);return phase==Phase.WARN;}
    public boolean committed(long token){if(!matches(Phase.COMMIT_PENDING,token))return false;recover();return true;}
    public void recover(){cooldown=COOLDOWN_TICKS;next(Phase.RECOVERY,RECOVERY_TICKS);}
    public void interrupt(){if(phase==Phase.START_PENDING||phase==Phase.WARN||phase==Phase.COMMIT_PENDING)recover();}
    public void refuse(){cooldown=0;next(Phase.REFUSED,0);}
    /** Returns true once, when normal entity ticks finish the full warning and need an END-phase request. */
    public boolean tick(long now){
        if(now==lastTick)return false;
        if(now<0||now<lastTick){lastTick=now;interrupt();return false;}
        lastTick=now;if(cooldown>0)cooldown--;
        if(phase==Phase.WARN){if(--remaining==0){next(Phase.COMMIT_PENDING,0);return true;}}
        else if(phase==Phase.RECOVERY&&--remaining==0)next(cooldown==0?Phase.IDLE:Phase.COOLDOWN,0);
        else if(phase==Phase.COOLDOWN&&cooldown==0)next(Phase.IDLE,0);
        return false;
    }
    public Saved saved(){return new Saved(phase,remaining,cooldown);}
    /** Strict data protocol. A pending saved transition never grants a relocation after load. */
    public boolean restore(Saved data){
        if(data==null||data.phase()==null||data.cooldown()<0||data.cooldown()>COOLDOWN_TICKS||data.remaining()<0){refuse();return false;}
        boolean valid=switch(data.phase()){
            case IDLE->data.remaining()==0&&data.cooldown()==0;
            case START_PENDING,COMMIT_PENDING->data.remaining()==0&&data.cooldown()==0;
            case WARN->data.remaining()>=1&&data.remaining()<=WARNING_TICKS&&data.cooldown()==0;
            case RECOVERY->data.remaining()>=1&&data.remaining()<=RECOVERY_TICKS&&data.cooldown()>=data.remaining();
            case COOLDOWN->data.remaining()==0&&data.cooldown()>0;
            case REFUSED->data.remaining()==0&&data.cooldown()==0;
        };
        if(!valid){refuse();return false;}
        lastTick=Long.MIN_VALUE;
        if(data.phase()==Phase.START_PENDING||data.phase()==Phase.WARN||data.phase()==Phase.COMMIT_PENDING){recover();return true;}
        cooldown=data.cooldown();next(data.phase(),data.remaining());return true;
    }
}
