package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.gravity.ActorFrameMath;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ActorFrameMathTest {
    @Test void worldUpPreservesVanillaDirections() {
        Vec3d direction=new Vec3d(.2,-.7,.3);
        assertEquals(direction,ActorFrameMath.toWorld(direction,ActorFrameMath.WORLD_UP));
    }
    @Test void everyFiniteBasisPreservesAnglesLengthsAndHandedness() {
        Random random=new Random(6027);
        for(int i=0;i<1000;i++) {
            Vec3d up=new Vec3d(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5).normalize();
            Vec3d x=ActorFrameMath.toWorld(new Vec3d(1,0,0),up);
            Vec3d y=ActorFrameMath.toWorld(new Vec3d(0,1,0),up);
            Vec3d z=ActorFrameMath.toWorld(new Vec3d(0,0,1),up);
            assertEquals(0,y.distanceTo(up),1e-10);
            assertEquals(1,x.length(),1e-10);assertEquals(1,z.length(),1e-10);
            assertEquals(0,x.dotProduct(z),1e-10);
            assertEquals(0,x.crossProduct(y).distanceTo(z),1e-10);
            Vec3d direction=new Vec3d(.24,-.13,.78);
            assertEquals(0,ActorFrameMath.toLocal(ActorFrameMath.toWorld(direction,up),up).distanceTo(direction),1e-10);
        }
    }
    @Test void corruptMetadataCannotCreateAnInvalidPhysicalFrame() {
        for(double[] value:new double[][]{{0,0,0},{Double.NaN,1,0},{1,Double.POSITIVE_INFINITY,0},{100,0,0},{1e-20,0,0}})
            assertEquals(Vec3d.ZERO,ActorFrameMath.decode(value[0],value[1],value[2]));
        assertEquals(new Vec3d(0,-1,0),ActorFrameMath.decode(0,-.9999999,0));
    }
}
