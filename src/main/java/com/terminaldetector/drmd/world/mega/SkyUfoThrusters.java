package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6ThrusterMixer;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.*;
import java.util.*;

/** Four fixed maneuvering pods in the existing UFO grid, not four new rigid bodies. */
public final class SkyUfoThrusters {
    public static final List<BlockPos> SLOTS=List.of(new BlockPos(-5,-2,-5),new BlockPos(-5,-2,5),
        new BlockPos(5,-2,-5),new BlockPos(5,-2,5));
    private static final List<Vec3> POINTS=SLOTS.stream().map(p->new Vec3(p.getX()+.5,p.getY()+.5,p.getZ()+.5)).toList();
    // Initialize after the template has been built. Ratings belong to the authored part,
    // not current mass: adding armor cannot manufacture more motor power.
    private static final class Rating {
        static final D6ThrusterMixer MIXER=new D6ThrusterMixer(POINTS,
            SkyUfoHull.TEMPLATE.cells().values().stream().filter(s->!s.isAir()).count()*3.0);
    }
    private SkyUfoThrusters() {}
    private static boolean online(SkyUfoEntity body,int index,boolean legacy) {
        var state=body.getLocalBlock(SLOTS.get(index));
        // Schema1 used the original shell cells. Retain only those actually still present;
        // no material insertion, repair, grid revision or pose reset during old-world load.
        return state.isOf(ModWorldBlocks.SHIP_THRUSTER)||(legacy&&state.isOf(Blocks.PRISMARINE_BRICKS));
    }
    static int active(SkyUfoEntity body,boolean legacy) {
        int count=0;for(int i=0;i<SLOTS.size();i++)if(online(body,i,legacy))count++;return count;
    }
    static void apply(SkyUfoEntity body,boolean legacy,Vec3 worldForce,Vec3 worldTorque) {
        var q=body.rotation();var inverse=q.inverse();
        for(var out:Rating.MIXER.mix(body.localCentre(),inverse.rotate(worldForce),inverse.rotate(worldTorque),i->online(body,i,legacy))) {
            var at=body.localToWorld(new Vec3d(out.localPoint().x(),out.localPoint().y(),out.localPoint().z()));
            var impulse=q.rotate(out.force()).scaled(.05);
            body.applyPhysicsImpulse(at,new Vec3d(impulse.x(),impulse.y(),impulse.z()));
        }
    }
}
