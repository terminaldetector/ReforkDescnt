package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.weapon.core.InterceptAim;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InterceptAimTest {
    private void meets(Vec3 r,Vec3 v,double speed,double life){var s=InterceptAim.solve(r,v,speed,life).orElseThrow();assertTrue(s.ticks()>0&&s.ticks()<=life);assertEquals(1,s.direction().length(),1e-10);assertTrue(s.direction().scaled(speed*s.ticks()).minus(r.plus(v.scaled(s.ticks()))).length()<1e-8);}
    @Test void stationaryTarget(){var s=InterceptAim.solve(new Vec3(0,0,40),new Vec3(0,0,0),2.5,80).orElseThrow();assertEquals(16,s.ticks(),1e-10);}
    @Test void lateralAndVerticalMotion(){meets(new Vec3(8,22,40),new Vec3(.4,-.3,.1),2.5,80);}
    @Test void inheritedLauncherVelocity(){meets(new Vec3(-10,30,50),new Vec3(-.8,.4,-.3),1.4,100);}
    @Test void equalSpeedApproachUsesLinearRoot(){meets(new Vec3(0,0,40),new Vec3(0,0,-2.5),2.5,80);}
    @Test void unreachableAndExpiredTargetsAreRejected(){assertTrue(InterceptAim.solve(new Vec3(0,0,40),new Vec3(0,0,3),2.5,80).isEmpty());assertTrue(InterceptAim.solve(new Vec3(0,0,400),new Vec3(0,0,0),2.5,80).isEmpty());}
    @Test void rotationsDoNotChangeInterceptTime(){var q=ImmPtlQuaternions.rotationByDegrees(new Vec3(1,2,3).normalized(),137);var r=new Vec3(18,-12,44);var v=new Vec3(-.3,.2,.6);var a=InterceptAim.solve(r,v,2.5,80).orElseThrow();var b=InterceptAim.solve(q.rotate(r),q.rotate(v),2.5,80).orElseThrow();assertEquals(a.ticks(),b.ticks(),1e-10);assertTrue(q.rotate(a.direction()).minus(b.direction()).length()<1e-10);}
    @Test void invalidInputsCannotCreateAnAim(){assertTrue(InterceptAim.solve(new Vec3(0,0,0),new Vec3(0,0,0),2.5,80).isEmpty());assertTrue(InterceptAim.solve(new Vec3(Double.NaN,0,1),new Vec3(0,0,0),2.5,80).isEmpty());assertTrue(InterceptAim.solve(new Vec3(0,0,1),new Vec3(0,0,0),0,80).isEmpty());}
}
