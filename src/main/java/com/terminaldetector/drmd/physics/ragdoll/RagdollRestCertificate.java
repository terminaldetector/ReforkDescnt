package com.terminaldetector.drmd.physics.ragdoll;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Actual contact impulse/wrench evidence for a whole rig on one static horizontal plane. */
public final class RagdollRestCertificate {
    private static final Vec3 ZERO=new Vec3(0,0,0);
    private Vec3 centre=ZERO,gravity=ZERO,impulse=ZERO,angular=ZERO;
    private double mass,radius,dt,plane=Double.NaN;
    private boolean horizontal;
    private final Set<Integer> supports=new LinkedHashSet<>();
    public record Footprint(int part,double minX,double maxX,double minZ,double maxZ,double plane){}
    private final Map<Integer,Footprint> footprints=new LinkedHashMap<>();
    public void begin(Vec3 centre,Vec3 gravity,double mass,double radius,double seconds){
        this.centre=centre;this.gravity=gravity;this.mass=mass;this.radius=radius;dt=seconds;
        impulse=ZERO;angular=ZERO;plane=Double.NaN;horizontal=true;supports.clear();footprints.clear();
    }
    public void contact(int part,Vec3 position,Vec3 linearImpulse,Vec3 spinImpulse,Vec3 normal,double supportPlane){
        contact(part,position,linearImpulse,spinImpulse,normal,supportPlane,position.x(),position.x(),position.z(),position.z());
    }
    public void contact(int part,Vec3 position,Vec3 linearImpulse,Vec3 spinImpulse,Vec3 normal,double supportPlane,double minX,double maxX,double minZ,double maxZ){
        if(linearImpulse.lengthSquared()+spinImpulse.lengthSquared()<1e-24)return;
        impulse=impulse.plus(linearImpulse);angular=angular.plus(position.minus(centre).cross(linearImpulse)).plus(spinImpulse);
        if(normal.y()<.999999||Math.abs(normal.x())+Math.abs(normal.z())>1e-9||!Double.isFinite(supportPlane))horizontal=false;
        if(Double.isNaN(plane))plane=supportPlane;else if(Math.abs(plane-supportPlane)>1e-4)horizontal=false;
        supports.add(part);
        var previous=footprints.get(part);
        footprints.put(part,new Footprint(part,previous==null?minX:Math.min(minX,previous.minX()),previous==null?maxX:Math.max(maxX,previous.maxX()),
            previous==null?minZ:Math.min(minZ,previous.minZ()),previous==null?maxZ:Math.max(maxZ,previous.maxZ()),supportPlane));
    }
    public Set<Integer> supports(){return Set.copyOf(supports);}
    public List<Footprint> footprints(){return List.copyOf(footprints.values());}
    public double plane(){return plane;}
    public double linearResidual(){return impulse.plus(gravity.scaled(mass*dt)).length();}
    public double angularResidual(){return angular.length();}
    public boolean balanced(){
        // Constant gravity has zero torque about system COM. Permit the explicit <=1mm
        // positional certificate error and <=1mm/s impulse residual, never arbitrary jitter.
        double angularBudget=mass*(radius*.001+gravity.length()*dt*RagdollSleepController.MAX_POSE_DRIFT);
        return horizontal&&!supports.isEmpty()&&mass>0&&Double.isFinite(mass)&&Double.isFinite(radius)&&radius>0
            &&Math.abs(gravity.x())+Math.abs(gravity.z())<1e-9&&Math.abs(gravity.y()+9.81)<1e-9
            &&Double.isFinite(linearResidual())&&linearResidual()<=mass*.001
            &&Double.isFinite(angularResidual())&&angularResidual()<=angularBudget;
    }
}
