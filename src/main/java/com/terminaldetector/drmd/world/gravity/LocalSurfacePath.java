package com.terminaldetector.drmd.world.gravity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.List;

/** Native Path lifecycle/index, with exact world feet rather than world-XZ width offsets. */
public final class LocalSurfacePath extends Path {
    private final List<Vec3d> feet;
    final Vec3d up;
    final long createdAt;
    long progressAt;
    Vec3d progressPosition;
    LocalSurfacePath(List<Vec3d> feet,Vec3d up,BlockPos target,long now) {
        super(feet.stream().map(p->new PathNode(MathHelper.floor(p.x),MathHelper.floor(p.y),MathHelper.floor(p.z))).toList(),target,true);
        this.feet=List.copyOf(feet);this.up=up;createdAt=progressAt=now;progressPosition=feet.get(0);
    }
    @Override public Vec3d getNodePosition(Entity entity,int index){return feet.get(MathHelper.clamp(index,0,feet.size()-1));}
    @Override public Vec3d getNodePosition(Entity entity){return getNodePosition(entity,getCurrentNodeIndex());}
    @Override public boolean equalsPath(Path other) {
        return other instanceof LocalSurfacePath p&&feet.equals(p.feet)&&up.equals(p.up)&&getTarget().equals(p.getTarget());
    }
    boolean matches(Vec3d actualUp){return up.distanceTo(actualUp)<1e-6;}
}
