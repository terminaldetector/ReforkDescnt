package com.terminaldetector.drmd.physics.collision;

import com.terminaldetector.drmd.d6.D6PhysicsBody;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;

/** Conservative corner-travel subdivision for the existing D6 integrator. A tiny stabilizing
 * torque must not force every slow large ship to use the maximum angular speed each tick. */
public final class BodyStepBudget {
    private BodyStepBudget(){}
    public static int steps(D6PhysicsBody body,Vec3 torque,double radius,boolean gravity) {
        double dt=.05,spin=body.angularVelocity().length();var inverse=body.worldInverseInertia();
        if(inverse!=null) {
            double norm=Math.sqrt(inverse.row0().lengthSquared()+inverse.row1().lengthSquared()+inverse.row2().lengthSquared());
            double rate=2*norm*(body.angularMomentum().length()+torque.length()*dt);
            // d|omega|/dt <= rate*|omega| + ||I^-1||*|torque|, including changing
            // world inertia during free precession. The engine itself caps spin at 2rad/s.
            spin=rate*dt>20?2:spin*Math.exp(rate*dt)+norm*torque.length()*(rate<1e-12?dt:Math.expm1(rate*dt)/rate);
        }
        spin=Math.min(2,spin);
        double linear=Math.min(50,body.linearVelocity().length()+(gravity?9.81*dt:0));
        return Math.max(1,Math.min(256,(int)Math.ceil((linear+spin*radius)*dt/.15)));
    }
}
