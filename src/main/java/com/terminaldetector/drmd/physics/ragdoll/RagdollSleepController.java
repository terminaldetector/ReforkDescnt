package com.terminaldetector.drmd.physics.ragdoll;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import java.util.List;

/** A whole-rig, consecutive rest certificate. It never zeroes or rewrites physical state. */
public final class RagdollSleepController {
    public static final int QUIET_TICKS=20,MAX_PARTS=16,MAX_SLEEP_TICKS=20;
    public static final double MAX_CORNER_SPEED=.02,MAX_POSE_DRIFT=.001;
    public record Pose(Vec3 position,Quat rotation,double radius){}
    private List<Pose> anchor;
    private int quiet,slept;
    private boolean sleeping;
    public boolean ready(){return quiet>=QUIET_TICKS;}
    public boolean sleeping(){return sleeping;}
    public int quietTicks(){return quiet;}
    public void wake(){anchor=null;quiet=0;slept=0;sleeping=false;}
    public boolean skipTick(){return sleeping&&++slept<=MAX_SLEEP_TICKS;}
    public void sleep(){if(!ready())throw new IllegalStateException("uncertified ragdoll sleep");sleeping=true;slept=0;}
    /** 21 poses prove 20 consecutive ordinary intervals; the first sample is only an anchor. */
    public boolean observe(boolean eligible,List<Pose> poses,double cornerSpeed,double energy,double energyBudget){
        if(!eligible||poses==null||poses.isEmpty()||poses.size()>MAX_PARTS||!Double.isFinite(cornerSpeed)||cornerSpeed<0||cornerSpeed>MAX_CORNER_SPEED
            ||!Double.isFinite(energy)||energy<0||!Double.isFinite(energyBudget)||energyBudget<0||energy>energyBudget){wake();return false;}
        for(var pose:poses)if(!valid(pose)){wake();return false;}
        if(anchor==null){anchor=List.copyOf(poses);return false;}
        if(anchor.size()!=poses.size()){wake();return false;}
        for(int i=0;i<poses.size();i++){
            var a=anchor.get(i);var b=poses.get(i);var q=a.rotation();var r=b.rotation();
            double dot=Math.min(1,Math.abs(q.x()*r.x()+q.y()*r.y()+q.z()*r.z()+q.w()*r.w()));
            double drift=b.position().minus(a.position()).length()+2*b.radius()*Math.sqrt(Math.max(0,1-dot*dot));
            if(!Double.isFinite(drift)||drift>MAX_POSE_DRIFT){wake();return false;}
        }
        if(quiet<QUIET_TICKS)quiet++;
        return ready();
    }
    private static boolean valid(Pose p){
        if(p==null||p.position()==null||p.rotation()==null||!Double.isFinite(p.radius())||p.radius()<=0)return false;
        var v=p.position();var q=p.rotation();double norm=q.x()*q.x()+q.y()*q.y()+q.z()*q.z()+q.w()*q.w();
        return Double.isFinite(v.lengthSquared())&&Double.isFinite(norm)&&Math.abs(norm-1)<1e-8;
    }
}
