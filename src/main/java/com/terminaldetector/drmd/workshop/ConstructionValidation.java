package com.terminaldetector.drmd.workshop;

import java.util.List;

/** Bounded finite construction data before publishing a world override. Old NBT defaults remain valid. */
public final class ConstructionValidation {
    private ConstructionValidation() {}
    public static boolean validId(String id) { return id != null && id.matches("[a-z0-9_]{1,64}"); }
    public static boolean valid(List<ClusterModule> modules) {
        if(modules.size()>64) return false;
        for(var m:modules) {
            if(!List.of(WeaponClusters.ORDER).contains(m.cluster) || m.name.length()>64 || m.model.length()>64) return false;
            for(float v:new float[]{m.posFwd,m.posRgt,m.posUp,m.pitch,m.yaw,m.roll,m.scaleX,m.scaleY,m.scaleZ,m.kick,m.spin,m.bobAmp,m.bobFreq})
                if(!Float.isFinite(v)) return false;
            if(Math.abs(m.posFwd)>128 || Math.abs(m.posRgt)>128 || Math.abs(m.posUp)>128 ||
                    m.scaleX<=0 || m.scaleX>8 || m.scaleY<=0 || m.scaleY>8 || m.scaleZ<=0 || m.scaleZ>8 ||
                    m.muzzleIdx<0 || m.muzzleIdx>64) return false;
        }
        return true;
    }
}
