package com.terminaldetector.drmd.entity.model;

import com.terminaldetector.drmd.entity.FlightEnemyKind;
import com.terminaldetector.drmd.physics.ragdoll.RagdollRig;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import java.util.*;

/** Small authored compound bodies for the actual shared hull faces; legacy scout remains loadable. */
public final class FlightWreckRig {
    private FlightWreckRig(){}
    public static boolean validScale(double scale){return Double.isFinite(scale)&&scale>=.0625&&scale<=16;}
    public static RagdollRig create(FlightEnemyKind kind,double scale){
        if(!validScale(scale))throw new IllegalArgumentException("invalid Minecraft hull scale");
        RagdollRig base=kind==null?RagdollRig.scoutDrone():switch(kind){
            case ORB->new RagdollRig(List.of(part("orb_core",6,.43,.43,.43,0,0,0)),List.of());
            case ROCKET->new RagdollRig(List.of(part("rocket_core",8,.215,.215,.86,0,0,0),part("left_fin",.35,.285,.03,.325,-.465,0,-.225),part("bottom_fin",.35,.03,.26,.325,0,-.44,-.225),part("right_fin",.35,.285,.03,.325,.465,0,-.225),part("top_fin",.35,.03,.26,.325,0,.44,-.225)),List.of(joint(1,-.20,0,-.225,.265,0,0),joint(2,0,-.20,-.225,0,.24,0),joint(3,.20,0,-.225,-.265,0,0),joint(4,0,.20,-.225,0,-.24,0)));
            case JET->new RagdollRig(List.of(part("jet_core",10,.205,.235,.995,0,0,0),part("left_hull",2,.555,.205,.87,-.55,.02,.115),part("right_hull",2,.555,.205,.87,.55,.02,.115)),List.of(joint(1,-.17,0,0,.38,-.02,-.115),joint(2,.17,0,0,-.38,-.02,-.115)));
        };
        if(scale==1)return base;
        return new RagdollRig(base.segments().stream().map(p->new RagdollRig.Segment(p.id(),p.mass()*scale*scale*scale,p.halfExtents().scaled(scale),p.restPosition().scaled(scale))).toList(),base.joints().stream().map(j->new RagdollRig.Joint(j.first(),j.second(),j.firstAnchor().scaled(scale),j.secondAnchor().scaled(scale),j.compliance())).toList());
    }
    /** Each original face moves with exactly one rigid component; no duplicate live/dead mesh. */
    public static int partForFace(FlightEnemyKind kind,int face){
        if(face<0||face>=FlightEnemyMesh.faces(kind).size())throw new IllegalArgumentException("unknown hull face");
        return switch(kind){case ORB->0;case ROCKET->face>=9&&face<=12?face-8:0;case JET->face<4?1:face<8?2:0;};
    }
    private static RagdollRig.Segment part(String id,double mass,double x,double y,double z,double px,double py,double pz){return new RagdollRig.Segment(id,mass,new Vec3(x,y,z),new Vec3(px,py,pz));}
    private static RagdollRig.Joint joint(int other,double x,double y,double z,double ox,double oy,double oz){return new RagdollRig.Joint(0,other,new Vec3(x,y,z),new Vec3(ox,oy,oz),0);}
}
