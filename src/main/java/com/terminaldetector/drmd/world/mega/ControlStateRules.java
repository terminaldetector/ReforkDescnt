package com.terminaldetector.drmd.world.mega;

import java.util.Objects;
import java.util.UUID;

/** Pure client admission for the existing server lease, not an ownership source. */
public final class ControlStateRules {
    public enum Kind { SKY, CORE }
    public record Update(UUID body,int epoch,boolean active,String dimension,Kind kind,int fireMask,boolean turret) {
        public Update {
            Objects.requireNonNull(body);Objects.requireNonNull(dimension);Objects.requireNonNull(kind);
            if(epoch<0||dimension.isBlank()
                ||kind==Kind.CORE&&(fireMask!=3||turret)
                ||kind==Kind.SKY&&(fireMask!=7||!turret))
                throw new IllegalArgumentException("unsupported control identity/capabilities");
        }
        public Update released(){return new Update(body,epoch,false,dimension,kind,fireMask,turret);}
    }
    private ControlStateRules(){}
    public static boolean admit(Update previous,Update next,String actualDimension) {
        if(next==null||!next.dimension().equals(actualDimension))return false;
        if(previous==null)return true;
        if(next.epoch()<previous.epoch())return false;
        if(next.epoch()>previous.epoch())return true;
        // A duplicate acquisition cannot reset command counters, and a release
        // remains a tombstone until a genuinely newer server lease arrives.
        return previous.active()&&!next.active()&&previous.body().equals(next.body())
                &&previous.dimension().equals(next.dimension())&&previous.kind()==next.kind();
    }
    public static int sampleFireMask(Update state,int buttons) {
        return state!=null&&state.active()?buttons&state.fireMask():0;
    }
    public static boolean sendTurret(Update state) {return state!=null&&state.active()&&state.turret();}
}
