package com.terminaldetector.drmd.ai;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JetFlightMotorTest {
    @Test void reversalPreservesMomentumAndBoundsEveryChange(){
        var v=new Vec3d(.5,0,0);var demand=new Vec3d(-.5,0,0);
        var first=JetFlightMotor.steer(v,demand);assertTrue(first.x>0,"reverse thrust must brake before reversing travel");
        for(int i=0;i<20;i++){var next=JetFlightMotor.steer(v,demand);assertTrue(next.subtract(v).length()<=JetFlightMotor.MAX_VELOCITY_STEP+1e-12);v=next;}
        assertEquals(demand,v);
    }
    @Test void verticalAndDiagonalTurnsAreBoundedAndReachDemand(){
        for(var demand:new Vec3d[]{new Vec3d(0,.5,0),new Vec3d(0,-.5,0),new Vec3d(-.3,.3,.3)}){
            var v=new Vec3d(.5,0,0);
            for(int i=0;i<30;i++){var next=JetFlightMotor.steer(v,demand);assertTrue(next.subtract(v).length()<=JetFlightMotor.MAX_VELOCITY_STEP+1e-12);assertTrue(next.distanceTo(demand)<=v.distanceTo(demand)+1e-12);v=next;}
            assertEquals(demand,v);
        }
    }
    @Test void smallCorrectionsDoNotOvershootAndBadInputsAreRejected(){
        var v=new Vec3d(.01,0,0);assertEquals(Vec3d.ZERO,JetFlightMotor.steer(v,Vec3d.ZERO));assertEquals(v,JetFlightMotor.steer(v,v));
        assertThrows(IllegalArgumentException.class,()->JetFlightMotor.steer(new Vec3d(Double.NaN,0,0),v));
        assertThrows(IllegalArgumentException.class,()->JetFlightMotor.steer(v,new Vec3d(0,Double.POSITIVE_INFINITY,0)));
    }
}
